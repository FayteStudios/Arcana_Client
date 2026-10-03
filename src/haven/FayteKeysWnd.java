package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteKeysWnd extends Window {
    private static final Coord SZ = new Coord(FayteSkin.s(460), FayteSkin.s(480));
    private static final String[] TIPS = {
        "Mouse & tips",
        "hint:Ctrl + left-click an item: drop it.",
        "hint:Shift + left-click: move an item to the other open container.",
        "hint:Shift + Alt + left-click: move all similar items.",
        "hint:Ctrl + scroll over a container: move items in or out one by one.",
        "hint:Alt + left-click: move all items of the same kind and purity.",
        "hint:Ctrl + right-click a container: open all containers of that kind.",
        "hint:Shift + right-click: use the object, then keep taking the same item.",
        "hint:Middle-click an item or object: Inspect it.",
        "hint:Right-click empty ground: Smart interact near the click.",
        "hint:Console (:) act lo, act lo cs: log out / to character select.",
    };
    private static final int ROWH = FayteSkin.s(20);
    private static final int KEYW = 150;
    private static final int FOOT = FayteSkin.labelf.height() * 2 + 12;
    private static final Color CHANGED = new Color(0xE8, 0xB9, 0x3A);
    private static final Color DIM = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.55);
    private static FayteKeysWnd instance = null;
    private final Map<String, Text> texts = new HashMap<>();
    private int scroll = 0;

    private FayteKeysWnd(Widget parent) {
        super(new Coord(200, 100), SZ, parent, "Key Bindings");
        justclose = true;
    }

    public static FayteKeysWnd embedded(Widget parent, Coord c) {
        FayteKeysWnd w = new FayteKeysWnd(parent);
        w.embed();
        w.c = c;
        return w;
    }

    public static void toggle(GameUI gui) {
        if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
        } else if (gui != null) {
            instance = new FayteKeysWnd(gui);
        }
    }

    private Text text(String s, Color c) {
        String k = c.getRGB() + "|" + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 400) {
                texts.clear();
            }
            t = FayteSkin.labelf.render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private List<Object> rows() {
        List<Object> ret = new ArrayList<>();
        String grp = null;

        for (FayteKeys.Action a : FayteKeys.actions()) {
            if (!a.group.equals(grp)) {
                grp = a.group;
                ret.add(grp);
            }
            ret.add(a);
        }
        if (!FayteKeys.GRID.equals(grp)) {
            ret.add(FayteKeys.GRID);
            ret.add("hint:Right-click an action in the action grid, then press a key.");
        }
        for (String t : TIPS) {
            ret.add(t);
        }
        return ret;
    }

    private int visible() {
        return (asz.y - FOOT) / ROWH;
    }

    @Override
    public void cdraw(GOut g) {
        List<Object> rs = rows();
        int vis = visible();
        scroll = Math.max(0, Math.min(scroll, rs.size() - vis));
        String cap = FayteKeys.capturing();
        int kx = asz.x - KEYW - 4;

        for (int i = 0; i < vis && i + scroll < rs.size(); i++) {
            Object r = rs.get(i + scroll);
            int y = i * ROWH;
            if (r instanceof String) {
                String s = (String) r;
                if (s.startsWith("hint:")) {
                    g.image(text(s.substring(5), DIM).tex(), new Coord(10, y + 3));
                } else {
                    FayteSkin.box(g, new Coord(0, y + 2), new Coord(asz.x, ROWH - 3), FayteSkin.BORDER, null);
                    g.image(text(s, FayteSkin.TEXT).tex(), new Coord(6, y + 4));
                }
            } else {
                FayteKeys.Action a = (FayteKeys.Action) r;
                g.image(text(a.label, FayteSkin.TEXT).tex(), new Coord(10, y + 3));
                boolean capping = a.id.equals(cap);
                FayteSkin.box(
                        g,
                        new Coord(kx, y + 1),
                        new Coord(KEYW, ROWH - 3),
                        capping ? FayteSkin.BORDER : FayteSkin.PANEL,
                        FayteSkin.BORDER);
                String ks = capping ? "Press a key\u2026" : FayteKeys.combo(FayteKeys.effective(a));
                Color kc = capping ? FayteSkin.TEXT : (FayteKeys.changed(a) ? CHANGED : FayteSkin.TEXT);
                g.aimage(text(ks, kc).tex(), new Coord(kx + KEYW / 2, y + ROWH / 2), 0.5, 0.5);
            }
        }
        int fy = asz.y - FOOT + 4;
        g.image(
                text("Click a key box, then press the new key. Esc cancels, Backspace removes.", DIM)
                        .tex(),
                new Coord(4, fy));
        g.image(
                text("Right-click a key box to reset it. Gold keys differ from the game's default.", DIM)
                        .tex(),
                new Coord(4, fy + FayteSkin.labelf.height() + 2));
    }

    private Object rowat(Coord c) {
        Coord p = c.sub(atl);
        if (p.x < 0 || p.y < 0 || p.x >= asz.x || p.y >= asz.y - FOOT) {
            return null;
        }
        List<Object> rs = rows();
        int i = p.y / ROWH + scroll;
        return i < rs.size() ? rs.get(i) : null;
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        Coord p = c.sub(atl);
        if (p.x >= 0 && p.y >= 0 && p.x < asz.x && p.y < asz.y) {
            parent.setfocus(this);
            raise();
            Object r = rowat(c);
            if (r instanceof FayteKeys.Action && p.x >= asz.x - KEYW - 4) {
                FayteKeys.Action a = (FayteKeys.Action) r;
                if (button == 1) {
                    FayteKeys.startcapture(a.id, a.label);
                } else if (button == 3) {
                    FayteKeys.reset(a);
                }
            }
            return true;
        }
        return super.mousedown(c, button);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        Object r = rowat(c);
        if (r instanceof FayteKeys.Action) {
            String d = FayteKeys.desc((FayteKeys.Action) r);
            return d == null ? ((FayteKeys.Action) r).label : d;
        }
        return super.tooltip(c, prev);
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        scroll = Math.max(0, scroll + (amount > 0 ? 3 : -3));
        return true;
    }

    @Override
    public void destroy() {
        if (instance == this) {
            instance = null;
        }
        super.destroy();
    }

    @Override
    protected boolean compactstyle() {
        return true;
    }
}
