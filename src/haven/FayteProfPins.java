package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteProfPins extends Widget {
    private static final int W = FayteSkin.s(210);
    private static final int ROWH = FayteSkin.labelf.height() + FayteSkin.s(10);
    private static final Color READY = new Color(0x58, 0xC8, 0x60);
    private static final Color EXPC = new Color(0x50, 0x8C, 0xF0);
    private static FayteProfPins instance = null;
    private static List<String> pins = null;
    private static String pinsof = null;
    private final Map<String, Text> texts = new HashMap<>();
    private final Map<String, Tex> icons = new HashMap<>();

    private FayteProfPins(Coord c, Widget parent) {
        super(c, new Coord(W, ROWH), parent);
    }

    private static String key(GameUI gui) {
        return "fayte_prof_pins_" + (gui.chrid == null ? "" : gui.chrid);
    }

    public static List<String> pins(GameUI gui) {
        String who = gui.chrid == null ? "" : gui.chrid;
        if (pins == null || !who.equals(pinsof)) {
            pinsof = who;
            pins = new ArrayList<>();
            for (String s : Utils.getpref(key(gui), "").split(",")) {
                if (!s.trim().isEmpty() && CharWnd.attrnm.containsKey(s.trim())) {
                    pins.add(s.trim());
                }
            }
        }
        return pins;
    }

    public static boolean pinned(GameUI gui, String nm) {
        return pins(gui).contains(nm);
    }

    public static void toggle(GameUI gui, String nm) {
        List<String> l = pins(gui);
        if (l.contains(nm)) {
            l.remove(nm);
        } else {
            l.add(nm);
            l.sort((a, b) -> CharWnd.attrorder.indexOf(a) - CharWnd.attrorder.indexOf(b));
        }
        Utils.setpref(key(gui), String.join(",", l));
    }

    public static void sync(GameUI gui) {
        boolean want = !pins(gui).isEmpty() && gui.chrwdg != null;
        if (want && (instance == null || instance.parent != gui)) {
            instance =
                    FayteLanding.add(gui, FayteHud.reg(new FayteProfPins(FayteLanding.anchor(gui), gui), "profpins"));
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

    private Tex icon(String nm) {
        Tex t = icons.get(nm);
        if (t == null) {
            try {
                Resource.Image img = Resource.load("gfx/hud/skills/" + nm).layer(Resource.imgc);
                if (img != null) {
                    t = img.tex();
                    icons.put(nm, t);
                }
            } catch (Loading e) {
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
            String nm = l.get(i);
            CharWnd.Attr a = FayteSkills.attr(gui, nm);
            int y = 2 + i * ROWH;
            Tex ic = icon(nm);
            if (ic != null) {
                g.image(ic, new Coord(4, y + 2), new Coord(16, 16));
            }
            if (a == null) {
                continue;
            }
            g.image(
                    text(CharWnd.attrnm.get(nm) + " " + a.attr.comp, a.av ? READY : FayteSkin.TEXT)
                            .tex(),
                    new Coord(24, y));
            int gn = FayteProfWnd.inspgain(ui, nm);
            Color gc = a.exp + gn >= a.cap ? READY : new Color(0xE8, 0xC8, 0x40);
            g.aimage(
                    text(a.exp + (gn > 0 ? " +" + gn : "") + " / " + a.cap, gn > 0 ? gc : dim)
                            .tex(),
                    new Coord(W - 6, y),
                    1.0,
                    0.0);
            Coord bc = new Coord(24, y + FayteSkin.labelf.height() + 2);
            Coord bs = new Coord(W - 30, 4);
            Color c = a.av ? READY : EXPC;
            g.chcolor(FayteSkin.mix(FayteSkin.PANEL, c, 0.25));
            g.frect(bc, bs);
            g.chcolor(c);
            g.frect(bc, new Coord((int) (Utils.clip((double) a.exp / Math.max(1, a.cap), 0, 1) * bs.x), bs.y));
            if (gn > 0) {
                int x0 = bc.x + (int) (Utils.clip((double) a.exp / Math.max(1, a.cap), 0, 1) * bs.x);
                int x1 = bc.x + (int) (Utils.clip((double) (a.exp + gn) / Math.max(1, a.cap), 0, 1) * bs.x);
                g.chcolor(gc);
                g.frect(new Coord(x0, bc.y), new Coord(Math.max(1, x1 - x0), bs.y));
            }
            g.chcolor();
        }
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (button == 1 && !FayteHud.editing()) {
            FayteAlmanacWnd.skills(getparent(GameUI.class), false);
            return true;
        }
        return false;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        return "Pinned proficiencies. Click to open Skills; right-click a proficiency there to pin or unpin it.";
    }
}
