package haven;

import java.util.HashMap;
import java.util.Map;

public class FayteAuto {
    private static final long MIN = 60L * 1000L;
    private static final long HOUR = 60L * MIN;
    private static final Object[][] LIT = {
        {
            "cement",
            "Cementation Furnace",
            new Object[][] {
                {"check fuel", 55 * HOUR},
                {"coffins done, take them out within 4 h", 168 * HOUR},
                {"LAST HOUR to take coffins out", 171 * HOUR}
            }
        },
        {"oresmelter", "Ore Smelter", new Object[][] {{"bars ready", 40 * MIN}}},
        {"wildsmelter", "Wilderness Smelter", new Object[][] {{"break it open", HOUR}}},
        {"wildernesssmelter", "Wilderness Smelter", new Object[][] {{"break it open", HOUR}}},
        {"bogsmelter", "Wilderness Smelter", new Object[][] {{"break it open", HOUR}}},
        {"clamp", "Coal Clamp", new Object[][] {{"charcoal ready", 56 * HOUR}}},
        {"smoker", "Meatsmoker", new Object[][] {{"smoked meat ready", 8 * HOUR}}},
    };
    private static final Object[][] SEAL = {
        {
            "cask",
            "Cask",
            "seal",
            new Object[][] {{"wine ready, pop the seal", 2 * HOUR}, {"wine turns to vinegar soon", 7 * 24 * HOUR}}
        },
    };
    private static final Map<Long, Boolean> lit = new HashMap<>();
    private static long last = 0L;

    public static boolean on() {
        return FayteModules.TIMERS.on() && FayteConfig.autoTimers.get();
    }

    private static final String TOKE = "gfx/borka/tokestart";
    private static final long HOOKAH = 20L * 3600L * 1000L;

    private static final long TOKEMIN = 12000L;
    private static final long TOKEMAX = 15000L;
    private static long tokeat = 0L;

    public static void posed(GameUI gui, String poses) {
        if (!on()) {
            return;
        }
        if (poses.contains(TOKE)) {
            if (tokeat == 0L) {
                tokeat = System.currentTimeMillis();
            }
            return;
        }
        if (tokeat == 0L || poses.isEmpty()) {
            return;
        }
        long held = System.currentTimeMillis() - tokeat;
        tokeat = 0L;
        String obj = FayteTools.lastobject(gui);
        boolean hookah = obj != null && obj.toLowerCase().contains("hookah") && FayteTools.lastclickage() < 600000L;
        if (hookah && held >= TOKEMIN && held <= TOKEMAX) {
            FayteTimers.restart("Hookah", HOOKAH);
            gui.message("Hookah timer started: 20 hours until your next puff.", GameUI.MsgType.INFO);
        }
    }

    private static boolean burning(Gob g) {
        synchronized (g.ols) {
            for (Gob.Overlay ol : g.ols) {
                try {
                    Resource r = ol.res.get();
                    if (r != null && (r.name.contains("ismoke") || r.name.startsWith("sfx/terobjs/"))) {
                        return true;
                    }
                } catch (Loading e) {
                }
            }
        }
        return false;
    }

    private static String place(Gob g, String title) {
        String n = FayteLabels.get("gob:" + g.id);
        return n != null ? n : title + " #" + (g.id % 1000);
    }

    private static void start(Gob g, String title, Object[][] timers) {
        String base = place(g, title);
        for (Object[] t : timers) {
            FayteTimers.restart(base + ": " + t[0], (Long) t[1], false);
        }
        FayteMsg.say(base + ": " + timers.length + (timers.length == 1 ? " timer" : " timers") + " started.");
    }

    public static void tick(GameUI gui) {
        long now = System.currentTimeMillis();
        if (!on() || gui.map == null || now - last < 1000L) {
            return;
        }
        last = now;
        Gob pl = gui.map.player();
        if (pl == null || pl.rc == null) {
            return;
        }

        synchronized (gui.ui.sess.glob.oc) {
            for (Gob g : gui.ui.sess.glob.oc) {
                if (g.rc == null || g.rc.dist(pl.rc) > 15 * 11) {
                    continue;
                }
                String rn = FayteMsg.resname(g);
                if (rn == null || !rn.startsWith("gfx/terobjs/")) {
                    continue;
                }
                Object[] rule = null;
                for (Object[] r : LIT) {
                    if (rn.contains((String) r[0])) {
                        rule = r;
                        break;
                    }
                }
                if (rule == null) {
                    continue;
                }
                boolean b = burning(g);
                Boolean was = lit.get(g.id);
                lit.put(g.id, b);
                if (was != null && !was && b && g.rc.dist(pl.rc) < 4 * 11) {
                    start(g, (String) rule[1], (Object[][]) rule[2]);
                }
            }
        }
        if (lit.size() > 5000) {
            lit.clear();
        }
    }

    public static void flower(GameUI gui, String option) {
        if (!on() || gui == null || option == null || FayteTools.lastclickage() > 30000L) {
            return;
        }
        Gob g = FayteTools.lastgob(gui);
        String rn = g == null ? null : FayteMsg.resname(g);
        if (rn == null) {
            return;
        }
        for (Object[] r : SEAL) {
            if (rn.contains((String) r[0]) && option.trim().equalsIgnoreCase((String) r[2])) {
                start(g, (String) r[1], (Object[][]) r[3]);
                return;
            }
        }
    }
}
