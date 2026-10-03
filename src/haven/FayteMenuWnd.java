package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteMenuWnd extends Window {
    private static final Color GOLD = new Color(0xF0, 0xC8, 0x40);
    private static FayteMenuWnd instance = null;
    private final String key;
    private final String label;
    private final Map<String, Tex> texts = new HashMap<>();
    private boolean everywhere;
    private final int rowh = FayteSkin.s(24);
    private int scroll = 0;
    private List<String> rows = new ArrayList<>();

    public static void open(GameUI gui, String key, String label, List<String> current) {
        if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
        }
        instance = new FayteMenuWnd(gui, key, label);
    }

    private FayteMenuWnd(GameUI gui, String key, String label) {
        super(
                new Coord(FayteSkin.s(160), FayteSkin.s(120)),
                new Coord(FayteSkin.s(360), FayteSkin.s(330)),
                gui,
                "Arrange right-click options");
        this.key = key;
        this.label = label;
        justclose = true;
        everywhere = key == null;
        new Button(new Coord(0, asz.y - Button.bh()), FayteSkin.s(110), this, "Reset") {
            @Override
            public void click() {
                FayteMenus.Rule r = FayteMenuWnd.this.rule();
                r.order.clear();
                r.auto.clear();
                r.hide.clear();
                FayteMenus.save();
            }
        };
    }

    private FayteMenus.Rule rule() {
        return everywhere ? FayteMenus.rule(FayteMenus.GLOBAL, true) : FayteMenus.rule(key, true);
    }

    private List<String> ordered() {
        FayteMenus.Rule r = rule();
        List<String> l = new ArrayList<>();
        for (String n : r.order) {
            if (r.seen.contains(n) || everywhere) {
                l.add(n);
            }
        }
        for (String n : r.seen) {
            if (!l.contains(n)) {
                l.add(n);
            }
        }
        return l;
    }

    private Tex text(String s, Color c) {
        String k = c.getRGB() + "|" + s;
        Tex t = texts.get(k);
        if (t == null) {
            if (texts.size() > 300) {
                texts.clear();
            }
            t = FayteSkin.labelf.render(s, c).tex();
            texts.put(k, t);
        }
        return t;
    }

    private int top() {
        return FayteSkin.s(30) + FayteSkin.labelf.height() + 6;
    }

    private int tabw() {
        return (asz.x - FayteSkin.s(6)) / 2;
    }

    @Override
    public void cdraw(GOut g) {
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.7);
        String[] tabs = {"This: " + (label == null ? "(nothing)" : label), "Everywhere"};
        for (int i = 0; i < 2; i++) {
            boolean sel = (i == 1) == everywhere;
            boolean dis = i == 0 && key == null;
            Coord tc = new Coord(i * (tabw() + FayteSkin.s(6)), 0);
            FayteSkin.box(
                    g,
                    tc,
                    new Coord(tabw(), FayteSkin.s(24)),
                    sel ? FayteSkin.mix(FayteSkin.PANEL, GOLD, 0.25) : FayteSkin.PANEL,
                    sel ? GOLD : FayteSkin.BORDER);
            GOut tg = g.reclip(tc.add(6, 0), new Coord(tabw() - 12, FayteSkin.s(24)));
            tg.aimage(
                    text(tabs[i], dis ? dim : (sel ? GOLD : FayteSkin.TEXT)), new Coord(0, FayteSkin.s(12)), 0.0, 0.5);
        }
        g.image(
                text(
                        everywhere
                                ? "Order for every right-click menu (a thing's own order wins)."
                                : "Order for this thing's menu. Top is shown first.",
                        dim),
                new Coord(0, FayteSkin.s(30)));
        rows = ordered();
        FayteMenus.Rule r = rule();
        int w = asz.x;
        int bottom = asz.y - Button.bh() - 6;
        int vis = Math.max(1, (bottom - top()) / rowh);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - vis)));
        if (rows.isEmpty()) {
            g.image(text("No options seen yet. Right-click things to fill this list.", dim), new Coord(0, top()));
        }
        for (int i = scroll; i < rows.size() && i < scroll + vis; i++) {
            String n = rows.get(i);
            int y = top() + (i - scroll) * rowh;
            boolean hid = r.hide.contains(n);
            boolean auto = r.auto.contains(n);
            FayteSkin.box(
                    g,
                    new Coord(0, y),
                    new Coord(w, rowh - 2),
                    FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.12),
                    null);
            g.image(text(n, hid ? dim : FayteSkin.TEXT), new Coord(6, y + 3));
            int bx = w - FayteSkin.s(4 * 28);
            String[] bl = {"\u25b2", "\u25bc", auto ? "\u2605" : "\u2606", hid ? "Show" : "Hide"};
            for (int b = 0; b < 4; b++) {
                Coord bc = new Coord(bx + b * FayteSkin.s(28), y + 1);
                boolean on = (b == 2 && auto) || (b == 3 && hid);
                FayteSkin.box(
                        g,
                        bc,
                        new Coord(FayteSkin.s(26), rowh - 4),
                        on ? FayteSkin.mix(FayteSkin.PANEL, GOLD, 0.3) : FayteSkin.PANEL,
                        on ? GOLD : FayteSkin.BORDER);
                g.aimage(text(bl[b], on ? GOLD : FayteSkin.TEXT), bc.add(FayteSkin.s(13), (rowh - 4) / 2), 0.5, 0.5);
            }
        }
    }

    private void move(String n, int d) {
        FayteMenus.Rule r = rule();
        List<String> l = ordered();
        int i = l.indexOf(n);
        int j = i + d;
        if (i < 0 || j < 0 || j >= l.size()) {
            return;
        }
        l.set(i, l.get(j));
        l.set(j, n);
        r.order.clear();
        r.order.addAll(l);
        FayteMenus.save();
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        Coord p = c.sub(atl);
        if (button == 1 && p.y >= 0 && p.y < FayteSkin.s(24)) {
            boolean want = p.x >= tabw();
            if (!want && key == null) {
                return true;
            }
            everywhere = want;
            scroll = 0;
            return true;
        }
        if (button == 1 && p.y >= top()) {
            int i = scroll + (p.y - top()) / rowh;
            if (i >= 0 && i < rows.size()) {
                String n = rows.get(i);
                int bx = asz.x - FayteSkin.s(4 * 28);
                if (p.x >= bx) {
                    int b = (p.x - bx) / FayteSkin.s(28);
                    FayteMenus.Rule r = rule();
                    if (b == 0) {
                        move(n, -1);
                    } else if (b == 1) {
                        move(n, 1);
                    } else if (b == 2) {
                        if (!r.auto.remove(n)) {
                            r.auto.add(n);
                            r.hide.remove(n);
                        }
                        FayteMenus.save();
                    } else if (b == 3) {
                        if (!r.hide.remove(n)) {
                            r.hide.add(n);
                            r.auto.remove(n);
                        }
                        FayteMenus.save();
                    }
                    return true;
                }
            }
        }
        return super.mousedown(c, button);
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        scroll += amount;
        return true;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        Coord p = c.sub(atl);
        if (p.y >= top() && p.x >= asz.x - FayteSkin.s(4 * 28)) {
            int b = (p.x - (asz.x - FayteSkin.s(4 * 28))) / FayteSkin.s(28);
            String[] tips = {
                "Move up",
                "Move down",
                "Automatic: pick this by itself whenever it's offered (hold Shift to see the menu anyway)",
                "Hide this option from the menu"
            };
            if (b >= 0 && b < 4) {
                return tips[b];
            }
        }
        return super.tooltip(c, prev);
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
