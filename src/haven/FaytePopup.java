package haven;

import java.util.function.IntConsumer;

public class FaytePopup extends Widget {
    private static final int ROWH = FayteSkin.s(26);
    private final Text[] rows;
    private final int sel;
    private final IntConsumer pick;
    private int hover = -1;

    public FaytePopup(Coord c, Widget parent, String[] opts, int sel, IntConsumer pick) {
        super(c, Coord.z, parent);
        rows = new Text[opts.length];
        int w = FayteSkin.s(140);

        for (int i = 0; i < opts.length; i++) {
            rows[i] = FayteSkin.labelf.render(opts[i], FayteSkin.TEXT);
            w = Math.max(w, rows[i].sz().x + FayteSkin.s(40));
        }
        this.sel = sel;
        this.pick = pick;
        sz = new Coord(w, opts.length * ROWH + 4);
        if (this.c.x + sz.x > parent.sz.x) {
            this.c = new Coord(Math.max(0, parent.sz.x - sz.x), this.c.y);
        }
        if (this.c.y + sz.y > parent.sz.y) {
            this.c = new Coord(this.c.x, Math.max(0, parent.sz.y - sz.y));
        }
        raise();
        ui.grabmouse(this);
    }

    @Override
    public void draw(GOut g) {
        FayteSkin.box(g, Coord.z, sz, FayteSkin.PANEL, FayteSkin.BORDER);

        for (int i = 0; i < rows.length; i++) {
            int y = 2 + i * ROWH;
            if (i == hover) {
                FayteSkin.box(g, new Coord(2, y), new Coord(sz.x - 4, ROWH), FayteSkin.HOVER, null);
            }
            if (i == sel) {
                g.chcolor(FayteSkin.TEXT);
                g.frect(new Coord(7, y + ROWH / 2 - 2), new Coord(4, 4));
                g.chcolor();
            }
            g.aimage(rows[i].tex(), new Coord(16, y + ROWH / 2), 0.0, 0.5);
        }
    }

    private int rowat(Coord c) {
        if (c.x < 0 || c.x >= sz.x || c.y < 2 || c.y >= sz.y - 2) {
            return -1;
        }
        return (c.y - 2) / ROWH;
    }

    @Override
    public void mousemove(Coord c) {
        hover = rowat(c);
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        int r = rowat(c);
        ui.grabmouse(null);
        ui.destroy(this);
        if (r >= 0 && r < rows.length && button == 1) {
            pick.accept(r);
        }
        return true;
    }
}
