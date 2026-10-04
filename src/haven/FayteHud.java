package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class FayteHud {
    public static final int BARH = 14;
    private static final Map<Widget, String> keys = new WeakHashMap<>();
    private static final Map<Widget, Coord> defaults = new WeakHashMap<>();
    private static final Map<String, int[]> layout = new HashMap<>();
    private static final Map<String, Coord> sizes = new HashMap<>();
    private static final Map<String, String> names = new LinkedHashMap<>();
    private static final Map<String, int[]> ghostdef = new HashMap<>();
    private static final Map<String, Text> labels = new HashMap<>();
    private static FayteHud.Target drag = null;
    private static Coord doff = null;
    private static final Color EDGE = new Color(0x3A, 0x41, 0x4C, 220);
    private static final Color HOT = new Color(0xF0, 0xEB, 0xDD, 230);
    private static final Color GHOST = new Color(0x15, 0x18, 0x1D, 150);

    static {
        names.put("season", "Season");
        names.put("buffs", "Effects");
        names.put("equip", "Tools");
        names.put("tempers", "Humours");
        names.put("cravings", "Cravings");
        names.put("fight", "Combat");
        names.put("party", "Party");
        names.put("stance", "Stance");
        names.put("wiki", "Wiki");
        names.put("store", "Store");
        names.put("attributes", "Attributes");
        names.put("actions", "Actions");
        names.put("buttons", "Buttons");
        names.put("sellist", "Selections");
        names.put("skillgoal", "Skill goal");
        names.put("profpins", "Pinned proficiencies");
        names.put("achpins", "Pinned achievements");
        names.put("timerstart", "Pop-outs start here");
        names.put("achtoast", "Achievement pop-up");
        names.put("shoplist", "Shopping list");
        names.put("timers", "Timers");
        names.put("mainbar", "Main bar");
        for (int i = 1; i <= 6; i++) {
            names.put("bar" + i, "Action bar " + i);
        }
        ghostdef.put("fight", new int[] {'R', 'L', 0, 0, 170, 120});
        ghostdef.put("buffs", new int[] {'L', 'L', 80, 60, 240, 40});
        ghostdef.put("party", new int[] {'L', 'L', 2, 80, 110, 60});
        ghostdef.put("timerstart", new int[] {'L', 'C', 10, 0, 200, 80});
        ghostdef.put("achtoast", new int[] {'C', 'L', 0, 110, FayteAchieveToast.SZ.x, FayteAchieveToast.SZ.y});
    }

    public static class Target {
        public final String key;
        public final Widget w;
        public Coord c;
        public Coord sz;

        Target(String key, Widget w, Coord c, Coord sz) {
            this.key = key;
            this.w = w;
            this.c = c;
            this.sz = sz;
        }

        Coord barc() {
            return c.y >= BARH ? c.sub(0, BARH) : c;
        }

        Coord barsz() {
            return new Coord(Math.max(sz.x, label(key).sz().x + 10), BARH);
        }
    }

    public static boolean on() {
        return FayteSkin.on();
    }

    public static boolean locked(UI ui) {
        return FayteModules.STYLE.on() && FayteConfig.lockUi.get();
    }

    public static boolean editing() {
        return on() && !FayteConfig.lockUi.get();
    }

    private static String pref(String key) {
        String p = FayteLayout.profile();
        if (!p.isEmpty() && !Utils.getpref("fayte_hud_" + p + key, "").isEmpty()) {
            return "fayte_hud_" + p + key;
        }
        return p.isEmpty() ? "fayte_hud_" + key : "fayte_hud_" + p + key;
    }

    public static String name(String key) {
        String n = names.get(key);
        if (n != null) {
            return n;
        } else if (key.startsWith("misc_")) {
            String[] p = key.split("_");
            String l = p[p.length - 1];
            return l.isEmpty() ? key : Character.toUpperCase(l.charAt(0)) + l.substring(1);
        } else {
            return key;
        }
    }

    private static Text label(String key) {
        Text t = labels.get(key);
        if (t == null) {
            t = FayteSkin.labelf.render(name(key), FayteSkin.TEXT);
            labels.put(key, t);
        }
        return t;
    }

    public static <T extends Widget> T reg(T w, String key) {
        if (w != null && key != null) {
            keys.put(w, key);
            defaults.put(w, w.c);
            apply(w);
        }
        return w;
    }

    public static boolean is(Widget w) {
        return keys.containsKey(w);
    }

    private static boolean own(Widget w) {
        return !(w instanceof MenuGrid) && !(w instanceof GameUI.MainMenu);
    }

    public static void resetcache() {
        layout.clear();
        sizes.clear();
    }

    public static Coord ghostpos(String key, Coord psz) {
        int[] gd = ghostdef.get(key);
        if (gd == null) {
            return Coord.z;
        }
        int[] v = saved(key);
        return at(v != null ? v : gd, psz, new Coord(gd[4], gd[5]));
    }

    private static int[] saved(String key) {
        if (!layout.containsKey(key)) {
            int[] v = null;
            String[] p = Utils.getpref(pref(key), "").trim().split(" ");
            if (p.length == 4) {
                try {
                    v = new int[] {p[0].charAt(0), p[1].charAt(0), Integer.parseInt(p[2]), Integer.parseInt(p[3])};
                } catch (RuntimeException e) {
                    v = null;
                }
            }
            layout.put(key, v);
        }
        return layout.get(key);
    }

    private static Coord size(String key) {
        if (!sizes.containsKey(key)) {
            Coord s = null;
            String[] p = Utils.getpref(pref(key) + "_sz", "").trim().split(" ");
            if (p.length == 2) {
                try {
                    s = new Coord(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
                } catch (RuntimeException e) {
                    s = null;
                }
            }
            sizes.put(key, s);
        }
        return sizes.get(key);
    }

    private static void seen(String key, Coord sz) {
        if (sz.x > 0 && sz.y > 0 && !sz.equals(size(key))) {
            sizes.put(key, sz);
            Utils.setpref(pref(key) + "_sz", sz.x + " " + sz.y);
        }
    }

    private static int place(char a, int d, int psz, int sz) {
        if (a == 'R') {
            return psz - sz - d;
        } else if (a == 'C') {
            return psz / 2 + d - sz / 2;
        } else {
            return d;
        }
    }

    public static Coord at(int[] v, Coord psz, Coord sz) {
        int x = place((char) v[0], v[2], psz.x, sz.x);
        int y = place((char) v[1], v[3], psz.y, sz.y);
        return new Coord(Math.max(0, Math.min(x, psz.x - sz.x)), Math.max(0, Math.min(y, psz.y - sz.y)));
    }

    public static void apply(Widget w) {
        if (!on() || (drag != null && drag.w == w) || !w.attached() || !own(w)) {
            return;
        }
        String k = keys.get(w);
        int[] v = k == null ? null : saved(k);
        if (v != null) {
            Coord nc = at(v, w.parent.sz, w.sz);
            if (!nc.equals(w.c)) {
                w.c = nc;
            }
        }
    }

    public static void applyall(Widget parent) {
        for (Widget w = parent.child; w != null; w = w.next) {
            String k = keys.get(w);
            if (k != null) {
                apply(w);
                if (on() && w.visible) {
                    seen(k, w.sz);
                }
            }
        }
    }

    private static char third(int mid, int psz) {
        return mid < psz / 3 ? 'L' : (mid > psz * 2 / 3 ? 'R' : 'C');
    }

    private static int offset(char a, int pos, int sz, int psz) {
        if (a == 'R') {
            return psz - pos - sz;
        } else if (a == 'C') {
            return pos + sz / 2 - psz / 2;
        } else {
            return pos;
        }
    }

    public static int[] anchor(Coord c, Coord sz, Coord psz) {
        char ax = third(c.x + sz.x / 2, psz.x);
        char ay = third(c.y + sz.y / 2, psz.y);
        return new int[] {ax, ay, offset(ax, c.x, sz.x, psz.x), offset(ay, c.y, sz.y, psz.y)};
    }

    private static void save(Widget parent, FayteHud.Target t) {
        if (t.w instanceof MenuGrid) {
            FayteConfig.actionGridPos.set(t.w.c);
        } else if (t.w instanceof GameUI.MainMenu) {
            FayteConfig.buttonPanelPos.set(t.w.c);
        } else {
            int[] v = anchor(t.c, t.sz, parent.sz);
            layout.put(t.key, v);
            Utils.setpref(pref(t.key), (char) v[0] + " " + (char) v[1] + " " + v[2] + " " + v[3]);
        }
    }

    private static void reset(GameUI gui, FayteHud.Target t) {
        if (t.w != null && !own(t.w)) {
            return;
        }
        layout.put(t.key, null);
        Utils.setpref(pref(t.key), "");

        for (Map.Entry<Widget, String> e : new ArrayList<>(keys.entrySet())) {
            if (e.getValue().equals(t.key) && e.getKey() != null) {
                Coord d = defaults.get(e.getKey());
                if (d != null) {
                    e.getKey().c = d;
                }
                e.getKey().presize();
            }
        }
        gui.resize(gui.sz);
    }

    private static List<FayteHud.Target> targets(Widget parent) {
        List<FayteHud.Target> ret = new ArrayList<>();
        Set<String> live = new HashSet<>();

        for (Widget w = parent.child; w != null; w = w.next) {
            String k = keys.get(w);
            if (k != null && w.visible && w.sz.x > 0 && w.sz.y > 0) {
                ret.add(new FayteHud.Target(k, w, w.c, w.sz));
                live.add(k);
            }
        }
        Set<String> all = new HashSet<>(ghostdef.keySet());

        for (String k : all) {
            if (live.contains(k) || (drag != null && drag.w == null && drag.key.equals(k))) {
                continue;
            }
            int[] gd = ghostdef.get(k);
            Coord sz = size(k);
            if (sz == null && gd != null) {
                sz = new Coord(gd[4], gd[5]);
            }
            int[] v = saved(k);
            if (v == null && gd != null) {
                v = gd;
            }
            if (sz != null && v != null) {
                ret.add(0, new FayteHud.Target(k, null, at(v, parent.sz, sz), sz));
            }
        }
        if (drag != null && drag.w == null) {
            ret.add(drag);
        }
        return ret;
    }

    private static FayteHud.Target hit(Widget parent, Coord c) {
        List<FayteHud.Target> ts = targets(parent);

        for (int i = ts.size() - 1; i >= 0; i--) {
            FayteHud.Target t = ts.get(i);
            if (c.isect(t.barc(), t.barsz())) {
                return t;
            }
        }
        return null;
    }

    public static boolean mousedown(GameUI gui, Coord c, int button) {
        if (!editing() || drag != null) {
            return false;
        }
        FayteHud.Target t = hit(gui, c);
        if (t == null) {
            return false;
        }
        if (button == 1) {
            drag = t;
            doff = c.sub(t.c);
            if (t.w != null) {
                t.w.raise();
            }
            gui.ui.grabmouse(gui);
            return true;
        } else if (button == 3) {
            reset(gui, t);
            return true;
        }
        return false;
    }

    public static boolean dragging() {
        return drag != null;
    }

    public static void mousemove(GameUI gui, Coord c) {
        FayteHud.Target t = drag;
        if (t == null) {
            return;
        }
        Coord psz = gui.sz;
        Coord want = c.sub(doff);
        want = new Coord(Math.max(0, Math.min(want.x, psz.x - t.sz.x)), Math.max(0, Math.min(want.y, psz.y - t.sz.y)));
        t.c = WindowSnap.snap(gui, t.w, t.sz, want);
        if (t.w != null) {
            t.w.c = t.c;
            if (!own(t.w)) {
                gui.menumoved();
            }
        }
    }

    public static boolean mouseup(GameUI gui, Coord c, int button) {
        FayteHud.Target t = drag;
        if (t == null) {
            return false;
        }
        if (button == 1) {
            drag = null;
            gui.ui.grabmouse(null);
            if (t.w == null || t.w.attached()) {
                save(gui, t);
                if (t.w != null && !own(t.w)) {
                    gui.menumoved();
                }
            }
        }
        return true;
    }

    public static void draw(GameUI gui, GOut g) {
        if (!editing() && drag == null) {
            return;
        }
        FayteHud.Target hot = drag != null ? drag : hit(gui, gui.ui.mc.sub(gui.rootpos()));

        for (FayteHud.Target t : targets(gui)) {
            boolean h = hot != null && hot.key.equals(t.key) && hot.w == t.w;
            if (t.w == null) {
                g.chcolor(GHOST);
                g.frect(t.c, t.sz);
            }
            g.chcolor(h ? HOT : EDGE);
            g.rect(t.c, t.sz.add(1, 1));
            Coord bc = t.barc();
            Coord bs = t.barsz();
            FayteSkin.box(g, bc, bs, h ? FayteSkin.BORDER : FayteSkin.PANEL, h ? FayteSkin.TEXT : FayteSkin.BORDER);
            g.chcolor();
            g.aimage(label(t.key).tex(), bc.add(5, BARH / 2), 0.0, 0.5);
        }
        g.chcolor();
    }
}
