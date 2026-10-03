package haven;

import java.util.function.IntConsumer;

public class FayteSplit extends Widget {
    public static final int W = 6;
    private final boolean vertical;
    private final IntConsumer moved;
    private boolean drag = false;
    private boolean hov = false;
    private int off = 0;

    public FayteSplit(Widget parent, boolean vertical, IntConsumer moved) {
        super(Coord.z, new Coord(W, W), parent);
        this.vertical = vertical;
        this.moved = moved;
        tooltip = "Drag to resize";
    }

    public void place(int pos, int from, int len) {
        if (vertical) {
            c = new Coord(pos, from);
            sz = new Coord(W, len);
        } else {
            c = new Coord(from, pos);
            sz = new Coord(len, W);
        }
    }

    @Override
    public void draw(GOut g) {
        g.chcolor(drag || hov ? FayteSkin.TEXT : FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.8));
        if (vertical) {
            g.frect(new Coord(W / 2 - 1, 0), new Coord(2, sz.y));
        } else {
            g.frect(new Coord(0, W / 2 - 1), new Coord(sz.x, 2));
        }
        g.chcolor();
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (button != 1) {
            return false;
        }
        drag = true;
        off = vertical ? c.x : c.y;
        ui.grabmouse(this);
        return true;
    }

    @Override
    public void mousemove(Coord c) {
        hov = c.isect(Coord.z, sz);
        if (drag) {
            int p = vertical ? this.c.x + c.x - off : this.c.y + c.y - off;
            moved.accept(p);
        }
    }

    @Override
    public boolean mouseup(Coord c, int button) {
        if (drag && button == 1) {
            drag = false;
            ui.grabmouse(null);
            return true;
        }
        return false;
    }
}
