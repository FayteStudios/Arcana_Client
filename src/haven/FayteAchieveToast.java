package haven;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;

public class FayteAchieveToast extends Widget {
    public static final String KEY = "achtoast";
    public static final Coord SZ = new Coord(FayteSkin.s(320), FayteSkin.s(70));
    private static final Color GOLD = new Color(0xC8, 0xAA, 0x62);
    private static final long IN = 300L;
    private static final long HOLD = 4200L;
    private static final long OUT = 350L;
    private static final Deque<String[]> queue = new ArrayDeque<>();
    private static FayteAchieveToast current = null;

    static void reset() {
        current = null;
        queue.clear();
    }

    private final String icon;
    private final Text head;
    private final Text title;
    private final Text sub;
    private final long start;
    private final Coord home;
    private Tex ictex = null;

    private FayteAchieveToast(GameUI gui, String[] a) {
        super(FayteHud.ghostpos(KEY, gui.sz), SZ, gui);
        home = c;
        icon = a[0];
        head = FayteSkin.labelf.render("Achievement unlocked", FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.7));
        title = FayteSkin.titlef.render(a[1], GOLD);
        sub = FayteSkin.labelf.render(a[2], FayteSkin.TEXT);
        start = System.currentTimeMillis();
        raise();
    }

    public static void show(GameUI gui, String icon, String title, String sub) {
        if (gui == null) {
            return;
        }
        queue.add(new String[] {icon, title, sub});
        next(gui);
    }

    private static void next(GameUI gui) {
        if ((current == null || !current.attached()) && !queue.isEmpty()) {
            current = new FayteAchieveToast(gui, queue.poll());
        }
    }

    private Tex icon() {
        if (ictex == null && icon != null) {
            try {
                ictex = Resource.load(icon).layer(Resource.imgc).tex();
            } catch (Loading e) {
            } catch (RuntimeException e) {
                FayteLog.once("FayteAchieveToast.icon", e);
            }
        }
        return ictex;
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        long el = System.currentTimeMillis() - start;
        int off;
        if (el < IN) {
            off = (int) (-(SZ.y + 10) * (1.0 - (double) el / IN));
        } else if (el < IN + HOLD) {
            off = 0;
        } else if (el < IN + HOLD + OUT) {
            off = (int) (-(SZ.y + 10) * ((double) (el - IN - HOLD) / OUT));
        } else {
            GameUI gui = getparent(GameUI.class);
            ui.destroy(this);
            if (current == this) {
                current = null;
            }
            next(gui);
            return;
        }
        c = home.add(0, off);
    }

    @Override
    public void draw(GOut g) {
        FayteSkin.box(g, Coord.z, sz, FayteSkin.mix(FayteSkin.PANEL, GOLD, 0.1), GOLD);
        int is = FayteSkin.s(44);
        Tex it = icon();
        Coord ic = new Coord(FayteSkin.s(12), (sz.y - is) / 2);
        if (it != null) {
            g.image(it, ic, new Coord(is, is));
        } else {
            FayteSkin.box(g, ic, new Coord(is, is), FayteSkin.mix(FayteSkin.PANEL, GOLD, 0.3), GOLD);
            g.aimage(FayteSkin.titlef.render("\u2605", GOLD).tex(), ic.add(is / 2, is / 2), 0.5, 0.5);
        }
        int x = ic.x + is + FayteSkin.s(12);
        g.image(head.tex(), new Coord(x, FayteSkin.s(8)));
        g.image(title.tex(), new Coord(x, FayteSkin.s(24)));
        g.image(sub.tex(), new Coord(x, FayteSkin.s(46)));
        long el = System.currentTimeMillis() - start - IN;
        if (el >= 0 && el < 1200L) {
            double f = el / 1200.0;
            int bw = FayteSkin.s(40);
            int bx = (int) (-bw + (sz.x + bw * 2) * f);
            for (int i = 0; i < bw; i++) {
                int a = (int) (70 * Math.sin(Math.PI * i / bw));
                int xx = bx + i;
                if (xx >= 2 && xx < sz.x - 2) {
                    g.chcolor(255, 240, 200, a);
                    g.frect(new Coord(xx, 2), new Coord(1, sz.y - 4));
                }
            }
            g.chcolor();
        }
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        GameUI gui = getparent(GameUI.class);
        if (button == 1 && gui != null && !FayteHud.editing()) {
            FayteAlmanacWnd.achievements(gui);
        }
        return true;
    }
}
