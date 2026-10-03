package haven;

import java.util.Map;
import java.util.WeakHashMap;

public class FayteWinWatch {
    private static final Map<Window, Object[]> seen = new WeakHashMap<>();
    private static int logged = 0;

    private static void log(String s) {
        if (logged < 400) {
            logged++;
            FayteLog.log("Windows: " + s);
        }
    }

    private static String name(Window w) {
        return w.cap == null ? w.getClass().getSimpleName() : w.cap.text;
    }

    public static void tick(GameUI gui) {
        long now = System.currentTimeMillis();
        for (Widget w = gui.child; w != null; w = w.next) {
            if (!(w instanceof Window) || !w.visible || !((Window) w).hasinv()) {
                continue;
            }
            Window win = (Window) w;
            Object[] s = seen.get(win);
            if (s == null) {
                seen.put(win, new Object[] {now, win.c, win.sz});
                log(name(win) + " opened at " + win.c + " size " + win.sz + (win.pinned ? " (pinned)" : ""));
                continue;
            }
            Coord was = (Coord) s[1];
            if (!was.equals(win.c)) {
                log(name(win) + " moved " + was + " -> " + win.c + (win.dm ? " by dragging" : " (not by you)") + ", "
                        + (now - (Long) s[0]) + " ms after opening" + (win.pinned ? ", pinned" : ""));
                s[1] = win.c;
            }
        }
    }

    public static void after(GameUI gui) {
        long now = System.currentTimeMillis();
        for (Map.Entry<Window, Object[]> e : seen.entrySet()) {
            Window win = e.getKey();
            if (win == null || !win.attached()) {
                continue;
            }
            Coord was = (Coord) e.getValue()[1];
            if (!was.equals(win.c)) {
                log(name(win) + " placed " + was + " -> " + win.c + " by the window placer, "
                        + (now - (Long) e.getValue()[0]) + " ms after opening");
                e.getValue()[1] = win.c;
            }
        }
    }

    public static void consumed(Window win, Coord c) {
        if (!win.hasinv()) {
            return;
        }
        String who = "?";
        for (Widget ch = win.child; ch != null; ch = ch.next) {
            if (ch.visible && c.isect(win.xlate(ch.c, true), ch.sz)) {
                who = ch.getClass().getSimpleName() + " at " + ch.c + " size " + ch.sz;
            }
        }
        log(name(win) + ": title click at " + c + " went to " + who + " instead of starting a drag");
    }
}
