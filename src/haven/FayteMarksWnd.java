package haven;

import java.awt.Color;
import java.util.List;

public class FayteMarksWnd extends Window {
    private static FayteMarksWnd instance = null;
    private final int rowh = FayteSkin.s(24);
    private int scroll = 0;
    private List<Object[]> rows;

    public static void toggle(GameUI gui) {
        if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
            instance = null;
        } else {
            instance = new FayteMarksWnd(gui);
        }
    }

    private FayteMarksWnd(GameUI gui) {
        super(
                new Coord(FayteSkin.s(120), FayteSkin.s(120)),
                new Coord(FayteSkin.s(340), FayteSkin.s(320)),
                gui,
                "Markers & alerts");
        justclose = true;
    }

    private int top() {
        return FayteSkin.labelf.height() + 8;
    }

    @Override
    public void cdraw(GOut g) {
        rows = FayteHighlights.all();
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.7);
        g.image(
                FayteSkin.labelf
                        .render(
                                rows.isEmpty()
                                        ? "Nothing marked yet. Mark things from their window or the Almanac."
                                        : "Everything you've coloured or set an alert on:",
                                dim)
                        .tex(),
                Coord.z);
        int w = asz.x;
        int vis = (asz.y - top()) / rowh;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - vis)));
        for (int i = scroll; i < rows.size() && i < scroll + vis; i++) {
            Object[] r = rows.get(i);
            int y = top() + (i - scroll) * rowh;
            int col = (Integer) r[1];
            boolean alert = (Boolean) r[2];
            Color c = col > 0 ? FayteHighlights.COLORS[col] : null;
            FayteSkin.box(
                    g,
                    new Coord(0, y + 2),
                    new Coord(FayteSkin.s(18), rowh - 6),
                    c != null ? c : FayteSkin.PANEL,
                    FayteSkin.BORDER);
            g.image(FayteSkin.labelf.render((String) r[0], FayteSkin.TEXT).tex(), new Coord(FayteSkin.s(26), y + 3));
            int ax = w - FayteSkin.s(90);
            FayteSkin.box(
                    g,
                    new Coord(ax, y + 2),
                    new Coord(FayteSkin.s(58), rowh - 6),
                    alert ? FayteSkin.mix(FayteSkin.PANEL, new Color(0xE3, 0xA8, 0x4A), 0.35) : FayteSkin.PANEL,
                    alert ? new Color(0xE3, 0xA8, 0x4A) : FayteSkin.BORDER);
            g.aimage(
                    FayteSkin.labelf
                            .render("Alert", alert ? FayteSkin.TEXT : dim)
                            .tex(),
                    new Coord(ax + FayteSkin.s(29), y + rowh / 2 - 1),
                    0.5,
                    0.5);
            FayteSkin.box(
                    g,
                    new Coord(w - FayteSkin.s(26), y + 2),
                    new Coord(FayteSkin.s(24), rowh - 6),
                    FayteSkin.PANEL,
                    FayteSkin.BORDER);
            g.aimage(
                    FayteSkin.labelf.render("\u2715", FayteSkin.TEXT).tex(),
                    new Coord(w - FayteSkin.s(14), y + rowh / 2 - 1),
                    0.5,
                    0.5);
        }
    }

    private Object[] rowat(Coord p) {
        if (rows == null || p.y < top()) {
            return null;
        }
        int i = scroll + (p.y - top()) / rowh;
        return i >= 0 && i < rows.size() ? rows.get(i) : null;
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        Coord p = c.sub(atl);
        Object[] r = rowat(p);
        if (r != null && button == 1 && p.x >= 0) {
            String nm = (String) r[0];
            int w = asz.x;
            if (p.x >= w - FayteSkin.s(26)) {
                FayteHighlights.remove(nm);
            } else if (p.x >= w - FayteSkin.s(90) && p.x < w - FayteSkin.s(32)) {
                FayteHighlights.togglealert(nm);
            } else if (p.x < FayteSkin.s(20)) {
                FayteHighlights.cycle(nm);
            } else {
                GameUI gui = getparent(GameUI.class);
                if (gui != null) {
                    FayteAlmanacWnd.showlink(gui, nm);
                }
            }
            return true;
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
        Object[] r = rowat(p);
        if (r != null) {
            int w = asz.x;
            if (p.x >= w - FayteSkin.s(26)) {
                return "Forget this mark and alert";
            } else if (p.x >= w - FayteSkin.s(90) && p.x < w - FayteSkin.s(32)) {
                return "Tell me when one comes into view";
            } else if (p.x < FayteSkin.s(20)) {
                return "Colour on the ground: click to change";
            }
            return "Open in the Almanac";
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
