package haven;

import java.util.Map;
import java.util.WeakHashMap;

public class FayteTrade {
    public static final String PREFIX = "Trade Order";
    private static final long HOUR = 3600000L;
    private static final Map<Window, Boolean> seen = new WeakHashMap<>();
    private static long loginat = 0L;

    public static void login() {
        loginat = System.currentTimeMillis();
    }

    public static long online() {
        return loginat == 0L ? 0L : System.currentTimeMillis() - loginat;
    }

    public static String timername() {
        return PREFIX;
    }

    public static boolean istrade(FayteTimers.Timer t) {
        return t != null && t.name != null && t.name.startsWith(PREFIX);
    }

    public static boolean ready() {
        return online() >= HOUR;
    }

    public static String waitlabel() {
        return "online " + FayteProgress.fmt(Math.max(0L, HOUR - online())) + " more";
    }

    private static boolean running() {
        String n = timername();
        for (FayteTimers.Timer t : FayteTimers.all()) {
            if (n.equals(t.name) && t.end >= 0 && !t.done) {
                return true;
            }
        }
        return false;
    }

    public static void tick(GameUI gui) {
        if (!FayteModules.TIMERS.on()) {
            return;
        }
        for (Widget w = gui.child; w != null; w = w.next) {
            if (w instanceof Window
                    && w.visible
                    && ((Window) w).cap != null
                    && !seen.containsKey(w)
                    && ((Window) w).cap.text.equalsIgnoreCase("Trade Order")) {
                seen.put((Window) w, true);
                if (!running()) {
                    FayteTimers.restart(timername(), 12L * HOUR);
                    FayteMsg.say("Trade order timer started: the next one is available in 12 hours (and after an hour"
                            + " logged in).");
                }
            }
        }
    }
}
