package haven;

public class FayteSeason extends Widget {
    private static final String[] RES = {"gfx/hud/coldsnap", "gfx/hud/everbloom", "gfx/hud/bloodmoon"};
    private static final String[] NAMES = {"Coldsnap", "Everbloom", "Blood Moon"};
    private static int last = -1;
    private static FayteSeason cur = null;
    private static GameUI lastgui = null;
    private static long since = 0L;
    private final int season;
    private final long start = System.currentTimeMillis();
    private Tex img = null;
    private Text name = null;

    public static boolean on() {
        return Utils.getprefb("fayte_season_splash", true);
    }

    public static void seton(boolean v) {
        Utils.setprefb("fayte_season_splash", v);
    }

    public static void tick(GameUI gui) {
        int s = gui.ui.sess.glob.season;
        long now = System.currentTimeMillis();
        if (gui != lastgui) {
            lastgui = gui;
            since = now;
        }
        if (now - since < 20000L) {
            last = s;
            return;
        }
        if (last >= 0 && s != last && on() && s >= 0 && s < RES.length) {
            show(gui, s);
        }
        last = s;
        if (cur != null && cur.attached() && cur.over()) {
            gui.ui.destroy(cur);
            cur = null;
        }
    }

    public static void show(GameUI gui, int s) {
        if (cur != null && cur.attached()) {
            gui.ui.destroy(cur);
        }
        cur = new FayteSeason(gui, s);
    }

    private FayteSeason(GameUI gui, int season) {
        super(Coord.z, gui.sz, gui);
        this.season = season;
        raise();
    }

    private double t() {
        return (System.currentTimeMillis() - start) / 1000.0;
    }

    public boolean over() {
        return t() > 4.0;
    }

    @Override
    public void draw(GOut g) {
        sz = parent.sz;
        double t = this.t();
        double a = t < 0.6 ? t / 0.6 : (t < 2.4 ? 1.0 : Math.max(0, 1.0 - (t - 2.4) / 1.6));
        int al = (int) (255 * a);
        try {
            if (img == null) {
                img = Resource.loadtex(RES[season]);
            }
        } catch (Loading e) {
            return;
        }
        if (name == null) {
            name = Window.bigtf.render(NAMES[season], FayteSkin.TEXT);
        }
        g.chcolor(0, 0, 0, (int) (140 * a));
        g.frect(Coord.z, sz);
        Coord isz = img.sz();
        double f = Math.min(sz.x * 0.5 / isz.x, sz.y * 0.5 / isz.y);
        Coord dsz = new Coord((int) (isz.x * f), (int) (isz.y * f));
        g.chcolor(255, 255, 255, al);
        g.image(img, sz.sub(dsz).div(2), dsz);
        g.aimage(name.tex(), new Coord(sz.x / 2, (sz.y + dsz.y) / 2 + 20), 0.5, 0.0);
        g.chcolor();
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        return false;
    }
}
