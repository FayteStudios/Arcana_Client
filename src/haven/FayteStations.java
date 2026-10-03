package haven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class FayteStations {
    private static final String[] AUTO = {
        "whittl",
        "carpent",
        "loom",
        "grind",
        "gemcut",
        "gemwheel",
        "cottoncleaner",
        "cottongin",
        "churn",
        "anvil",
        "crucible"
    };
    private static final String[] PANEL = {
        "kiln",
        "oven",
        "stove",
        "fireplace",
        "smoker",
        "dframe",
        "rack",
        "ttub",
        "trough",
        "cauldron",
        "herbpot",
        "cask",
        "smelter",
        "furnace",
        "forge",
        "crucible",
        "coop",
        "churn"
    };
    private static final Map<String, List<String>> titles = new HashMap<>();
    private static final Map<Window, Boolean> done = new WeakHashMap<>();
    private static long pending = -1L;
    private static List<String> pendingst = null;
    private static long pendinguntil = 0L;

    private static void pendtick(GameUI gui) {
        if (pending < 0) {
            return;
        }
        if (System.currentTimeMillis() > pendinguntil || gui.map == null) {
            pending = -1L;
            return;
        }
        Gob b = gui.ui.sess.glob.oc.getgob(pending);
        Gob pl = gui.map.player();
        if (b == null || pl == null || b.rc == null || pl.rc == null) {
            return;
        }
        if (pl.rc.dist(b.rc) <= 22 && pl.getattr(Moving.class) == null) {
            long id = pending;
            pending = -1L;
            FayteBenchCard.open(gui, id, pendingst);
        }
    }

    public static boolean on() {
        return FayteModules.ALMANAC.on() && FayteConfig.stationRecipes.get();
    }

    public static List<String> stations(String rn) {
        synchronized (titles) {
            List<String> l = titles.get(rn);
            if (l != null) {
                return l;
            }
        }
        List<String> l = new ArrayList<>();
        boolean known = false;
        try {
            FayteWikiData.Entry e = FayteWorldNames.lookup(rn);
            known = e != null;
            if (e != null && e.title != null) {
                l.add(e.title);
            } else {
                String d = FayteWorldNames.display(rn);
                if (d != null) {
                    l.add(d);
                }
            }
        } catch (RuntimeException e) {
            FayteLog.once("FayteStations.stations", e);
        }
        if (rn.contains("carpent")) {
            l.add("Whittler's Bench");
        }

        if (known) {
            synchronized (titles) {
                titles.put(rn, l);
            }
        }
        return l;
    }

    public static boolean uses(String recipe, List<String> st) {
        for (String[] in : FayteRecipes.ingredients(recipe)) {
            for (String s : st) {
                if (in[0].trim().equalsIgnoreCase(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int count(GameUI gui, List<String> st) {
        int n = 0;
        for (FayteRecipes.Recipe r : FayteRecipes.list(gui)) {
            if (uses(r.name, st)) {
                n++;
            }
        }
        return n;
    }

    public static void clicked(GameUI gui, Gob g) {
        if (!on() || gui == null || g == null) {
            return;
        }
        String rn = FayteMsg.resname(g);
        if (rn == null || !rn.startsWith("gfx/terobjs/")) {
            return;
        }
        String low = rn.toLowerCase();
        boolean auto = false;
        for (String k : AUTO) {
            if (low.contains(k)) {
                auto = true;
                break;
            }
        }
        if (!auto) {
            return;
        }
        List<String> st = stations(rn);
        if (!st.isEmpty() && count(gui, st) > 0) {
            pending = g.id;
            pendingst = st;
            pendinguntil = System.currentTimeMillis() + 30000L;
        }
    }

    public static void tick(GameUI gui) {
        if (!FayteModules.ALMANAC.on()) {
            return;
        }
        pendtick(gui);

        for (Widget w = gui.child; w != null; w = w.next) {
            if (w.getClass() != Window.class
                    || !w.visible
                    || ((Window) w).cap == null
                    || w == gui.invwnd
                    || done.containsKey(w)) {
                continue;
            }
            Window win = (Window) w;
            done.put(win, true);
            Gob g = FayteTools.lastclickage() < 5000L ? FayteTools.lastgob(gui) : null;
            String rn = g == null ? null : FayteMsg.resname(g);
            if (rn == null || !rn.startsWith("gfx/terobjs/")) {
                continue;
            }
            boolean want = FayteGuide.find(rn) != null;
            String low = rn.toLowerCase();
            for (String k : PANEL) {
                if (low.contains(k)) {
                    want = true;
                }
            }
            if (low.contains("winerack")) {
                want = false;
            }
            if (want) {
                FayteStationPanel.attach(gui, win, rn, g.id);
            }
            if (rn.contains("compost")) {
                new FayteFooter(win);
            }
        }
    }
}
