package haven;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class FayteLanding {
    public static final String KEY = "timerstart";
    private static final int GAP = 4;
    private static final List<Widget> stack = new ArrayList<>();
    private static final Map<Widget, Coord> assigned = new WeakHashMap<>();

    public static Coord anchor(GameUI gui) {
        return FayteHud.ghostpos(KEY, gui.sz);
    }

    public static <T extends Widget> T add(GameUI gui, T w) {
        if (!stack.contains(w)) {
            stack.add(w);
        }
        tick(gui);
        return w;
    }

    public static void tick(GameUI gui) {
        Coord a = anchor(gui);
        int y = a.y;
        for (int i = 0; i < stack.size(); i++) {
            Widget w = stack.get(i);
            Coord last = assigned.get(w);
            if (!w.attached() || (last != null && !last.equals(w.c))) {
                stack.remove(i--);
                assigned.remove(w);
                continue;
            }
            if (!w.visible) {
                continue;
            }
            Coord nc = new Coord(
                    Math.max(0, Math.min(a.x, gui.sz.x - w.sz.x)), Math.max(0, Math.min(y, gui.sz.y - w.sz.y)));
            if (!nc.equals(w.c)) {
                w.c = nc;
            }
            assigned.put(w, nc);
            y += w.sz.y + GAP;
        }
    }
}
