package haven;

import java.awt.Color;
import java.awt.Font;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class FayteProgress {
    private static final Map<GItem, long[]> samples = new WeakHashMap<>();
    private static final Map<Window, Object[]> stations = new WeakHashMap<>();
    private static final Map<Long, long[]> fuel = new HashMap<>();
    private static final Map<String, Tex> texts = new HashMap<>();
    private static final Text.Foundry etaf =
            new Text.Foundry(new Font("SansSerif", Font.BOLD, 10), Color.WHITE).aa(true);

    public static boolean on() {
        return FayteModules.TIMERS.on();
    }

    public static void sample(GItem it, int v) {
        long now = System.currentTimeMillis();
        synchronized (samples) {
            long[] s = samples.get(it);
            if (s == null || v < s[1]) {
                samples.put(it, new long[] {now, v, -1, -1, now, v});
                return;
            }
            if (v == s[5]) {
                return;
            }
            if (s[2] < 0) {
                s[2] = now;
                s[3] = v;
            }
            s[4] = now;
            s[5] = v;
        }
    }

    private static final Object[][] BASE = {
        {"Raw Rabbit Skin", 2.0},
        {"Raw Beaver Pelt", 4.0},
        {"Raw Deer Hide", 9.0},
        {"Raw Bear Skin", 15.0},
        {"Raw Argopelt", 24.0},
        {"Raw March Hare Hide", 24.0},
        {"Raw Timber Rattler Skin", 1.0},
        {"Raw Wishpoosh Hide", 1.0},
        {"Swim Bladder", 0.25},
        {"Soaked Bark", 2.0},
        {"Argopelter Neck", 18.0},
        {"Brick", 1.0 / 6},
        {"Misshapen Lump of Clay", 1.0 / 6},
        {"Unburnt Clay Pot", 1.0 / 3},
        {"Unburnt Gardener's Pot", 1.0 / 3},
        {"Unburnt Large Urn", 1.0 / 3},
        {"Unfired Remains", 1.0},
        {"Unfired Jar of Tar", 5.0},
        {"Golden Egg", 8.0},
    };

    private static final Object[][] FULLBURN = {
        {"cement", 60.0}, {"brickstove", 24.0}, {"stove", 24.0}, {"fireplace", 4.0},
    };

    public static double fullburn(String rn) {
        if (rn == null) {
            return -1;
        }
        for (Object[] f : FULLBURN) {
            if (rn.contains((String) f[0])) {
                return (Double) f[1];
            }
        }
        return -1;
    }

    public static long fuelguess(String rn, int amount) {
        double h = fullburn(rn);
        return h > 0 && amount > 0 ? (long) (h * 3600000.0 * amount / 100.0) : -1;
    }

    public static double base(String name) {
        if (name == null) {
            return -1;
        }
        for (Object[] b : BASE) {
            if (((String) b[0]).equalsIgnoreCase(name)) {
                return (Double) b[1];
            }
        }
        return -1;
    }

    public static long eta(GItem it) {
        synchronized (samples) {
            long[] s = samples.get(it);
            if (s == null || s[2] < 0 || s[5] <= s[3] || s[4] <= s[2]) {
                double h = base(FayteAlmanac.itemname(it));
                if (h > 0) {
                    return (long) (h * 3600000.0 * (100 - Math.max(0, it.meter)) / 100.0);
                }
                return -1;
            }
            double rate = (double) (s[5] - s[3]) / (double) (s[4] - s[2]);
            long left = (long) ((100 - s[5]) / rate) - (System.currentTimeMillis() - s[4]);
            return Math.max(0, left);
        }
    }

    public static String fmt(long ms) {
        long m = (Math.max(0, ms) + 59999) / 60000;
        return String.format("%d:%02d", m / 60, m % 60);
    }

    public static String clock(long ms) {
        Date at = new Date(System.currentTimeMillis() + Math.max(0, ms));
        String s = DateFormat.getTimeInstance(DateFormat.SHORT).format(at);
        return s.replaceAll("[^0-9:.]+(AM|am)$", "a").replaceAll("[^0-9:.]+(PM|pm)$", "p");
    }

    private static Tex text(String s) {
        Tex t = texts.get(s);
        if (t == null) {
            if (texts.size() > 200) {
                texts.clear();
            }
            t = new TexI(Utils.outline2(etaf.render(s).img, Color.BLACK, true));
            texts.put(s, t);
        }
        return t;
    }

    public static void draw(GOut g, WItem w) {
        if (!on() || w.item.meter <= 0 || w.item.meter >= 100) {
            return;
        }
        long e = eta(w.item);
        if (e >= 0) {
            g.image(text(clock(e)), new Coord(1, 0));
        }
    }

    private static boolean station(GameUI gui, Widget w) {
        if (w.getClass() != Window.class || !w.visible || ((Window) w).cap == null || w == gui.invwnd) {
            return false;
        }
        String t = ((Window) w).cap.text.toLowerCase();
        return !t.contains("pack") && !t.contains("belt") && !t.contains("sack") && !t.contains("bag");
    }

    private static List<WItem> items(Widget w) {
        List<WItem> l = new ArrayList<>();
        for (Widget c = w.child; c != null; c = c.next) {
            if (c instanceof WItem) {
                l.add((WItem) c);
            } else {
                l.addAll(items(c));
            }
        }
        return l;
    }

    private static List<VMeter> meters(Widget w) {
        List<VMeter> l = new ArrayList<>();
        for (Widget c = w.child; c != null; c = c.next) {
            if (c instanceof VMeter) {
                l.add((VMeter) c);
            }
            l.addAll(meters(c));
        }
        return l;
    }

    public static long longest(Window w) {
        long best = -1;
        boolean unknown = false;
        for (WItem it : items(w)) {
            if (it.item.meter > 0 && it.item.meter < 100) {
                long e = eta(it.item);
                if (e < 0) {
                    unknown = true;
                }
                best = Math.max(best, e);
            }
        }
        return unknown && best < 0 ? -2 : best;
    }

    public static void tick(GameUI gui) {
        if (!on()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Widget w = gui.child; w != null; w = w.next) {
            if (!station(gui, w)) {
                continue;
            }
            Window win = (Window) w;
            Object[] st = stations.get(win);
            if (st == null) {
                Gob g = FayteTools.lastclickage() < 5000L ? FayteTools.lastgob(gui) : null;
                st = new Object[] {g == null ? -1L : g.id, null, null, 0L};
                stations.put(win, st);
            }
            final long gobid = (Long) st[0];
            if (gobid >= 0 && now - (Long) st[3] > 2000L) {
                st[3] = now;
                for (VMeter m : meters(win)) {
                    if (m.tipt == null) {
                        fuelsample(gobid, m.amount, now);
                        break;
                    }
                }
            }
        }
    }

    private static void fuelsample(long gobid, int v, long now) {
        long[] f = fuel.get(gobid);
        if (f == null || v > f[3]) {
            fuel.put(gobid, new long[] {now, v, now, v, -1, -1});
            return;
        }
        if (v < f[3]) {
            if (f[4] < 0) {
                f[4] = now;
                f[5] = v;
            }
            f[2] = now;
            f[3] = v;
        }
    }

    public static long fuelleft(long gobid, long now) {
        long[] f = fuel.get(gobid);
        if (f == null || f[4] < 0 || f[3] >= f[5] || f[2] <= f[4]) {
            return -1;
        }
        double rate = (double) (f[5] - f[3]) / (double) (f[2] - f[4]);
        return Math.max(0, (long) (f[3] / rate) - (now - f[2]));
    }
}
