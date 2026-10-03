package haven;

public class FayteGrip extends Widget {
    private final Window win;
    private final String pref;
    private final Coord min;
    private final Runnable relayout;
    private Coord start = null;
    private Coord startsz = null;

    public FayteGrip(Window win, String pref, Coord min, Runnable relayout) {
        super(Coord.z, new Coord(12, 12), win);
        this.win = win;
        this.pref = pref;
        this.min = min;
        this.relayout = relayout;
        tooltip = "Drag to resize";
    }

    public static Coord saved(String pref, Coord def, Coord min) {
        String[] p = Utils.getpref(pref, "").split(",");
        try {
            if (p.length == 2) {
                return new Coord(Math.max(min.x, Integer.parseInt(p[0])), Math.max(min.y, Integer.parseInt(p[1])));
            }
        } catch (NumberFormatException e) {
        }
        return def;
    }

    public void place() {
        c = win.asz.sub(sz);
        raise();
    }

    @Override
    public void draw(GOut g) {
        g.chcolor(FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.6));
        for (int i = 0; i < 3; i++) {
            int o = 3 + i * 4;
            g.line(new Coord(sz.x - 1, o), new Coord(o, sz.y - 1), 1);
        }
        g.chcolor();
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (button != 1) {
            return false;
        }
        start = rootpos().add(c);
        startsz = win.asz;
        ui.grabmouse(this);
        return true;
    }

    @Override
    public void mousemove(Coord c) {
        if (start != null) {
            Coord d = rootpos().add(c).sub(start);
            Coord nsz = new Coord(Math.max(min.x, startsz.x + d.x), Math.max(min.y, startsz.y + d.y));
            if (!nsz.equals(win.asz)) {
                win.resize(nsz);
                relayout.run();
                place();
            }
        }
    }

    @Override
    public boolean mouseup(Coord c, int button) {
        if (start != null && button == 1) {
            start = null;
            ui.grabmouse(null);
            Coord s = win.asz;
            Utils.setpref(pref, s.x + "," + s.y);
            return true;
        }
        return false;
    }
}
