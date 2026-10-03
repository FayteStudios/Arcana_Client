package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteAchievePins extends Widget {
    private static final int W = FayteSkin.s(240);
    private static final int ROWH = FayteSkin.labelf.height() + FayteSkin.s(12);
    private static final Color BAR = new Color(0x6A, 0x9A, 0x5A);
    private static final Color GOLD = new Color(0xC8, 0xAA, 0x62);
    private static FayteAchievePins instance = null;
    private static List<String> pins = null;
    private static String pinsof = null;
    private final Map<String, Text> texts = new HashMap<>();
    private final Map<String, Tex> icons = new HashMap<>();

    private FayteAchievePins(Coord c, Widget parent) {
        super(c, new Coord(W, ROWH), parent);
    }

    private static String key(GameUI gui) {
        return "fayte_ach_pins_" + (gui.chrid == null ? "" : gui.chrid);
    }

    public static List<String> pins(GameUI gui) {
        String who = gui.chrid == null ? "" : gui.chrid;
        if (pins == null || !who.equals(pinsof)) {
            pinsof = who;
            pins = new ArrayList<>();
            for (String s : Utils.getpref(key(gui), "").split(",")) {
                if (!s.trim().isEmpty()) {
                    pins.add(s.trim());
                }
            }
        }
        return pins;
    }

    public static boolean pinned(GameUI gui, String id) {
        return pins(gui).contains(id);
    }

    public static void toggle(GameUI gui, String id) {
        List<String> l = pins(gui);
        if (!l.remove(id)) {
            l.add(id);
        }
        Utils.setpref(key(gui), String.join(",", l));
    }

    private static FayteAchieve.Line line(String id) {
        for (FayteAchieve.Line l : FayteAchieve.lines()) {
            if (l.id.equals(id)) {
                return l;
            }
        }
        return null;
    }

    public static void sync(GameUI gui) {
        boolean want = !pins(gui).isEmpty();
        if (want && (instance == null || instance.parent != gui)) {
            instance =
                    FayteLanding.add(gui, FayteHud.reg(new FayteAchievePins(FayteLanding.anchor(gui), gui), "achpins"));
        } else if (!want && instance != null) {
            if (instance.attached()) {
                instance.ui.destroy(instance);
            }
            instance = null;
        }
    }

    private Text text(String s, Color c) {
        String k = c.getRGB() + "|" + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 100) {
                texts.clear();
            }
            t = FayteSkin.labelf.render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private Tex icon(String name) {
        if (name == null) {
            return null;
        }
        Tex t = icons.get(name);
        if (t == null) {
            try {
                t = Resource.load(name).layer(Resource.imgc).tex();
                icons.put(name, t);
            } catch (Loading e) {
            } catch (RuntimeException e) {
                FayteLog.once("FayteAchievePins.icon", e);
            }
        }
        return t;
    }

    @Override
    public void draw(GOut g) {
        GameUI gui = getparent(GameUI.class);
        List<String> l = pins(gui);
        int h = Math.max(1, l.size()) * ROWH + 4;
        if (sz.y != h) {
            sz = new Coord(W, h);
        }
        FayteSkin.box(g, Coord.z, sz, FayteSkin.PANEL, FayteSkin.BORDER);
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.75);
        for (int i = 0; i < l.size(); i++) {
            FayteAchieve.Line a = line(l.get(i));
            if (a == null) {
                continue;
            }
            int y = 2 + i * ROWH;
            Tex ic = icon(a.icon);
            if (ic != null) {
                g.image(ic, new Coord(4, y + 3), new Coord(16, 16));
            }
            int next = a.next();
            boolean done = next < 0;
            int goal = done ? a.tiers[a.tiers.length - 1] : a.tiers[next];
            int v = Math.min(a.value, goal);
            g.image(text(a.name, done ? GOLD : FayteSkin.TEXT).tex(), new Coord(24, y));
            g.aimage(text(done ? "done" : v + " / " + goal, dim).tex(), new Coord(W - 6, y), 1.0, 0.0);
            Coord bc = new Coord(24, y + FayteSkin.labelf.height() + 3);
            Coord bs = new Coord(W - 30, 4);
            Color c = done ? GOLD : BAR;
            g.chcolor(FayteSkin.mix(FayteSkin.PANEL, c, 0.25));
            g.frect(bc, bs);
            g.chcolor(c);
            g.frect(bc, new Coord((int) ((long) bs.x * v / Math.max(1, goal)), bs.y));
            g.chcolor();
        }
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (button == 1 && !FayteHud.editing()) {
            FayteAlmanacWnd.achievements(getparent(GameUI.class));
            return true;
        }
        return false;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        int i = (c.y - 2) / ROWH;
        GameUI gui = getparent(GameUI.class);
        List<String> l = pins(gui);
        if (i >= 0 && i < l.size()) {
            FayteAchieve.Line a = line(l.get(i));
            if (a != null) {
                int next = a.next();
                return (next < 0 ? "Every tier earned" : "Next: " + a.goal(next))
                        + ". Click to open Achievements; use Pin on a card there to unpin.";
            }
        }
        return "Pinned achievements. Click to open Achievements.";
    }
}
