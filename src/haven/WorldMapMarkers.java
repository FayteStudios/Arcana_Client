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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorldMapMarkers {
    public static final String FILE_NAME = "worldmap_markers.txt";
    public static final String ANCHOR = "g:";
    public static final WorldMapMarkers.Marker HOMESTEAD =
            new WorldMapMarkers.Marker("homestead", null, 0, 0, "Homestead");
    private static final int DEDUPE_TILES = 8;
    private static List<WorldMapMarkers.Marker> markers = new ArrayList<>();
    private static String loadedfor = null;
    public static volatile WorldMapMarkers.Marker pointed = null;
    private static MarkerPointerFX fx = null;
    private static Gob fxgob = null;

    public static class Marker {
        public final String kind;
        public final String session;
        public final int tx;
        public final int ty;
        public volatile String name;

        Marker(String kind, String session, int tx, int ty, String name) {
            this.kind = kind;
            this.session = session;
            this.tx = tx;
            this.ty = ty;
            this.name = name;
        }

        public boolean anchored() {
            return session != null && session.startsWith(ANCHOR);
        }

        public long grid() {
            try {
                return Long.parseLong(session.substring(ANCHOR.length()));
            } catch (RuntimeException e) {
                return 0L;
            }
        }
    }

    public static synchronized void reload() {
        loadedfor = null;
    }

    public static File datafile() {
        return file();
    }

    private static File file() {
        String c = Config.currentCharName;
        if (FayteConfig.markersPerChar.get() && c != null && !c.isEmpty()) {
            File mine = new File(WorldMapData.mapfolder(), "worldmap_markers@" + FaytePaths.safename(c) + ".txt");
            File shared = new File(WorldMapData.mapfolder(), FILE_NAME);
            if (!mine.exists() && shared.exists() && !Utils.getprefb("fayte_markers_seeded", false)) {
                try {
                    Files.copy(shared.toPath(), mine.toPath());
                    Utils.setprefb("fayte_markers_seeded", true);
                } catch (IOException e) {
                    FayteLog.once("WorldMapMarkers.file", e);
                }
            }
            return mine;
        }
        return new File(WorldMapData.mapfolder(), FILE_NAME);
    }

    public static final String SHARED = "Everyone (from before per-character markers)";

    public static synchronized List<String> othermarkers() {
        List<String> ret = new ArrayList<>();
        if (FayteConfig.markersPerChar.get() && new File(WorldMapData.mapfolder(), FILE_NAME).exists()) {
            ret.add(SHARED);
        }
        String c = Config.currentCharName;
        String me = c == null ? "" : "worldmap_markers@" + FaytePaths.safename(c) + ".txt";
        String[] fs = WorldMapData.mapfolder().list();
        if (fs != null) {
            for (String f : fs) {
                if (f.startsWith("worldmap_markers@") && f.endsWith(".txt") && !f.equals(me)) {
                    ret.add(f.substring("worldmap_markers@".length(), f.length() - 4));
                }
            }
        }
        return ret;
    }

    public static synchronized int importfrom(String other) {
        ensure();
        File f = SHARED.equals(other)
                ? new File(WorldMapData.mapfolder(), FILE_NAME)
                : new File(WorldMapData.mapfolder(), "worldmap_markers@" + other + ".txt");
        if (!f.exists()) {
            return 0;
        }
        int n = 0;
        try (BufferedReader in =
                new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = in.readLine()) != null) {
                String[] p = ln.split("\t", 5);
                if (p.length == 5) {
                    boolean dup = false;
                    for (WorldMapMarkers.Marker m : markers) {
                        if (m.kind.equals(p[0])
                                && String.valueOf(m.session).equals(p[1])
                                && String.valueOf(m.tx).equals(p[2])
                                && String.valueOf(m.ty).equals(p[3])) {
                            dup = true;
                        }
                    }
                    if (!dup) {
                        try {
                            markers.add(new WorldMapMarkers.Marker(
                                    p[0], p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4]));
                            n++;
                        } catch (NumberFormatException e) {
                        }
                    }
                }
            }
        } catch (IOException e) {
            return 0;
        }
        save();
        return n;
    }

    private static synchronized void ensure() {
        String srv =
                String.valueOf(Config.server) + (FayteConfig.markersPerChar.get() ? "@" + Config.currentCharName : "");
        if (!srv.equals(loadedfor)) {
            loadedfor = srv;
            markers = new ArrayList<>();
            pointed = null;
            File f = file();
            if (f.exists()) {
                try (BufferedReader in =
                        new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                    String ln;
                    while ((ln = in.readLine()) != null) {
                        String[] p = ln.split("\t", 5);
                        if (p.length == 5) {
                            try {
                                markers.add(new WorldMapMarkers.Marker(
                                        p[0], p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4]));
                            } catch (NumberFormatException e) {
                            }
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Could not read " + f + ": " + e);
                }
            }
        }
    }

    private static synchronized void save() {
        File f = file();
        File tmp = new File(f.getPath() + ".tmp");
        try {
            f.getParentFile().mkdirs();
            try (BufferedWriter out =
                    new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
                for (WorldMapMarkers.Marker m : markers) {
                    out.write(m.kind + "\t" + m.session + "\t" + m.tx + "\t" + m.ty + "\t"
                            + m.name.replace('\t', ' ').replace('\n', ' ') + "\n");
                }
            }
            try {
                Files.move(
                        tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.out.println("Could not write " + f + ": " + e);
            tmp.delete();
        }
    }

    public static synchronized List<WorldMapMarkers.Marker> all() {
        ensure();
        return new ArrayList<>(markers);
    }

    public static synchronized void convert(Map<String, long[]> sessloc, FayteMapStore store) {
        ensure();
        int n = 0;

        for (int i = 0; i < markers.size(); i++) {
            WorldMapMarkers.Marker m = markers.get(i);
            long[] sl = m.anchored() ? null : sessloc.get(m.session);
            if (sl != null) {
                long[] a = store.anchor(
                        sl[0], sl[1] + m.tx / (double) MCache.cmaps.x, sl[2] + m.ty / (double) MCache.cmaps.y);
                if (a != null) {
                    WorldMapMarkers.Marker nm =
                            new WorldMapMarkers.Marker(m.kind, ANCHOR + a[0], (int) a[1], (int) a[2], m.name);
                    markers.set(i, nm);
                    if (pointed == m) {
                        pointed = nm;
                    }
                    n++;
                }
            }
        }
        if (n > 0) {
            save();
        }
    }

    public static Set<String> legacysessions() {
        Set<String> ret = new HashSet<>();

        for (WorldMapMarkers.Marker m : all()) {
            if (m.session != null && !m.anchored()) {
                ret.add(m.session);
            }
        }
        return ret;
    }

    public static Set<Long> segs() {
        Set<Long> ret = new HashSet<>();
        FayteMapStore st = FayteMapStore.current();

        for (WorldMapMarkers.Marker m : all()) {
            if (m.anchored()) {
                FayteMapStore.Loc l = st.anchorloc(m.grid(), m.tx, m.ty);
                if (l != null) {
                    ret.add(l.seg);
                }
            }
        }
        return ret;
    }

    public static synchronized WorldMapMarkers.Marker add(String kind, String name, long grid, int tx, int ty) {
        ensure();
        WorldMapMarkers.Marker m = new WorldMapMarkers.Marker(kind, ANCHOR + grid, tx, ty, name);
        markers.add(m);
        save();
        return m;
    }

    public static synchronized void remove(WorldMapMarkers.Marker m) {
        ensure();
        if (markers.remove(m)) {
            if (pointed == m) {
                pointed = null;
            }
            save();
        }
    }

    public static synchronized void rename(WorldMapMarkers.Marker m, String name) {
        ensure();
        if (markers.contains(m) && name != null && !name.trim().isEmpty()) {
            m.name = name.trim();
            save();
        }
    }

    public static synchronized String nextname() {
        ensure();
        int n = 1;
        for (WorldMapMarkers.Marker m : markers) {
            if (m.kind.equals("custom")) {
                n++;
            }
        }
        return "Marker " + n;
    }

    public static Gob player(GameUI gui) {
        return gui == null || gui.map == null ? null : gui.ui.sess.glob.oc.getgob(gui.map.plgob);
    }

    public static Coord playertile(GameUI gui) {
        Gob pl = player(gui);
        return pl == null || pl.rc == null ? null : pl.rc.div(MCache.tilesz);
    }

    public static FayteMapStore.Loc playerloc(GameUI gui) {
        Coord t = playertile(gui);
        return t == null ? null : FayteMapStore.current().where(gui.ui.sess.glob.map, t);
    }

    public static FayteMapStore.Loc tileloc(GameUI gui, Coord tile) {
        if (gui == null || tile == null) {
            return null;
        }
        FayteMapStore.Loc l = FayteMapStore.current().where(gui.ui.sess.glob.map, tile);
        if (l == null) {
            Coord pt = playertile(gui);
            FayteMapStore.Loc pl = playerloc(gui);
            if (pl != null && pt != null) {
                l = new FayteMapStore.Loc(
                        pl.seg,
                        pl.x + (tile.x - pt.x) / (double) MCache.cmaps.x,
                        pl.y + (tile.y - pt.y) / (double) MCache.cmaps.y);
            }
        }
        return l;
    }

    public static FayteMapStore.Loc loc(GameUI gui, WorldMapMarkers.Marker m) {
        if (m == null) {
            return null;
        } else if (m == HOMESTEAD) {
            Coord hc = gui == null ? null : gui.homestead();
            return hc == null ? null : tileloc(gui, hc.div(MCache.tilesz));
        } else {
            return m.anchored() ? FayteMapStore.current().anchorloc(m.grid(), m.tx, m.ty) : null;
        }
    }

    public static Coord curtile(GameUI gui, WorldMapMarkers.Marker m) {
        if (gui == null || m == null) {
            return null;
        }
        if (m == HOMESTEAD) {
            Coord hc = gui.homestead();
            return hc == null ? null : hc.div(MCache.tilesz);
        }
        FayteMapStore.Loc ml = loc(gui, m);
        FayteMapStore.Loc pl = playerloc(gui);
        Coord pt = playertile(gui);
        if (ml == null || pl == null || pt == null || ml.seg != pl.seg) {
            return null;
        }
        return pt.add(
                (int) Math.round((ml.x - pl.x) * MCache.cmaps.x), (int) Math.round((ml.y - pl.y) * MCache.cmaps.y));
    }

    public static WorldMapMarkers.Marker addloc(String kind, String name, FayteMapStore.Loc l) {
        if (l == null) {
            return null;
        }
        long[] a = FayteMapStore.current().anchor(l.seg, l.x, l.y);
        return a == null ? null : add(kind, name, a[0], (int) a[1], (int) a[2]);
    }

    public static WorldMapMarkers.Marker addat(GameUI gui, String kind, String name, Coord tile) {
        return addloc(kind, name, tileloc(gui, tile));
    }

    public static void autoadd(GameUI gui, String kind, String name, Coord tile) {
        if (!FayteModules.WORLDMAP.on() || gui == null || tile == null) {
            return;
        }
        FayteMapStore.Loc nl = tileloc(gui, tile);
        if (nl == null) {
            return;
        }
        for (WorldMapMarkers.Marker m : all()) {
            if (m.kind.equals(kind)) {
                FayteMapStore.Loc ml = loc(gui, m);
                if (ml != null
                        && ml.seg == nl.seg
                        && Math.hypot(ml.x - nl.x, ml.y - nl.y) * MCache.cmaps.x < DEDUPE_TILES) {
                    return;
                }
            }
        }
        WorldMapMarkers.Marker m = addloc(kind, name, nl);
        if (m != null) {
            FayteMsg.say("Added World Map marker: " + name);
        }
    }

    public static void autobell() {
        try {
            GameUI gui = UI.instance == null ? null : UI.instance.gui;
            autoadd(gui, "bell", "Town Bell", playertile(gui));
        } catch (Exception e) {
            FayteLog.once("WorldMapMarkers.autobell", e);
        }
    }

    public static void autoclaim(GameUI gui, Coord c1, Coord c2) {
        try {
            autoadd(gui, "claim", "Claim", c1.add(c2).div(2));
        } catch (Exception e) {
            FayteLog.once("WorldMapMarkers.autoclaim", e);
        }
    }

    public static void tick(GameUI gui) {
        FayteMapStore.tickobserve(gui);
        WorldMapMarkers.Marker p = pointed;
        Gob pl = player(gui);
        if (p == null || pl == null || !FayteModules.WORLDMAP.on()) {
            dropfx();
            return;
        }
        if (fxgob != pl) {
            dropfx();
            fx = new MarkerPointerFX(pl);
            fxgob = pl;
        }
        Coord t = curtile(gui, p);
        fx.target = t == null ? null : t.mul(MCache.tilesz).add(MCache.tilesz.div(2));
    }

    private static void dropfx() {
        if (fx != null) {
            fx.dispose();
            fx = null;
            fxgob = null;
        }
    }
}
