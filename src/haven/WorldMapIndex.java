package haven;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorldMapIndex {
    public static final String CACHE_NAME = "worldmap_index.txt";
    private static final int UNIFORM_BYTES = 1000;
    private static final int ALL_PAIRS_LIMIT = 64;
    private static final int ANCHORS = 8;
    private static final int GRIDID_WEIGHT = 100;
    private static final int MIN_ISLAND_TILES = 20;
    private final File folder;
    public volatile int scanned = 0;
    public volatile int total = 0;

    public int minisland = MIN_ISLAND_TILES;

    public WorldMapIndex(File folder) {
        this.folder = folder;
    }

    static class Tile {
        final int x;
        final int y;
        final long hash;
        final boolean uniform;

        Tile(int x, int y, long hash, boolean uniform) {
            this.x = x;
            this.y = y;
            this.hash = hash;
            this.uniform = uniform;
        }
    }

    static class Session {
        final String name;
        long mtime;
        final List<WorldMapIndex.Tile> tiles = new ArrayList<>();
        final Map<Coord, Long> gridids = new HashMap<>();

        Session(String name, long mtime) {
            this.name = name;
            this.mtime = mtime;
        }
    }

    static class Cand {
        final WorldMapIndex.Tile tile;
        final File file;
        final int session;

        Cand(WorldMapIndex.Tile tile, File file, int session) {
            this.tile = tile;
            this.file = file;
            this.session = session;
        }
    }

    public static class Island {
        public final Map<Coord, File> tiles = new HashMap<>();
        public final Map<String, Coord> offsets = new HashMap<>();
        public long seg;
        public int linked;
        public Coord min;
        public Coord max;
    }

    public static class Result {
        public final List<WorldMapIndex.Island> islands = new ArrayList<>();
        public int sessions;
        public boolean hascurrent;

        public WorldMapIndex.Island islandof(String session) {
            if (session != null) {
                for (WorldMapIndex.Island isl : islands) {
                    if (isl.offsets.containsKey(session)) {
                        return isl;
                    }
                }
            }
            return null;
        }
    }

    public WorldMapIndex.Result build(String current, Set<String> keep) throws InterruptedException {
        Map<String, WorldMapIndex.Session> cached = loadcache();
        File[] dirs = folder.listFiles(File::isDirectory);
        if (dirs == null) {
            dirs = new File[0];
        }
        Arrays.sort(dirs);
        total = dirs.length;
        List<WorldMapIndex.Session> sessions = new ArrayList<>(dirs.length);
        boolean changed = cached.size() != dirs.length;

        for (File dir : dirs) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            WorldMapIndex.Session s = cached.get(dir.getName());
            long mtime = dir.lastModified();
            if (s == null || s.mtime != mtime) {
                s = scan(dir, mtime);
                changed = true;
            }
            sessions.add(s);
            scanned++;
        }
        if (changed) {
            savecache(sessions);
        }
        return link(sessions, current, keep);
    }

    private WorldMapIndex.Session scan(File dir, long mtime) {
        WorldMapIndex.Session s = new WorldMapIndex.Session(dir.getName(), mtime);
        File[] files = dir.listFiles();
        if (files == null) {
            return s;
        }
        for (File f : files) {
            String n = f.getName();
            if (n.startsWith("tile_") && n.endsWith(".png")) {
                String[] p = n.substring(5, n.length() - 4).split("_");
                if (p.length == 2) {
                    try {
                        byte[] data = Files.readAllBytes(f.toPath());
                        s.tiles.add(new WorldMapIndex.Tile(
                                Integer.parseInt(p[0]),
                                Integer.parseInt(p[1]),
                                hash(data),
                                data.length < UNIFORM_BYTES));
                    } catch (IOException | NumberFormatException e) {
                        FayteLog.once("WorldMapIndex.scan", e);
                    }
                }
            } else if (n.equals("grids.txt")) {
                readgridids(f, s);
            }
        }
        return s;
    }

    private static void readgridids(File f, WorldMapIndex.Session s) {
        try (BufferedReader in =
                new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = in.readLine()) != null) {
                String[] p = ln.trim().split(" ");
                if (p.length == 3) {
                    try {
                        s.gridids.put(new Coord(Integer.parseInt(p[0]), Integer.parseInt(p[1])), Long.parseLong(p[2]));
                    } catch (NumberFormatException e) {
                    }
                }
            }
        } catch (IOException e) {
            FayteLog.once("WorldMapIndex.readgridids", e);
        }
    }

    static long hash(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(data);
            long h = 0L;
            for (int i = 0; i < 8; i++) {
                h = h << 8 | d[i] & 255;
            }
            return h;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, WorldMapIndex.Session> loadcache() {
        Map<String, WorldMapIndex.Session> ret = new HashMap<>();
        File f = new File(folder, CACHE_NAME);
        if (!f.exists()) {
            return ret;
        }
        try (BufferedReader in =
                new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            WorldMapIndex.Session cur = null;
            String ln;
            while ((ln = in.readLine()) != null) {
                if (ln.startsWith("S ")) {
                    int sp = ln.indexOf(' ', 2);
                    cur = new WorldMapIndex.Session(ln.substring(sp + 1), Long.parseLong(ln.substring(2, sp)));
                    ret.put(cur.name, cur);
                } else if (cur != null && ln.startsWith("T ")) {
                    String[] p = ln.split(" ");
                    cur.tiles.add(new WorldMapIndex.Tile(
                            Integer.parseInt(p[1]),
                            Integer.parseInt(p[2]),
                            Long.parseUnsignedLong(p[3], 16),
                            p[4].equals("1")));
                } else if (cur != null && ln.startsWith("G ")) {
                    String[] p = ln.split(" ");
                    cur.gridids.put(new Coord(Integer.parseInt(p[1]), Integer.parseInt(p[2])), Long.parseLong(p[3]));
                }
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("World map index cache unreadable, rebuilding: " + e);
            ret.clear();
        }
        return ret;
    }

    private void savecache(List<WorldMapIndex.Session> sessions) {
        File f = new File(folder, CACHE_NAME);
        File tmp = new File(folder, CACHE_NAME + ".tmp");
        try {
            try (BufferedWriter out =
                    new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
                for (WorldMapIndex.Session s : sessions) {
                    out.write("S " + s.mtime + " " + s.name + "\n");
                    for (WorldMapIndex.Tile t : s.tiles) {
                        out.write("T " + t.x + " " + t.y + " " + Long.toHexString(t.hash) + " "
                                + (t.uniform ? "1" : "0") + "\n");
                    }
                    for (Map.Entry<Coord, Long> e : s.gridids.entrySet()) {
                        out.write("G " + e.getKey().x + " " + e.getKey().y + " " + e.getValue() + "\n");
                    }
                }
            }
            try {
                Files.move(
                        tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.out.println("Could not write world map index cache: " + e);
            tmp.delete();
        }
    }

    private static long pairkey(int a, int b) {
        return (long) a << 32 | b & 0xffffffffL;
    }

    private static long offkey(int x, int y) {
        return (long) x << 32 | y & 0xffffffffL;
    }

    private static void vote(Map<Long, Map<Long, int[]>> votes, int a, int ax, int ay, int b, int bx, int by, int w) {
        if (a != b) {
            int dx = ax - bx;
            int dy = ay - by;
            if (a > b) {
                int t = a;
                a = b;
                b = t;
                dx = -dx;
                dy = -dy;
            }
            Map<Long, int[]> offs = votes.computeIfAbsent(pairkey(a, b), k -> new HashMap<>());
            offs.computeIfAbsent(offkey(dx, dy), k -> new int[1])[0] += w;
        }
    }

    private static void votegroup(Map<Long, Map<Long, int[]>> votes, List<int[]> occ, int w) {
        int n = occ.size();
        if (n < 2) {
            return;
        }
        int anchors = n <= ALL_PAIRS_LIMIT ? n : ANCHORS;
        for (int i = 0; i < anchors; i++) {
            int[] a = occ.get(i);
            for (int j = i + 1; j < n; j++) {
                int[] b = occ.get(j);
                vote(votes, a[0], a[1], a[2], b[0], b[1], b[2], w);
            }
        }
    }

    private WorldMapIndex.Result link(List<WorldMapIndex.Session> sessions, String current, Set<String> keep)
            throws InterruptedException {
        int n = sessions.size();
        Map<Long, List<int[]>> byhash = new HashMap<>();
        Map<Long, List<int[]>> byid = new HashMap<>();
        Set<Long> generic = new HashSet<>();

        for (int i = 0; i < n; i++) {
            WorldMapIndex.Session s = sessions.get(i);
            Set<Long> seen = new HashSet<>();
            for (WorldMapIndex.Tile t : s.tiles) {
                if (!t.uniform) {
                    if (!seen.add(t.hash)) {
                        generic.add(t.hash);
                    }
                    byhash.computeIfAbsent(t.hash, k -> new ArrayList<>()).add(new int[] {i, t.x, t.y});
                }
            }
            for (Map.Entry<Coord, Long> e : s.gridids.entrySet()) {
                byid.computeIfAbsent(e.getValue(), k -> new ArrayList<>())
                        .add(new int[] {i, e.getKey().x, e.getKey().y});
            }
        }
        Map<Long, Map<Long, int[]>> votes = new HashMap<>();
        for (Map.Entry<Long, List<int[]>> e : byhash.entrySet()) {
            if (!generic.contains(e.getKey())) {
                votegroup(votes, e.getValue(), 1);
            }
        }
        for (List<int[]> occ : byid.values()) {
            votegroup(votes, occ, GRIDID_WEIGHT);
        }
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        List<long[]> accepted = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, int[]>> e : votes.entrySet()) {
            long best = 0L;
            int c1 = 0;
            int c2 = 0;
            for (Map.Entry<Long, int[]> o : e.getValue().entrySet()) {
                int c = o.getValue()[0];
                if (c > c1) {
                    c2 = c1;
                    c1 = c;
                    best = o.getKey();
                } else if (c > c2) {
                    c2 = c;
                }
            }
            if (c1 >= 3 * c2 && (c1 >= 2 || c2 == 0)) {
                accepted.add(new long[] {e.getKey(), best, c1});
            }
        }
        Collections.sort(accepted, (x, y) -> Long.compare(y[2], x[2]));
        int[] parent = new int[n];
        int[] ox = new int[n];
        int[] oy = new int[n];
        int[] size = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            size[i] = 1;
        }
        for (long[] l : accepted) {
            int a = (int) (l[0] >>> 32);
            int b = (int) l[0];
            int dx = (int) (l[1] >> 32);
            int dy = (int) l[1];
            int ra = find(parent, ox, oy, a);
            int rb = find(parent, ox, oy, b);
            if (ra != rb) {
                int nx = ox[a] + dx - ox[b];
                int ny = oy[a] + dy - oy[b];
                if (size[ra] >= size[rb]) {
                    parent[rb] = ra;
                    ox[rb] = nx;
                    oy[rb] = ny;
                    size[ra] += size[rb];
                } else {
                    parent[ra] = rb;
                    ox[ra] = -nx;
                    oy[ra] = -ny;
                    size[rb] += size[ra];
                }
            }
        }
        int curroot = -1;
        if (current != null) {
            for (int i = 0; i < n; i++) {
                if (sessions.get(i).name.equals(current)) {
                    curroot = find(parent, ox, oy, i);
                    break;
                }
            }
        }
        Map<Integer, WorldMapIndex.Island> byroot = new HashMap<>();
        Map<WorldMapIndex.Island, Map<Coord, List<WorldMapIndex.Cand>>> cands = new HashMap<>();
        Map<WorldMapIndex.Island, Map<Long, Map<Coord, int[]>>> hashcells = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int r = find(parent, ox, oy, i);
            WorldMapIndex.Session s = sessions.get(i);
            WorldMapIndex.Island isl = byroot.computeIfAbsent(r, k -> new WorldMapIndex.Island());
            isl.offsets.put(s.name, new Coord(ox[i], oy[i]));
            isl.linked++;
            Map<Coord, List<WorldMapIndex.Cand>> ic = cands.computeIfAbsent(isl, k -> new HashMap<>());
            Map<Long, Map<Coord, int[]>> ih = hashcells.computeIfAbsent(isl, k -> new HashMap<>());
            File dir = new File(folder, s.name);
            for (WorldMapIndex.Tile t : s.tiles) {
                Coord wc = new Coord(ox[i] + t.x, oy[i] + t.y);
                ic.computeIfAbsent(wc, k -> new ArrayList<>())
                        .add(new WorldMapIndex.Cand(t, new File(dir, "tile_" + t.x + "_" + t.y + ".png"), i));
                if (!t.uniform) {
                    ih.computeIfAbsent(t.hash, k -> new HashMap<>()).computeIfAbsent(wc, k -> new int[1])[0]++;
                }
            }
        }
        for (WorldMapIndex.Island isl : byroot.values()) {
            Map<Long, Map<Coord, int[]>> ih = hashcells.get(isl);
            Set<Integer> suspect = new HashSet<>();
            for (Map.Entry<Coord, List<WorldMapIndex.Cand>> e : cands.get(isl).entrySet()) {
                for (WorldMapIndex.Cand c : e.getValue()) {
                    if (!c.tile.uniform && placing(ih.get(c.tile.hash), e.getKey()) < 0) {
                        suspect.add(c.session);
                    }
                }
            }
            for (Map.Entry<Coord, List<WorldMapIndex.Cand>> e : cands.get(isl).entrySet()) {
                List<WorldMapIndex.Cand> l = e.getValue();
                for (int j = l.size() - 1; j >= 0; j--) {
                    WorldMapIndex.Cand c = l.get(j);
                    int p = c.tile.uniform ? 1 : placing(ih.get(c.tile.hash), e.getKey());
                    if (p > 0 || p == 0 && !suspect.contains(c.session)) {
                        isl.tiles.put(e.getKey(), c.file);
                        break;
                    }
                }
            }
        }
        WorldMapIndex.Result res = new WorldMapIndex.Result();
        res.sessions = n;
        WorldMapIndex.Island cur = curroot < 0 ? null : byroot.get(curroot);
        List<WorldMapIndex.Island> rest = new ArrayList<>();
        for (WorldMapIndex.Island isl : byroot.values()) {
            if (isl != cur && !isl.tiles.isEmpty() && (isl.tiles.size() >= minisland || keeps(isl, keep))) {
                rest.add(isl);
            }
        }
        Collections.sort(rest, (x, y) -> Integer.compare(y.tiles.size(), x.tiles.size()));
        if (cur != null && !cur.tiles.isEmpty()) {
            res.islands.add(cur);
            res.hascurrent = true;
        }
        res.islands.addAll(rest);
        for (WorldMapIndex.Island isl : res.islands) {
            int minx = Integer.MAX_VALUE;
            int miny = Integer.MAX_VALUE;
            int maxx = Integer.MIN_VALUE;
            int maxy = Integer.MIN_VALUE;
            for (Coord wc : isl.tiles.keySet()) {
                minx = Math.min(minx, wc.x);
                miny = Math.min(miny, wc.y);
                maxx = Math.max(maxx, wc.x);
                maxy = Math.max(maxy, wc.y);
            }
            isl.min = new Coord(minx, miny);
            isl.max = new Coord(maxx, maxy);
        }
        return res;
    }

    private static boolean keeps(WorldMapIndex.Island isl, Set<String> keep) {
        if (keep != null) {
            for (String s : keep) {
                if (isl.offsets.containsKey(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int placing(Map<Coord, int[]> cells, Coord wc) {
        int here = cells.get(wc)[0];
        int ret = 1;
        for (Map.Entry<Coord, int[]> c : cells.entrySet()) {
            if (!c.getKey().equals(wc)) {
                if (c.getValue()[0] > here) {
                    return -1;
                }
                if (c.getValue()[0] == here) {
                    ret = 0;
                }
            }
        }
        return ret;
    }

    private static int find(int[] parent, int[] ox, int[] oy, int i) {
        int p = parent[i];
        if (p == i) {
            return i;
        }
        int r = find(parent, ox, oy, p);
        if (p != r) {
            ox[i] += ox[p];
            oy[i] += oy[p];
        }
        parent[i] = r;
        return r;
    }
}
