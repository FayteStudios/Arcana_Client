package haven;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Color;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

public class FayteSelections {
    public static final int BIT0 = 20;
    public static final int PREVIEW = 28;
    public static final int DIG = 29;
    public static final int FILL = 30;
    public static final int LEVEL = 31;
    public static final int DIG0 = 5;
    public static final int FILL0 = 10;
    public static final int STEPS = 5;
    public static final String[] TARGETS = {"Lowest", "Mean", "Highest"};
    private static long lastheight = 0;
    private static int heightsig = 0;
    public static final String[] NAMES = {"Blue", "Green", "Red", "Yellow", "Purple", "Cyan", "Orange", "White"};
    public static final Color[] COLORS = {
        new Color(0x50, 0x8C, 0xF0), new Color(0x58, 0xC8, 0x60), new Color(0xE0, 0x50, 0x48),
                new Color(0xF0, 0xD0, 0x48),
        new Color(0xB0, 0x60, 0xE0), new Color(0x48, 0xD0, 0xD0), new Color(0xF0, 0x90, 0x40),
                new Color(0xF0, 0xEB, 0xDD)
    };
    private static Store store = null;
    private static String loadedfor = null;
    private static final List<MCache.Overlay> live = new ArrayList<>();
    private static MCache.Overlay preview = null;
    private static boolean dirty = true;
    private static boolean enabledneed = false;
    private static int gridsig = 0;
    private static MapView enabledon = null;
    private static Grab grab = null;
    public static Sel editing = null;

    public static class Sel {
        public long id;
        public String name;
        public int color;
        public boolean on = true;
        public boolean height = false;
        public int target = 0;
        public Map<String, List<Integer>> tiles = new TreeMap<>();
        public transient int dig, fill, level, tz, step = 1, zmin, zmax;
        public transient boolean known;

        public int count() {
            int n = 0;
            for (List<Integer> l : tiles.values()) {
                n += l.size();
            }
            return n;
        }
    }

    public static class Store {
        public long nextid = 1;
        public List<Sel> sets = new ArrayList<>();
    }

    public static boolean on() {
        return FayteModules.SELECTIONS.on();
    }

    private static File file() {
        return new File(
                new File(FaytePaths.fayte(), "selections"),
                FaytePaths.safename(String.valueOf(Config.server)) + ".json");
    }

    public static synchronized Store get() {
        String k = String.valueOf(Config.server);
        if (store == null || !k.equals(loadedfor)) {
            loadedfor = k;
            store = null;
            File f = file();
            if (f.exists()) {
                try {
                    store = new Gson()
                            .fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), Store.class);
                } catch (Exception e) {
                    FayteLog.log("Selections: could not read " + f + ": " + e);
                }
            }
            if (store == null) {
                store = new Store();
            }
            dirty = true;
        }
        return store;
    }

    public static synchronized void save() {
        dirty = true;
        try {
            File f = file();
            f.getParentFile().mkdirs();
            FaytePaths.write(f, new GsonBuilder().create().toJson(get()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.log("Selections: could not save: " + e);
        }
    }

    public static synchronized List<Sel> all() {
        return new ArrayList<>(get().sets);
    }

    public static synchronized Sel add(String name) {
        Sel s = new Sel();
        s.id = get().nextid++;
        s.name = name;
        s.color = (int) ((s.id - 1) % COLORS.length);
        get().sets.add(s);
        save();
        return s;
    }

    public static synchronized void remove(Sel s) {
        if (editing == s) {
            stopedit();
        }
        get().sets.remove(s);
        save();
    }

    public static synchronized void toggle(Sel s) {
        s.on = !s.on;
        save();
    }

    public static synchronized void toggleheight(Sel s) {
        s.height = !s.height;
        if (s.height) {
            s.on = true;
        }
        save();
    }

    public static synchronized void cycletarget(Sel s) {
        s.target = (s.target + 1) % TARGETS.length;
        save();
    }

    public static synchronized void cycle(Sel s) {
        s.color = (s.color + 1) % COLORS.length;
        save();
    }

    private static Map<Long, Coord> grids(MCache map) {
        return FayteMapStore.loaded(map);
    }

    private static long gridof(MCache map, Coord tile) {
        Coord gc = tile.div(MCache.cmaps);
        synchronized (map.grids) {
            MCache.Grid g = map.grids.get(gc);
            return g == null ? 0 : g.id;
        }
    }

    private static synchronized void apply(MCache map, Coord a, Coord b, boolean add) {
        Sel s = editing;
        if (s == null) {
            return;
        }
        int x0 = Math.min(a.x, b.x), x1 = Math.max(a.x, b.x);
        int y0 = Math.min(a.y, b.y), y1 = Math.max(a.y, b.y);
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) > 40000) {
            FayteMsg.say("That area is too big to select at once", GameUI.MsgType.BAD);
            return;
        }
        Map<String, TreeSet<Integer>> sets = new HashMap<>();
        for (Map.Entry<String, List<Integer>> e : s.tiles.entrySet()) {
            sets.put(e.getKey(), new TreeSet<>(e.getValue()));
        }
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                Coord t = new Coord(x, y);
                long gid = gridof(map, t);
                if (gid == 0) {
                    continue;
                }
                Coord l = t.sub(t.div(MCache.cmaps).mul(MCache.cmaps));
                int packed = l.x + l.y * MCache.cmaps.x;
                String k = Long.toString(gid);
                TreeSet<Integer> ts = sets.get(k);
                if (add) {
                    if (ts == null) {
                        ts = new TreeSet<>();
                        sets.put(k, ts);
                    }
                    ts.add(packed);
                } else if (ts != null) {
                    ts.remove(packed);
                }
            }
        }
        s.tiles.clear();
        for (Map.Entry<String, TreeSet<Integer>> e : sets.entrySet()) {
            if (!e.getValue().isEmpty()) {
                s.tiles.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
        }
        save();
    }

    private static void setpreview(MCache map, Coord a, Coord b) {
        if (preview != null) {
            preview.destroy();
            preview = null;
        }
        if (a != null && b != null) {
            preview = map
            .new Overlay(
                    new Coord(Math.min(a.x, b.x), Math.min(a.y, b.y)),
                    new Coord(Math.max(a.x, b.x), Math.max(a.y, b.y)),
                    1 << PREVIEW);
        }
    }

    private static List<Coord> abstiles(Sel s, Map<Long, Coord> g) {
        List<Coord> ret = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : s.tiles.entrySet()) {
            Coord gc;
            try {
                gc = g.get(Long.parseLong(e.getKey()));
            } catch (NumberFormatException ex) {
                gc = null;
            }
            if (gc != null) {
                Coord base = gc.mul(MCache.cmaps);
                for (int p : e.getValue()) {
                    ret.add(base.add(p % MCache.cmaps.x, p / MCache.cmaps.x));
                }
            }
        }
        return ret;
    }

    private static int[] corners(MCache map, Coord t) {
        return new int[] {map.getz(t), map.getz(t.add(1, 0)), map.getz(t.add(0, 1)), map.getz(t.add(1, 1))};
    }

    private static Map<Coord, Integer> classify(MCache map, Sel s, Map<Long, Coord> g) {
        Map<Coord, Integer> cls = new LinkedHashMap<>();
        List<Coord> ts = abstiles(s, g);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        long sum = 0;
        int n = 0;
        Map<Coord, int[]> hs = new HashMap<>();
        for (Coord t : ts) {
            try {
                int[] c = corners(map, t);
                hs.put(t, c);
                for (int z : c) {
                    min = Math.min(min, z);
                    max = Math.max(max, z);
                    sum += z;
                    n++;
                }
            } catch (RuntimeException e) {
                FayteLog.once("FayteSelections.classify", e);
            }
        }
        s.dig = s.fill = s.level = 0;
        s.known = n > 0;
        if (n == 0) {
            return cls;
        }
        int tz = s.target == 0 ? min : s.target == 2 ? max : (int) Math.round((double) sum / n);
        s.tz = tz;
        s.zmin = min;
        s.zmax = max;
        int span = Math.max(max - tz, tz - min);
        int step = Math.max(1, (span + STEPS - 1) / STEPS);
        s.step = step;
        for (Map.Entry<Coord, int[]> e : hs.entrySet()) {
            int hi = Integer.MIN_VALUE, lo = Integer.MAX_VALUE;
            for (int z : e.getValue()) {
                hi = Math.max(hi, z);
                lo = Math.min(lo, z);
            }
            int c;
            if (hi > tz) {
                c = DIG0 + Math.min(STEPS - 1, (hi - tz - 1) / step);
                s.dig++;
            } else if (lo < tz) {
                c = FILL0 + Math.min(STEPS - 1, (tz - lo - 1) / step);
                s.fill++;
            } else {
                c = LEVEL;
                s.level++;
            }
            cls.put(e.getKey(), c);
        }
        return cls;
    }

    private static void runs(MCache map, Map<Coord, Integer> tiles, IntUnaryOperator maskof) {
        Map<Long, TreeSet<Integer>> rows = new TreeMap<>();
        for (Map.Entry<Coord, Integer> e : tiles.entrySet()) {
            long key = ((long) e.getValue() << 40) ^ (e.getKey().y + (1 << 20));
            rows.computeIfAbsent(key, k -> new TreeSet<>()).add(e.getKey().x);
        }
        for (Map.Entry<Long, TreeSet<Integer>> r : rows.entrySet()) {
            int cls = (int) (r.getKey() >> 40);
            int y = (int) ((r.getKey() & ((1L << 40) - 1)) - (1 << 20));
            int mask = maskof.applyAsInt(cls);
            int start = Integer.MIN_VALUE, prev = Integer.MIN_VALUE;
            for (int x : r.getValue()) {
                if (start == Integer.MIN_VALUE || x != prev + 1) {
                    if (start != Integer.MIN_VALUE) {
                        live.add(map.new Overlay(new Coord(start, y), new Coord(prev, y), mask));
                    }
                    start = x;
                }
                prev = x;
            }
            if (start != Integer.MIN_VALUE) {
                live.add(map.new Overlay(new Coord(start, y), new Coord(prev, y), mask));
            }
        }
    }

    private static int heightsig(MCache map) {
        Map<Long, Coord> g = grids(map);
        int h = 1;
        for (Sel s : all()) {
            if (s.on && s.height) {
                h = 31 * h + classify(map, s, g).hashCode();
            }
        }
        return h;
    }

    private static void rebuild(MCache map) {
        for (MCache.Overlay o : live) {
            o.destroy();
        }
        live.clear();
        Map<Long, Coord> g = grids(map);
        for (Sel s : all()) {
            if (!s.on) {
                continue;
            }
            if (s.height) {
                runs(map, classify(map, s, g), c -> 1 << MapView.WFOL);
                continue;
            }
            int mask = 1 << (BIT0 + s.color);
            for (Map.Entry<String, List<Integer>> e : s.tiles.entrySet()) {
                Coord gc;
                try {
                    gc = g.get(Long.parseLong(e.getKey()));
                } catch (NumberFormatException ex) {
                    gc = null;
                }
                if (gc == null) {
                    continue;
                }
                Map<Integer, TreeSet<Integer>> rows = new LinkedHashMap<>();
                for (int p : e.getValue()) {
                    int x = p % MCache.cmaps.x;
                    int y = p / MCache.cmaps.x;
                    rows.computeIfAbsent(y, k -> new TreeSet<>()).add(x);
                }
                Coord base = gc.mul(MCache.cmaps);
                for (Map.Entry<Integer, TreeSet<Integer>> r : rows.entrySet()) {
                    int start = -2, prev = -2;
                    for (int x : r.getValue()) {
                        if (x != prev + 1) {
                            if (start >= 0) {
                                live.add(
                                        map.new Overlay(base.add(start, r.getKey()), base.add(prev, r.getKey()), mask));
                            }
                            start = x;
                        }
                        prev = x;
                    }
                    if (start >= 0) {
                        live.add(map.new Overlay(base.add(start, r.getKey()), base.add(prev, r.getKey()), mask));
                    }
                }
            }
        }
    }

    public static void tick(GameUI gui) {
        if (gui.map == null || gui.ui.sess == null) {
            return;
        }
        MCache map = gui.ui.sess.glob.map;
        boolean need = editing != null;
        for (Sel sl : all()) {
            need |= sl.on;
        }
        if (enabledon != gui.map || need != enabledneed) {
            int[] bits = new int[COLORS.length + 3];
            for (int i = 0; i < COLORS.length; i++) {
                bits[i] = BIT0 + i;
            }
            bits[COLORS.length] = PREVIEW;
            bits[COLORS.length + 1] = LEVEL;
            bits[COLORS.length + 2] = MapView.WFOL;
            if (enabledon != null && enabledneed) {
                enabledon.disol(bits);
            }
            enabledon = gui.map;
            enabledneed = need;
            if (need) {
                gui.map.enol(bits);
            }
            dirty = true;
        }
        if (!need && !dirty) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastheight > 1000) {
            lastheight = now;
            int hs = heightsig(map);
            if (hs != heightsig) {
                heightsig = hs;
                dirty = true;
            }
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (Sel s : all()) {
                if (s.on && s.height && s.known) {
                    lo = Math.min(lo, s.zmin);
                    hi = Math.max(hi, s.zmax);
                }
            }
            if (lo != Integer.MAX_VALUE && !FlatnessTool.open()) {
                FlatnessTool.minheight = lo;
                FlatnessTool.maxheight = hi > lo ? hi : lo + 1;
            }
        }
        int sig = grids(map).keySet().hashCode();
        if (dirty || sig != gridsig) {
            dirty = false;
            gridsig = sig;
            rebuild(map);
        }
    }

    public static void startedit(GameUI gui, Sel s) {
        stopedit();
        if (gui == null || gui.map == null) {
            return;
        }
        editing = s;
        s.on = true;
        save();
        grab = new Grab(gui.map);
        gui.map.grab(grab);
        FayteMsg.say("Editing " + s.name
                + ": left-click or drag to add tiles, right-click or drag to remove. Click Done when finished.");
    }

    public static void stopedit() {
        if (grab != null) {
            grab.mv.release(grab);
            setpreview(grab.mv.ui.sess.glob.map, null, null);
            grab = null;
        }
        editing = null;
    }

    private static class Grab implements MapView.Grabber {
        final MapView mv;
        int button = 0;
        Coord start = null;
        Coord cur = null;

        Grab(MapView mv) {
            this.mv = mv;
        }

        private void at(Coord sc, Consumer<Coord> k) {
            mv.delay(mv.new Hittest(sc) {
                public void hit(Coord pc, Coord mc, MapView.ClickInfo inf) {
                    if (mc != null) {
                        k.accept(mc.div(MCache.tilesz));
                    }
                }
            });
        }

        public boolean mmousedown(Coord sc, int b) {
            if (b != 1 && b != 3) {
                return false;
            }
            button = b;
            at(sc, t -> {
                start = t;
                cur = t;
                setpreview(mv.ui.sess.glob.map, t, t);
            });
            return true;
        }

        public void mmousemove(Coord sc) {
            if (button == 0 || start == null) {
                return;
            }
            at(sc, t -> {
                if (button != 0 && start != null && !t.equals(cur)) {
                    cur = t;
                    setpreview(mv.ui.sess.glob.map, start, t);
                }
            });
        }

        public boolean mmouseup(Coord sc, int b) {
            if (b != button) {
                return false;
            }
            final boolean add = b == 1;
            at(sc, t -> {
                if (start != null) {
                    apply(mv.ui.sess.glob.map, start, t, add);
                }
                start = null;
                cur = null;
                setpreview(mv.ui.sess.glob.map, null, null);
            });
            button = 0;
            return true;
        }

        public boolean mmousewheel(Coord sc, int amount) {
            return false;
        }
    }
}
