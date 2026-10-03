package haven;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.Color;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public class FayteHighlights {
    private static final Color OFF = new Color(0x40, 0x40, 0x48);
    private static final String[] ESSENCES = {
        null,
        "gfx/invobjs/essencegluttony",
        "gfx/invobjs/essencesloth",
        "gfx/invobjs/essenceenvy",
        "gfx/invobjs/essencegreed",
        "gfx/invobjs/essencelust",
        "gfx/invobjs/essencepride"
    };
    public static final String[] NAMES = {"Off", "Red", "Blue", "Green", "Yellow", "Purple", "White"};
    public static final Color[] COLORS = {
        null,
        new Color(0xE0, 0x50, 0x48),
        new Color(0x50, 0x8C, 0xF0),
        new Color(0x58, 0xC8, 0x60),
        new Color(0xF0, 0xD0, 0x48),
        new Color(0xB0, 0x60, 0xE0),
        new Color(0xF0, 0xEB, 0xDD)
    };
    private static Map<String, int[]> marks = null;

    public static synchronized void reload() {
        marks = null;
        version++;
        synchronized (rings) {
            rings.clear();
        }
    }

    public static File datafile() {
        return file();
    }

    public static int version = 0;
    private static final Map<String, List<String>> names = new HashMap<>();
    private static final Map<Gob, Object[]> rings = new WeakHashMap<>();
    private static final Set<Long> seen = new HashSet<>();
    private static final Map<Long, Object[]> alerted = new HashMap<>();
    private static long lastscan = 0;

    public static boolean on() {
        return FayteModules.TIMERS.on();
    }

    private static File file() {
        return new File(FaytePaths.fayte(), "highlights.json");
    }

    private static String key(String name) {
        return name == null ? null : name.trim().toLowerCase();
    }

    private static synchronized Map<String, int[]> marks() {
        if (marks == null) {
            marks = new TreeMap<>();
            File f = file();
            if (f.exists()) {
                try {
                    Map<String, int[]> m = new Gson()
                            .fromJson(
                                    new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8),
                                    new TypeToken<TreeMap<String, int[]>>() {}.getType());
                    if (m != null) {
                        marks.putAll(m);
                    }
                } catch (Exception e) {
                    FayteLog.log("Highlights: could not read " + f + ": " + e);
                }
            }
        }
        return marks;
    }

    private static synchronized void save() {
        version++;
        rings.clear();
        try {
            File f = file();
            f.getParentFile().mkdirs();
            FaytePaths.write(f, new Gson().toJson(marks()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.log("Highlights: could not save: " + e);
        }
    }

    public static synchronized int color(String name) {
        int[] m = marks().get(key(name));
        return m == null ? 0 : m[0];
    }

    public static synchronized boolean alert(String name) {
        int[] m = marks().get(key(name));
        return m != null && m[1] != 0;
    }

    public static synchronized List<Object[]> all() {
        List<Object[]> ret = new ArrayList<>();
        for (Map.Entry<String, int[]> e : marks().entrySet()) {
            String n = e.getKey();
            ret.add(new Object[] {
                n.isEmpty() ? n : Character.toUpperCase(n.charAt(0)) + n.substring(1),
                e.getValue()[0],
                e.getValue()[1] != 0
            });
        }
        return ret;
    }

    public static synchronized void remove(String name) {
        if (marks().remove(key(name)) != null) {
            save();
        }
    }

    public static synchronized void cycle(String name) {
        String k = key(name);
        if (k == null || k.isEmpty()) {
            return;
        }
        int[] m = marks().get(k);
        if (m == null) {
            m = new int[] {0, 0};
        }
        m[0] = (m[0] + 1) % COLORS.length;
        if (m[0] == 0 && m[1] == 0) {
            marks().remove(k);
        } else {
            marks().put(k, m);
        }
        save();
    }

    public static synchronized void togglealert(String name) {
        String k = key(name);
        if (k == null || k.isEmpty()) {
            return;
        }
        int[] m = marks().get(k);
        if (m == null) {
            m = new int[] {1, 0};
        }
        m[1] = m[1] == 0 ? 1 : 0;
        if (m[0] == 0 && m[1] == 0) {
            marks().remove(k);
        } else {
            marks().put(k, m);
        }
        save();
    }

    private static List<String> candidates(String rn) {
        List<String> c = names.get(rn);
        if (c == null) {
            c = new ArrayList<>();
            try {
                String d = FayteWorldNames.display(rn);
                if (d != null) {
                    c.add(key(d));
                }
                FayteWikiData.Entry e = FayteWorldNames.lookup(rn);
                if (e != null) {
                    c.add(key(e.title));
                }
            } catch (RuntimeException e) {
                FayteLog.once("FayteHighlights.candidates", e);
            }
            c.add(key(rn.substring(rn.lastIndexOf('/') + 1)));
            names.put(rn, c);
        }
        return c;
    }

    private static int[] match(Gob g) {
        String rn = FayteMsg.resname(g);
        if (rn == null) {
            return null;
        }
        Map<String, int[]> m = marks();
        if (m.isEmpty()) {
            return null;
        }
        for (String k : candidates(rn)) {
            int[] v = m.get(k);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    public static ColoredRadius ring(Gob g) {
        if (!on() || marks().isEmpty()) {
            return null;
        }
        Object[] r = rings.get(g);
        if (r != null && (Integer) r[0] == version) {
            return (ColoredRadius) r[1];
        }
        int[] v = match(g);
        ColoredRadius cr = null;
        if (v != null && v[0] > 0) {
            float rad;
            try {
                rad = FayteView.radius(g) + 3.0f;
            } catch (Loading e) {
                return null;
            }
            ColoredRadius.Cfg cfg = new ColoredRadius.Cfg();
            Color c = COLORS[v[0]];
            cfg.scol = String.format("60%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
            cfg.ecol = String.format("FF%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
            cfg.radius = rad;
            cr = new ColoredRadius(g, cfg, 10.0f);
        }
        rings.put(g, new Object[] {version, cr});
        return cr;
    }

    public static void tick(GameUI gui) {
        long now = System.currentTimeMillis();
        if (!on() || now - lastscan < 1000 || marks().isEmpty() || gui.ui.sess == null) {
            return;
        }
        lastscan = now;
        List<String> found = new ArrayList<>();
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob g : gui.ui.sess.glob.oc) {
                if (seen.contains(g.id)) {
                    continue;
                }
                int[] v = match(g);
                if (v != null && v[1] != 0) {
                    seen.add(g.id);
                    String rn = FayteMsg.resname(g);
                    found.add(rn == null ? "something" : FayteWorldNames.display(rn));
                    synchronized (alerted) {
                        alerted.put(g.id, new Object[] {now, COLORS[v[0] > 0 ? v[0] : 1]});
                    }
                }
            }
        }
        if (seen.size() > 20000) {
            seen.clear();
        }
        for (String f : found) {
            FayteMsg.say("Spotted: " + f, GameUI.MsgType.GOOD);
        }
    }

    public static void drawmini(GOut g, Coord c0, Glob glob, GameUI gui) {
        if (!on()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Object[]> draw = new ArrayList<>();
        synchronized (alerted) {
            alerted.values().removeIf(a -> now - (Long) a[0] > 30000L);
            for (Map.Entry<Long, Object[]> e : alerted.entrySet()) {
                draw.add(new Object[] {e.getKey(), e.getValue()[1]});
            }
        }
        if (draw.isEmpty()) {
            return;
        }
        Gob pl = null;
        if (gui != null && gui.map != null) {
            pl = gui.map.player();
        }
        double pulse = 0.5 + 0.5 * Math.sin(now / 150.0);
        for (Object[] d : draw) {
            Gob t = glob.oc.getgob((Long) d[0]);
            if (t == null || t.rc == null) {
                synchronized (alerted) {
                    alerted.remove((Long) d[0]);
                }
                continue;
            }
            Color c = (Color) d[1];
            Coord tp = c0.add(t.rc.div(MCache.tilesz));
            if (pl != null && pl.rc != null) {
                Coord pp = c0.add(pl.rc.div(MCache.tilesz));
                double dx = tp.x - pp.x, dy = tp.y - pp.y, len = Math.hypot(dx, dy);
                if (len > 12) {
                    double ux = dx / len, uy = dy / len;
                    Coord a = new Coord((int) (pp.x + ux * 8), (int) (pp.y + uy * 8));
                    Coord b = new Coord((int) (tp.x - ux * 8), (int) (tp.y - uy * 8));
                    g.chcolor(0, 0, 0, 200);
                    g.line(a, b, 4);
                    g.chcolor(c);
                    g.line(a, b, 2);
                    Coord h1 = new Coord((int) (b.x - ux * 8 - uy * 5), (int) (b.y - uy * 8 + ux * 5));
                    Coord h2 = new Coord((int) (b.x - ux * 8 + uy * 5), (int) (b.y - uy * 8 - ux * 5));
                    g.poly(b, h1, h2);
                }
            }
            int r = 6 + (int) (5 * pulse);
            g.chcolor(0, 0, 0, 200);
            g.rect(tp.sub(r + 1, r + 1), new Coord(r * 2 + 2, r * 2 + 2));
            g.chcolor(c.getRed(), c.getGreen(), c.getBlue(), 120 + (int) (135 * pulse));
            g.rect(tp.sub(r, r), new Coord(r * 2, r * 2));
            g.rect(tp.sub(r - 1, r - 1), new Coord(r * 2 - 2, r * 2 - 2));
        }
        g.chcolor();
    }

    public static FayteTitleButton[] buttons(Window w, Supplier<String> name) {
        FayteTitleButton[] b = new FayteTitleButton[2];
        b[0] = new FayteTitleButton(w, "Mark", "Footprint color for this on the ground (click to cycle)", () -> {
            cycle(name.get());
            relabel(b, name.get());
        });
        b[1] = new FayteTitleButton(w, "Alert", "Tell me when one comes into view", () -> {
                    togglealert(name.get());
                    relabel(b, name.get());
                })
                .iconly("paginae/bld/townidol");
        b[1].selcol = new Color(0xB0, 0x90, 0x20);
        b[0].swatch = OFF;
        b[0].iconly(null);
        return b;
    }

    public static void relabel(FayteTitleButton[] b, String name) {
        int c = color(name);
        b[0].swatch = c == 0 ? OFF : null;
        b[0].iconly(c == 0 ? null : ESSENCES[c]);
        b[0].setlabel(c == 0 ? "Mark" : "Mark: " + NAMES[c]);
        b[0].sel = c != 0;
        b[1].sel = alert(name);
        if (b[0].parent instanceof Window) {
            ((Window) b[0].parent).placetwdgs();
        }
    }
}
