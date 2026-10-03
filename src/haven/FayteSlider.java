package haven;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public class FayteSlider extends Widget {
    private final DoubleConsumer changed;
    private final DoubleFunction<String> label;
    private double val;
    private boolean drag = false;
    private Text lt = null;
    private String ls = null;

    public FayteSlider(
            Coord c, int w, Widget parent, double val, DoubleFunction<String> label, DoubleConsumer changed) {
        super(c, new Coord(w, 18), parent);
        this.val = Math.max(0, Math.min(1, val));
        this.label = label;
        this.changed = changed;
    }

    private int trackw() {
        return sz.x - 60;
    }

    @Override
    public void draw(GOut g) {
        int tw = trackw();
        int y = sz.y / 2;
        g.chcolor(FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.8));
        g.frect(new Coord(0, y - 2), new Coord(tw, 4));
        g.chcolor(FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.6));
        g.frect(new Coord(0, y - 2), new Coord((int) (tw * val), 4));
        int kx = (int) (tw * val);
        FayteSkin.box(
                g,
                new Coord(kx - 5, 1),
                new Coord(10, sz.y - 2),
                drag ? FayteSkin.TEXT : FayteSkin.BORDER,
                FayteSkin.TEXT);
        g.chcolor();
        String s = label.apply(val);
        if (!s.equals(ls)) {
            ls = s;
            lt = FayteSkin.labelf.render(s, FayteSkin.TEXT);
        }
        g.aimage(lt.tex(), new Coord(sz.x, y), 1.0, 0.5);
    }

    private void set(Coord c) {
        val = Math.max(0, Math.min(1, (double) c.x / Math.max(1, trackw())));
        changed.accept(val);
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (button != 1 || c.x > trackw() + 6) {
            return false;
        }
        drag = true;
        ui.grabmouse(this);
        set(c);
        return true;
    }

    @Override
    public void mousemove(Coord c) {
        if (drag) {
            set(c);
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
