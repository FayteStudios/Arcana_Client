package haven;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteSelList extends Widget {
    private static final int ROWH = FayteSkin.s(22);
    private static final int W = FayteSkin.s(250);
    private static final String PREF = "fayte_sel_pop";
    private static FayteSelList instance = null;
    private final Map<String, Text> texts = new HashMap<>();

    private FayteSelList(Coord c, Widget parent) {
        super(c, new Coord(W, ROWH), parent);
    }

    private static Boolean popcache = null;

    public static boolean popped() {
        if (popcache == null) {
            popcache = Utils.getprefb(PREF, false);
        }
        return popcache;
    }

    public static void setpopped(boolean p) {
        popcache = p;
        Utils.setprefb(PREF, p);
    }

    public static void sync(GameUI gui) {
        boolean want = popped() && !FayteSelections.all().isEmpty();
        if (want && (instance == null || instance.parent != gui || !instance.attached())) {
            instance = FayteLanding.add(gui, FayteHud.reg(new FayteSelList(FayteLanding.anchor(gui), gui), "sellist"));
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
            if (texts.size() > 200) {
                texts.clear();
            }
            t = FayteSkin.labelf.render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private void button(GOut g, int x, int y, String label, boolean on) {
        FayteSkin.box(
                g,
                new Coord(x, y + 3),
                new Coord(24, ROWH - 6),
                on ? FayteSkin.BORDER : FayteSkin.PANEL,
                FayteSkin.BORDER);
        g.aimage(
                text(label, on ? FayteSkin.TEXT : FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.5))
                        .tex(),
                new Coord(x + 12, y + ROWH / 2),
                0.5,
                0.5);
    }

    @Override
    public void draw(GOut g) {
        List<FayteSelections.Sel> ss = FayteSelections.all();
        int h = Math.max(1, ss.size()) * ROWH + 4;
        if (sz.y != h) {
            sz = new Coord(W, h);
        }
        FayteSkin.box(g, Coord.z, sz, FayteSkin.PANEL, FayteSkin.BORDER);
        for (int i = 0; i < ss.size(); i++) {
            FayteSelections.Sel s = ss.get(i);
            int y = 2 + i * ROWH;
            g.chcolor(FayteSelections.COLORS[s.color]);
            g.frect(new Coord(6, y + 6), new Coord(10, ROWH - 12));
            g.chcolor();
            g.aimage(
                    text(s.name, s.on ? FayteSkin.TEXT : FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.5))
                            .tex(),
                    new Coord(22, y + ROWH / 2),
                    0.0,
                    0.5);
            if (s.on && s.height && s.known) {
                String st = "\u2193" + s.dig + " \u2191" + s.fill + " \u2713" + s.level;
                g.aimage(
                        text(st, FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.8))
                                .tex(),
                        new Coord(W - 64, y + ROWH / 2),
                        1.0,
                        0.5);
            }
            button(g, W - 58, y, "On", s.on);
            button(g, W - 30, y, "H", s.height);
        }
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (button != 1) {
            return false;
        }
        List<FayteSelections.Sel> ss = FayteSelections.all();
        int i = (c.y - 2) / ROWH;
        if (i >= 0 && i < ss.size()) {
            FayteSelections.Sel s = ss.get(i);
            if (c.x >= W - 58 && c.x < W - 32) {
                FayteSelections.toggle(s);
            } else if (c.x >= W - 30) {
                FayteSelections.toggleheight(s);
            } else if (s.height && c.x >= W - 150) {
                FayteSelections.cycletarget(s);
            } else {
                FayteSelectionsWnd.toggle(getparent(GameUI.class));
            }
        }
        return true;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        List<FayteSelections.Sel> ss = FayteSelections.all();
        int i = (c.y - 2) / ROWH;
        if (i < 0 || i >= ss.size()) {
            return null;
        }
        FayteSelections.Sel s = ss.get(i);
        if (c.x >= W - 58 && c.x < W - 32) {
            return "Show or hide this selection";
        } else if (c.x >= W - 30) {
            return "Height view: the game's height grid over this selection (red high, blue low). Counts are against"
                    + " the target.";
        } else if (s.height) {
            return "Target: " + FayteSelections.TARGETS[s.target] + " (height " + s.tz + "). Click to change.";
        }
        return "Click to open Selections";
    }
}
