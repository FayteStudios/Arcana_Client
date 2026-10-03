package haven;

import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FayteOpenGlow {
    private static final Map<Long, Object[]> open = new HashMap<>();

    public static boolean on() {
        return FayteSkin.on() && FayteConfig.openGlow.get();
    }

    public static synchronized void opened(Window w) {
        GameUI gui = w.getparent(GameUI.class);
        if (gui == null || FayteTools.lastclickage() > 30000L || !FayteTools.lastwasworld()) {
            return;
        }
        Gob g = FayteTools.lastgob(gui);
        String rn = g == null ? null : FayteMsg.resname(g);
        if (rn == null || !rn.startsWith("gfx/terobjs/")) {
            return;
        }
        open.put(g.id, new Object[] {w, null});
    }

    public static synchronized ColoredRadius ring(Gob g) {
        if (!on() || open.isEmpty()) {
            return null;
        }
        Object[] e = open.get(g.id);
        if (e == null) {
            return null;
        }
        Window w = (Window) e[0];
        if (!w.linked()) {
            open.remove(g.id);
            return null;
        }
        if (!w.visible) {
            return null;
        }
        if (e[1] == null) {
            float rad;
            try {
                rad = FayteView.radius(g) + 4.0f;
            } catch (Loading l) {
                return null;
            }
            Color c = w.edgecolor();
            ColoredRadius.Cfg cfg = new ColoredRadius.Cfg();
            cfg.scol = String.format("50%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
            cfg.ecol = String.format("FF%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
            cfg.radius = rad;
            e[1] = new ColoredRadius(g, cfg, 10.0f);
        }
        return (ColoredRadius) e[1];
    }

    public static synchronized void sweep() {
        for (Iterator<Object[]> it = open.values().iterator(); it.hasNext(); ) {
            if (!((Window) it.next()[0]).linked()) {
                it.remove();
            }
        }
    }
}
