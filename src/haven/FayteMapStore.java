package haven;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

public class FayteMapStore {
    public static final String DIR = "fayte_map";
    private static final long SAVE_MS = 10000L;
    private static final Map<String, FayteMapStore> stores = new HashMap<>();
    private final File server;
    private final File dir;
    private final File tiles;
    private final Map<Long, FayteMapStore.Pos> grids = new HashMap<>();
    private final Map<Long, Map<Coord, Long>> segs = new HashMap<>();
    private final Map<String, Long> imported = new HashMap<>();
    private final Map<Long, Long> replaced = new HashMap<>();
    private final Set<Long> legacy = new HashSet<>();
    private final Map<Long, Boolean> content = new HashMap<>();
    private long nextseg = 1L;
    private long nextlegacy = Long.MIN_VALUE + 1L;
    private boolean dirty = false;
    private long lastsave = 0L;
    public int conflicts = 0;

    public static class Loc {
        public final long seg;
        public final double x;
        public final double y;

        public Loc(long seg, double x, double y) {
            this.seg = seg;
            this.x = x;
            this.y = y;
        }
    }

    public static class Pos {
        public long seg;
        public Coord c;

        Pos(long seg, Coord c) {
            this.seg = seg;
            this.c = c;
        }
    }

    public static synchronized FayteMapStore get(File server) {
        String k = server.getAbsolutePath();
        FayteMapStore s = stores.get(k);
        if (s == null) {
            s = new FayteMapStore(server);
            stores.put(k, s);
        }
        return s;
    }

    private FayteMapStore(File server) {
        this.server = server;
        dir = new File(server, DIR);
        tiles = new File(dir, "tiles");
        load();
    }

    public File tilefile(long id) {
        return new File(tiles, Long.toString(id) + ".png");
    }

    private synchronized void load() {
        File f = new File(dir, "grids.tsv");
        if (f.exists()) {
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                String line;
                int bad = 0;
                while ((line = r.readLine()) != null) {
                    String[] p = line.split("\t");
                    if (p.length >= 4 && !line.startsWith("#")) {
                        long id;
                        try {
                            id = Long.parseLong(p[0]);
                            set(id, Long.parseLong(p[1]), new Coord(Integer.parseInt(p[2]), Integer.parseInt(p[3])));
                        } catch (NumberFormatException e) {
                            bad++;
                            continue;
                        }
                        if (p.length >= 5 && p[4].equals("old")) {
                            legacy.add(id);
                            nextlegacy = Math.max(nextlegacy, id + 1L);
                        }
                    }
                }
                if (bad > 0) {
                    FayteLog.log("Map store: skipped " + bad + " unreadable lines in " + f);
                }
            } catch (Exception e) {
                FayteLog.log("Map store: could not read " + f + ": " + e);
            }
        }
        File rp = new File(dir, "replaced.tsv");
        if (rp.exists()) {
            try {
                for (String line : new String(Files.readAllBytes(rp.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    String[] p = line.trim().split("\t");
                    if (p.length == 2) {
                        replaced.put(Long.parseLong(p[0]), Long.parseLong(p[1]));
                    }
                }
            } catch (Exception e) {
                FayteLog.once("FayteMapStore.load", e);
            }
        }
        File im = new File(dir, "imported.tsv");
        if (im.exists()) {
            try {
                for (String line : new String(Files.readAllBytes(im.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    String[] p = line.split("\t");
                    if (p.length == 2) {
                        imported.put(p[0], Long.parseLong(p[1].trim()));
                    }
                }
            } catch (Exception e) {
                FayteLog.once("FayteMapStore.load", e);
            }
        }
        dirty = false;
    }

    private void set(long id, long seg, Coord c) {
        FayteMapStore.Pos old = grids.get(id);
        if (old != null) {
            Map<Coord, Long> os = segs.get(old.seg);
            if (os != null) {
                os.remove(old.c);
                if (os.isEmpty()) {
                    segs.remove(old.seg);
                }
            }
        }
        grids.put(id, new FayteMapStore.Pos(seg, c));
        segs.computeIfAbsent(seg, k -> new HashMap<>()).put(c, id);
        nextseg = Math.max(nextseg, seg + 1);
        dirty = true;
    }

    private boolean old(long id) {
        return legacy.contains(id);
    }

    public synchronized FayteMapStore.Pos pos(long id) {
        return grids.get(id);
    }

    public File dir() {
        return dir;
    }

    public synchronized Set<Long> allids() {
        return new HashSet<>(grids.keySet());
    }

    public synchronized Long idat(long seg, Coord c) {
        Map<Coord, Long> m = segs.get(seg);
        return m == null ? null : m.get(c);
    }

    public synchronized void save(boolean force) {
        if (!dirty || (!force && System.currentTimeMillis() - lastsave < SAVE_MS)) {
            return;
        }
        lastsave = System.currentTimeMillis();

        try {
            dir.mkdirs();
            File f = new File(dir, "grids.tsv");
            File tmp = new File(dir, "grids.tsv.tmp");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                w.write("# grid id\tmap\tx\ty\tsource\n");

                for (Map.Entry<Long, FayteMapStore.Pos> e : grids.entrySet()) {
                    w.write(e.getKey() + "\t" + e.getValue().seg + "\t" + e.getValue().c.x + "\t" + e.getValue().c.y
                            + (old(e.getKey()) ? "\told" : "") + "\n");
                }
            }
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            File im = new File(dir, "imported.tsv");
            StringBuilder sb = new StringBuilder();

            for (Map.Entry<String, Long> e : imported.entrySet()) {
                sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
            }
            FaytePaths.write(im, sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder rb = new StringBuilder();

            for (Map.Entry<Long, Long> e : replaced.entrySet()) {
                rb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
            }
            FaytePaths.write(new File(dir, "replaced.tsv"), rb.toString().getBytes(StandardCharsets.UTF_8));
            dirty = false;
        } catch (Exception e) {
            FayteLog.log("Map store: could not save: " + e);
        }
    }

    private static List<Map<Long, Coord>> components(Map<Long, Coord> found) {
        Map<Coord, Long> byc = new HashMap<>();

        for (Map.Entry<Long, Coord> e : found.entrySet()) {
            byc.put(e.getValue(), e.getKey());
        }
        List<Map<Long, Coord>> ret = new ArrayList<>();
        Set<Long> done = new HashSet<>();

        for (Long start : found.keySet()) {
            if (done.contains(start)) {
                continue;
            }
            Map<Long, Coord> comp = new LinkedHashMap<>();
            Deque<Long> q = new ArrayDeque<>();
            q.add(start);
            done.add(start);

            while (!q.isEmpty()) {
                long id = q.poll();
                Coord c = found.get(id);
                comp.put(id, c);

                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        Long n = byc.get(c.add(dx, dy));
                        if (n != null && done.add(n)) {
                            q.add(n);
                        }
                    }
                }
            }
            ret.add(comp);
        }
        return ret;
    }

    private boolean canmerge(long from, long into, Coord shift) {
        Map<Coord, Long> src = segs.get(from);
        Map<Coord, Long> dst = segs.get(into);
        if (src == null || dst == null) {
            return true;
        }
        for (Map.Entry<Coord, Long> e : src.entrySet()) {
            Long other = dst.get(e.getKey().add(shift));
            if (other != null && !other.equals(e.getValue()) && !old(other) && !old(e.getValue())) {
                return false;
            }
        }
        return true;
    }

    private void drop(long id, long by) {
        FayteMapStore.Pos p = grids.remove(id);
        if (p != null) {
            Map<Coord, Long> sm = segs.get(p.seg);
            if (sm != null && Long.valueOf(id).equals(sm.get(p.c))) {
                sm.remove(p.c);
            }
            replaced.put(id, by);
            legacy.remove(id);
            tilefile(id).delete();
            dirty = true;
        }
    }

    private void merge(long from, long into, Coord shift) {
        Map<Coord, Long> src = segs.get(from);
        if (src == null) {
            return;
        }
        Map<Coord, Long> dst = segs.computeIfAbsent(into, k -> new HashMap<>());

        for (Map.Entry<Coord, Long> e : new ArrayList<>(src.entrySet())) {
            Coord nc = e.getKey().add(shift);
            Long other = dst.get(nc);
            if (other != null && !other.equals(e.getValue())) {
                if (old(other)) {
                    drop(other, e.getValue());
                } else {
                    drop(e.getValue(), other);
                    continue;
                }
            }
            set(e.getValue(), into, nc);
        }
        segs.remove(from);
    }

    public synchronized Map<Long, Coord> ingest(Map<Long, Coord> found) {
        FayteMapSeen.mark(this, found.keySet());
        Map<Long, Coord> placed = new HashMap<>();

        for (Map<Long, Coord> comp : components(found)) {
            List<Long> known = new ArrayList<>();

            for (Long id : comp.keySet()) {
                if (grids.containsKey(id)) {
                    known.add(id);
                }
            }
            long seg;
            Coord off;
            if (known.isEmpty()) {
                seg = nextseg++;
                off = Coord.z;
            } else {
                long best = known.get(0);

                for (Long k : known) {
                    long ks = grids.get(k).seg;
                    if (segs.get(ks).size() > segs.get(grids.get(best).seg).size()) {
                        best = k;
                    }
                }
                FayteMapStore.Pos bp = grids.get(best);
                seg = bp.seg;
                off = bp.c.sub(comp.get(best));
                boolean ok = true;

                for (Long k : known) {
                    FayteMapStore.Pos kp = grids.get(k);
                    Coord want = comp.get(k).add(off);
                    if (kp.seg == seg) {
                        if (!kp.c.equals(want)) {
                            ok = false;
                        }
                    } else {
                        Coord shift = want.sub(kp.c);
                        if (canmerge(kp.seg, seg, shift)) {
                            merge(kp.seg, seg, shift);
                        } else {
                            ok = false;
                        }
                    }
                }
                if (!ok) {
                    conflicts++;
                    FayteLog.log("Map store: grids that don't fit the known map were kept apart (" + comp.size()
                            + " grids)");
                    continue;
                }
            }
            Map<Coord, Long> sm = segs.computeIfAbsent(seg, k -> new HashMap<>());

            for (Map.Entry<Long, Coord> e : comp.entrySet()) {
                Coord want = e.getValue().add(off);
                if (!grids.containsKey(e.getKey())) {
                    Long other = sm.get(want);
                    if (other != null && old(other)) {
                        drop(other, e.getKey());
                        other = null;
                    }
                    if (other == null) {
                        set(e.getKey(), seg, want);
                    } else {
                        conflicts++;
                        continue;
                    }
                }
                placed.put(e.getKey(), want);
            }
        }
        return placed;
    }

    public synchronized long resolve(long id) {
        for (int i = 0; i < 16 && !grids.containsKey(id) && replaced.containsKey(id); i++) {
            id = replaced.get(id);
        }
        return id;
    }

    public synchronized FayteMapStore.Loc anchorloc(long id, int tx, int ty) {
        FayteMapStore.Pos p = grids.get(resolve(id));
        return p == null
                ? null
                : new FayteMapStore.Loc(
                        p.seg, p.c.x + tx / (double) MCache.cmaps.x, p.c.y + ty / (double) MCache.cmaps.y);
    }

    public synchronized long[] anchor(long seg, double x, double y) {
        Map<Coord, Long> sm = segs.get(seg);
        if (sm == null || sm.isEmpty()) {
            return null;
        }
        Coord gc = new Coord((int) Math.floor(x), (int) Math.floor(y));
        Long best = null;
        Coord bc = null;
        double bd = Double.MAX_VALUE;

        for (Map.Entry<Coord, Long> e : sm.entrySet()) {
            double d = e.getKey().dist(gc) + (old(e.getValue()) ? 1000.0 : 0.0);
            if (d < bd) {
                bd = d;
                best = e.getValue();
                bc = e.getKey();
            }
        }
        return new long[] {best, Math.round((x - bc.x) * MCache.cmaps.x), Math.round((y - bc.y) * MCache.cmaps.y)};
    }

    public static Map<Long, Coord> loaded(MCache map) {
        Map<Long, Coord> found = new HashMap<>();
        synchronized (map.grids) {
            for (Map.Entry<Coord, MCache.Grid> e : map.grids.entrySet()) {
                MCache.Grid g = e.getValue();
                if (g != null && g.id != 0L) {
                    found.put(g.id, g.gc);
                }
            }
        }
        return found;
    }

    public static Map<Long, Coord> near(MCache map, Coord center) {
        Map<Long, Coord> found = new HashMap<>();
        synchronized (map.grids) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    MCache.Grid g = map.grids.get(center.add(dx, dy));
                    if (g != null && g.id != 0L && g.gc.equals(center.add(dx, dy))) {
                        found.put(g.id, g.gc);
                    }
                }
            }
        }
        return found;
    }

    private static volatile long quietuntil = 0L;
    private static Coord lastpg = null;

    public static boolean quiet() {
        return System.currentTimeMillis() < quietuntil;
    }

    public static long gridid(MCache map, Coord cg) {
        synchronized (map.grids) {
            MCache.Grid g = map.grids.get(cg);
            return g == null ? 0L : g.id;
        }
    }

    public void observe(MCache map, Coord center) {
        if (quiet()) {
            return;
        }
        Map<Long, Coord> found = near(map, center);
        if (!found.isEmpty()) {
            ingest(found);
            save(false);
        }
    }

    public FayteMapStore.Loc where(MCache map, Coord tile) {
        if (tile == null) {
            return null;
        }
        Coord cg = tile.div(MCache.cmaps);
        long id = 0L;
        synchronized (map.grids) {
            MCache.Grid g = map.grids.get(cg);
            if (g != null) {
                id = g.id;
            }
        }
        if (id == 0L) {
            return null;
        }
        FayteMapStore.Pos p = pos(id);
        return p == null
                ? null
                : new FayteMapStore.Loc(
                        p.seg,
                        p.c.x + (tile.x - cg.x * MCache.cmaps.x + 0.5) / MCache.cmaps.x,
                        p.c.y + (tile.y - cg.y * MCache.cmaps.y + 0.5) / MCache.cmaps.y);
    }

    public static FayteMapStore current() {
        return get(WorldMapData.mapfolder());
    }

    private final Map<Long, Object[]> held = new LinkedHashMap<>();

    public void live(MCache map, Coord cg, BufferedImage img, long id) {
        if (id != 0L && quiet()) {
            synchronized (held) {
                held.put(id, new Object[] {map, cg, img});
                if (held.size() > 400) {
                    held.remove(held.keySet().iterator().next());
                }
            }
            return;
        }
        if (id == 0L || gridid(map, cg) != id) {
            return;
        }
        Map<Long, Coord> found = near(map, cg);
        if (!found.containsKey(id)) {
            return;
        }
        ingest(found);
        if (pos(id) != null) {
            try {
                tiles.mkdirs();
                File tmp = new File(tiles, id + ".png.tmp");
                ImageIO.write(img, "png", tmp);
                Files.move(tmp.toPath(), tilefile(id).toPath(), StandardCopyOption.REPLACE_EXISTING);
                synchronized (this) {
                    content.remove(id);
                }
            } catch (Exception e) {
                FayteLog.log("Map store: could not save tile " + id + ": " + e);
            }
        }
        save(false);
    }

    private static Map<Long, Coord> readgrids(File f) {
        Map<Long, Coord> ret = new LinkedHashMap<>();
        if (f.exists()) {
            try {
                for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    String[] p = line.trim().split(" ");
                    if (p.length == 3) {
                        ret.put(Long.parseLong(p[2]), new Coord(Integer.parseInt(p[0]), Integer.parseInt(p[1])));
                    }
                }
            } catch (Exception e) {
                FayteLog.once("FayteMapStore.readgrids", e);
            }
        }
        return ret;
    }

    public synchronized int importsessions() {
        File[] ss = server.listFiles(File::isDirectory);
        if (ss == null) {
            return 0;
        }
        Arrays.sort(ss, (a, b) -> a.getName().compareTo(b.getName()));
        int n = 0;

        for (File s : ss) {
            File gf = new File(s, "grids.txt");
            if (s.getName().equals(DIR) || !gf.exists()) {
                continue;
            }
            Long seen = imported.get(s.getName());
            if (seen != null && seen == gf.lastModified()) {
                continue;
            }
            Map<Long, Coord> found = readgrids(gf);
            Map<Long, Coord> placed = ingest(found);

            for (Map.Entry<Long, Coord> e : found.entrySet()) {
                if (placed.containsKey(e.getKey())) {
                    File tf = new File(s, String.format("tile_%d_%d.png", e.getValue().x, e.getValue().y));
                    File dst = tilefile(e.getKey());
                    if (tf.exists() && (!dst.exists() || dst.lastModified() < tf.lastModified())) {
                        try {
                            tiles.mkdirs();
                            Files.copy(
                                    tf.toPath(),
                                    dst.toPath(),
                                    StandardCopyOption.REPLACE_EXISTING,
                                    StandardCopyOption.COPY_ATTRIBUTES);
                        } catch (Exception ex) {
                            FayteLog.once("FayteMapStore.importsessions", ex);
                        }
                    }
                }
            }
            imported.put(s.getName(), gf.lastModified());
            dirty = true;
            n++;
        }
        save(true);
        return n;
    }

    public boolean migrated() {
        return new File(dir, "migrated.done").exists();
    }

    public File mergedlist() {
        return new File(dir, "merged_sessions.txt");
    }

    private static long filehash(File f) {
        try {
            byte[] d = Files.readAllBytes(f.toPath());
            return d.length < 1000 ? 0L : WorldMapIndex.hash(d);
        } catch (Exception e) {
            return 0L;
        }
    }

    private synchronized long newseg() {
        return nextseg++;
    }

    private synchronized List<long[]> placelegacy(long seg, Coord off, Map<Coord, File> tiles) {
        List<long[]> placed = new ArrayList<>();
        Map<Coord, Long> sm = segs.computeIfAbsent(seg, k -> new HashMap<>());

        for (Map.Entry<Coord, File> t : tiles.entrySet()) {
            Coord p = t.getKey().add(off);
            if (!sm.containsKey(p)) {
                long id = nextlegacy++;
                legacy.add(id);
                try {
                    this.tiles.mkdirs();
                    Files.copy(t.getValue().toPath(), tilefile(id).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    set(id, seg, p);
                    placed.add(new long[] {seg, p.x, p.y, filehash(t.getValue())});
                } catch (Exception e) {
                    FayteLog.once("FayteMapStore.placelegacy", e);
                }
            }
        }
        dirty = true;
        return placed;
    }

    public void migrate(Consumer<String> progress) throws InterruptedException {
        progress.accept("Merging your old map: reading sessions with grid IDs...");
        importsessions();
        progress.accept("Merging your old map: indexing old sessions...");
        WorldMapIndex idx = new WorldMapIndex(server);
        idx.minisland = 1;
        Set<String> marked = WorldMapMarkers.legacysessions();
        Set<String> blank = new HashSet<>();
        WorldMapIndex.Result lr = idx.build(null, null);
        Map<Long, List<long[]>> exh = new HashMap<>();
        Map<Long, Map<Coord, Long>> snap;
        synchronized (this) {
            snap = new HashMap<>();

            for (Map.Entry<Long, Map<Coord, Long>> e : segs.entrySet()) {
                snap.put(e.getKey(), new HashMap<>(e.getValue()));
            }
        }
        for (Map.Entry<Long, Map<Coord, Long>> e : snap.entrySet()) {
            for (Map.Entry<Coord, Long> t : e.getValue().entrySet()) {
                long h = filehash(tilefile(t.getValue()));
                if (h != 0L) {
                    exh.computeIfAbsent(h, k -> new ArrayList<>())
                            .add(new long[] {e.getKey(), t.getKey().x, t.getKey().y});
                }
            }
        }
        Map<String, long[]> sessloc = new HashMap<>();
        int n = 0;
        int joined = 0;
        int alone = 0;
        int dups = 0;
        int skipped = 0;

        for (WorldMapIndex.Island isl : lr.islands) {
            if (++n % 50 == 0) {
                progress.accept("Merging your old map: " + n + " / " + lr.islands.size() + " areas...");
            }
            Map<String, Integer> votes = new HashMap<>();
            Map<String, Integer> full = new HashMap<>();
            Map<String, long[]> keys = new HashMap<>();
            int hashed = 0;

            for (Map.Entry<Coord, File> t : isl.tiles.entrySet()) {
                long h = filehash(t.getValue());
                if (h == 0L) {
                    continue;
                }
                hashed++;
                List<long[]> hits = exh.get(h);
                if (hits != null && hits.size() <= 64) {
                    Set<String> seen = new HashSet<>();

                    for (long[] e : hits) {
                        long[] k = {e[0], e[1] - t.getKey().x, e[2] - t.getKey().y};
                        String ks = k[0] + ":" + k[1] + ":" + k[2];
                        if (seen.add(ks)) {
                            full.merge(ks, 1, Integer::sum);
                            if (hits.size() <= 4) {
                                votes.merge(ks, 1, Integer::sum);
                            }
                            keys.put(ks, k);
                        }
                    }
                }
            }
            String dup = null;
            if (hashed >= 3) {
                for (Map.Entry<String, Integer> v : full.entrySet()) {
                    if (v.getValue() == hashed) {
                        dup = v.getKey();
                        break;
                    }
                }
            }
            String best = null;
            int bv = 0;
            int sv = 0;

            for (Map.Entry<String, Integer> v : votes.entrySet()) {
                if (v.getValue() > bv) {
                    sv = bv;
                    bv = v.getValue();
                    best = v.getKey();
                } else if (v.getValue() > sv) {
                    sv = v.getValue();
                }
            }
            boolean hasmark = false;

            for (String o : isl.offsets.keySet()) {
                if (marked.contains(o)) {
                    hasmark = true;
                }
            }
            if (hashed == 0 && !hasmark) {
                blank.addAll(isl.offsets.keySet());
                skipped++;
                continue;
            }
            long seg;
            Coord off;
            if (dup != null) {
                long[] k = keys.get(dup);
                seg = k[0];
                off = new Coord((int) k[1], (int) k[2]);
                dups++;
            } else if (best != null && bv >= 2 && bv >= 3 * sv) {
                long[] k = keys.get(best);
                seg = k[0];
                off = new Coord((int) k[1], (int) k[2]);
                joined++;
            } else {
                seg = newseg();
                off = Coord.z;
                alone++;
            }
            for (long[] p : placelegacy(seg, off, isl.tiles)) {
                if (p[3] != 0L) {
                    exh.computeIfAbsent(p[3], k -> new ArrayList<>()).add(new long[] {p[0], p[1], p[2]});
                }
            }
            for (Map.Entry<String, Coord> o : isl.offsets.entrySet()) {
                sessloc.put(o.getKey(), new long[] {seg, o.getValue().x + off.x, o.getValue().y + off.y});
            }
        }

        synchronized (this) {
            File[] ss = server.listFiles(File::isDirectory);
            if (ss != null) {
                for (File sd : ss) {
                    String[] pngs = sd.list((d, nm) -> nm.startsWith("tile_"));
                    if (!sd.getName().equals(DIR) && pngs != null && pngs.length == 0) {
                        blank.add(sd.getName());
                    }
                    for (Map.Entry<Long, Coord> e :
                            readgrids(new File(sd, "grids.txt")).entrySet()) {
                        FayteMapStore.Pos p = grids.get(e.getKey());
                        if (p != null) {
                            sessloc.put(
                                    sd.getName(), new long[] {p.seg, p.c.x - e.getValue().x, p.c.y - e.getValue().y});
                            break;
                        }
                    }
                }
            }
        }
        progress.accept("Merging your old map: moving your markers...");
        WorldMapMarkers.convert(sessloc, this);
        StringBuilder sb = new StringBuilder();

        for (String k : sessloc.keySet()) {
            sb.append(k).append('\n');
        }
        for (String k : imported.keySet()) {
            if (!sessloc.containsKey(k)) {
                sb.append(k).append('\n');
            }
        }
        for (String k : blank) {
            if (!sessloc.containsKey(k) && !imported.containsKey(k)) {
                sb.append(k).append('\n');
            }
        }
        try {
            dir.mkdirs();
            FaytePaths.write(mergedlist(), sb.toString().getBytes(StandardCharsets.UTF_8));
            save(true);
            Files.write(
                    new File(dir, "migrated.done").toPath(),
                    (joined + " old areas joined, " + alone + " kept as their own maps\n")
                            .getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.log("Map store: could not finish merging: " + e);
        }
        FayteLog.log("Map store: merged old map, " + joined + " areas joined, " + dups + " repeats folded, " + alone
                + " kept apart, " + skipped + " empty skipped, " + sessloc.size() + " sessions covered");
    }

    public Set<String> cleanable() {
        Set<String> ret = new HashSet<>();
        File f = mergedlist();
        if (f.exists()) {
            try {
                for (String l : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    if (!l.trim().isEmpty() && new File(server, l.trim()).isDirectory()) {
                        ret.add(l.trim());
                    }
                }
            } catch (Exception e) {
                FayteLog.once("FayteMapStore.cleanable", e);
            }
        }
        return ret;
    }

    private static void rmtree(File f) {
        File[] fs = f.listFiles();
        if (fs != null) {
            for (File c : fs) {
                rmtree(c);
            }
        }
        f.delete();
    }

    public int cleanup(String keep, Consumer<String> progress) {
        Set<String> cl = cleanable();
        int n = 0;

        for (String s : cl) {
            if (s.equals(keep) || s.equals(DIR)) {
                continue;
            }
            rmtree(new File(server, s));
            if (++n % 500 == 0) {
                progress.accept("Cleaning up old map files: " + n + " / " + cl.size() + "...");
            }
        }
        new File(server, "worldmap_index.txt").delete();
        mergedlist().delete();
        FayteLog.log("Map store: deleted " + n + " old session folders");
        return n;
    }

    public synchronized WorldMapIndex.Result result(long current, Set<Long> keep, int mintiles) {
        WorldMapIndex.Result r = new WorldMapIndex.Result();
        WorldMapIndex.Island cur = null;

        for (Map.Entry<Long, Map<Coord, Long>> se : segs.entrySet()) {
            WorldMapIndex.Island isl = new WorldMapIndex.Island();
            isl.seg = se.getKey();

            for (Map.Entry<Coord, Long> t : se.getValue().entrySet()) {
                if (!FayteMapSeen.visible(this, t.getValue())) {
                    continue;
                }
                File f = tilefile(t.getValue());
                isl.tiles.put(t.getKey(), f);
                Coord c = t.getKey();
                isl.min = isl.min == null ? c : new Coord(Math.min(isl.min.x, c.x), Math.min(isl.min.y, c.y));
                isl.max = isl.max == null ? c : new Coord(Math.max(isl.max.x, c.x), Math.max(isl.max.y, c.y));
            }
            boolean wanted =
                    (isl.tiles.size() >= mintiles && hascontent(se.getValue().values()))
                            || (keep != null && keep.contains(isl.seg));
            if (isl.seg == current) {
                cur = isl;
                wanted = true;
            }
            if (wanted && !isl.tiles.isEmpty()) {
                r.islands.add(isl);
            }
        }
        r.islands.sort((x, y) -> y.tiles.size() - x.tiles.size());
        if (cur != null && r.islands.remove(cur)) {
            r.islands.add(0, cur);
            r.hascurrent = true;
        }
        return r;
    }

    private boolean hascontent(Collection<Long> ids) {
        for (Long id : ids) {
            if (content.computeIfAbsent(id, k -> tilefile(k).length() >= 1000L)) {
                return true;
            }
        }
        return false;
    }

    public WorldMapIndex.Island island(WorldMapIndex.Result r, long seg) {
        if (r != null) {
            for (WorldMapIndex.Island i : r.islands) {
                if (i.seg == seg) {
                    return i;
                }
            }
        }
        return null;
    }

    private static long lastobserve = 0L;

    private void flushheld() {
        List<Object[]> l = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        synchronized (held) {
            for (Map.Entry<Long, Object[]> e : held.entrySet()) {
                ids.add(e.getKey());
                l.add(e.getValue());
            }
            held.clear();
        }
        for (int i = 0; i < l.size(); i++) {
            Object[] h = l.get(i);
            live((MCache) h[0], (Coord) h[1], (BufferedImage) h[2], ids.get(i));
        }
    }

    public static void tickobserve(GameUI gui) {
        long now = System.currentTimeMillis();
        if (now - lastobserve > 1000L
                && FayteModules.WORLDMAP.on()
                && gui != null
                && gui.ui != null
                && gui.ui.sess != null) {
            lastobserve = now;
            try {
                Gob pl = gui.map == null ? null : gui.map.player();
                if (pl == null || pl.rc == null) {
                    return;
                }
                Coord pg = pl.rc.div(MCache.tilesz).div(MCache.cmaps);
                if (lastpg != null && pg.manhattan(lastpg) > 3) {
                    quietuntil = now + 5000L;
                    FayteLog.log("Map store: big jump in position, pausing map saving for 5 s");
                }
                lastpg = pg;
                current().observe(gui.ui.sess.glob.map, pg);
                if (!quiet()) {
                    FayteMapStore st = current();
                    boolean any;
                    synchronized (st.held) {
                        any = !st.held.isEmpty();
                    }
                    if (any) {
                        Thread t = new Thread(st::flushheld, "Map store held tiles");
                        t.setDaemon(true);
                        t.start();
                    }
                }
            } catch (RuntimeException e) {
                FayteLog.once("FayteMapStore.tickobserve", e);
            }
        }
    }
}
