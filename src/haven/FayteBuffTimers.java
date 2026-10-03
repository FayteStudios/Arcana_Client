package haven;

import java.util.Map;
import java.util.WeakHashMap;

public class FayteBuffTimers {
    private static final String[] WATCH = {"fed up", "full"};
    private static final Map<Buff, Long> seen = new WeakHashMap<>();
    private static long last = 0L;

    private static boolean watched(String name) {
        String l = name.toLowerCase();
        for (String w : WATCH) {
            if (l.contains(w)) {
                return true;
            }
        }
        return false;
    }

    public static void tick(GameUI gui) {
        long now = System.currentTimeMillis();
        if (!FayteModules.TIMERS.on() || now - last < 2000L || gui.ui == null || gui.ui.sess == null) {
            return;
        }
        last = now;
        synchronized (gui.ui.sess.glob.buffs) {
            for (Buff b : gui.ui.sess.glob.buffs.values()) {
                if (b.cticks < 0 || b.cmeter < 0) {
                    continue;
                }
                String name;
                try {
                    name = b.tooltip();
                } catch (Loading e) {
                    continue;
                }
                if (name == null || !watched(name)) {
                    continue;
                }
                Long g = seen.get(b);
                if (g != null && g == b.gettime) {
                    continue;
                }
                seen.put(b, b.gettime);
                long left = (long) ((b.cticks * 0.06 - (now - b.gettime) / 1000.0) * 1000.0);
                if (left > 30000L) {
                    FayteTimers.restart(name, left);
                    FayteMsg.say("Timer started: " + name + " (" + FayteTimers.fmt(left) + ")");
                }
            }
        }
    }

    public static void craving(GameUI gui) {
        if (gui == null || !FayteModules.TIMERS.on() || gui.ui == null || gui.ui.sess == null) {
            return;
        }
        int hi = 0;
        Map<String, Glob.CAttr> ca = gui.ui.sess.glob.cattr;
        synchronized (ca) {
            for (String h : new String[] {"blood", "phlegm", "ybile", "bbile"}) {
                Glob.CAttr a = ca.get(h);
                if (a != null) {
                    hi = Math.max(hi, a.getComp());
                }
            }
        }
        if (hi <= 0) {
            return;
        }
        long ms = (long) (hi / 1000.0 * 60000.0);
        FayteTimers.restart("Next craving", ms);
        FayteMsg.say("Timer started: next craving in about " + FayteTimers.fmt(ms)
                + " (estimate: highest humour \u00d7 1 minute)");
    }
}
