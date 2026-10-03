package haven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class FayteStash {
    private static Map<String, Map<String, Integer>> cache = null;
    private static String cachefor = null;
    private static long lastscan = 0L;

    private static String key() {
        String c = Config.currentCharName;
        return "fayte_stash_" + (c == null ? "" : c);
    }

    private static synchronized Map<String, Map<String, Integer>> cache() {
        String k = key();
        if (cache == null || !k.equals(cachefor)) {
            cachefor = k;
            cache = new TreeMap<>();
            for (String l : Utils.getpref(k, "").split("\n")) {
                String[] p = l.split("\t");
                if (p.length == 3) {
                    try {
                        cache.computeIfAbsent(p[0], x -> new TreeMap<>()).put(p[1], Integer.parseInt(p[2]));
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
        return cache;
    }

    private static synchronized void save() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Map<String, Integer>> c : cache().entrySet()) {
            for (Map.Entry<String, Integer> e : c.getValue().entrySet()) {
                sb.append(c.getKey())
                        .append('\t')
                        .append(e.getKey())
                        .append('\t')
                        .append(e.getValue())
                        .append('\n');
            }
        }
        Utils.setpref(key(), sb.toString());
    }

    private static String title(Inventory inv) {
        Window w = inv.getparent(Window.class);
        return w == null || w.cap == null ? null : w.cap.text.trim();
    }

    private static Inventory backpack(GameUI gui) {
        for (Inventory inv : FayteBagSel.inventories(gui)) {
            String t = title(inv);
            if (inv != gui.maininv && t != null && t.toLowerCase().contains("pack")) {
                return inv;
            }
        }
        return null;
    }

    private static boolean carried(GameUI gui, Inventory inv, Inventory pack) {
        if (inv == gui.maininv) {
            return false;
        }
        String t = title(inv);
        if (t == null) {
            return false;
        }
        if (inv == pack) {
            return true;
        }
        for (Inventory src : new Inventory[] {gui.maininv, pack}) {
            if (src == null) {
                continue;
            }
            for (WItem w : FayteXfer.items(src)) {
                if (t.equalsIgnoreCase(FayteSort.name(w))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void addcontents(WItem w, Map<String, Integer> into) {
        try {
            String ct = ItemInfo.getContent(w.item.info());
            if (ct == null) {
                return;
            }
            String[] p = ct.split(" ", 4);
            if (p.length == 4 && p[2].equalsIgnoreCase("of")) {
                int amt = (int) Math.floor(Double.parseDouble(p[0]));
                into.merge(p[3], amt, Integer::sum);
                into.merge(p[1] + " of " + p[3], amt, Integer::sum);
            } else {
                into.merge(ct, 1, Integer::sum);
            }
        } catch (Loading e) {
        } catch (RuntimeException e) {
            FayteLog.once("FayteStash.addcontents", e);
        }
    }

    public static Map<String, Integer> snapshot(Inventory inv) {
        Map<String, Integer> m = new TreeMap<>();
        for (WItem w : FayteXfer.items(inv)) {
            String n = FayteSort.name(w);
            if (n != null) {
                m.merge(n, 1, Integer::sum);
            }
            addcontents(w, m);
        }
        return m;
    }

    public static Map<String, Integer> bagcontents(GameUI gui) {
        Map<String, Integer> m = new TreeMap<>();
        if (gui.maininv != null) {
            for (WItem w : FayteXfer.items(gui.maininv)) {
                addcontents(w, m);
            }
        }
        return m;
    }

    public static void tick(GameUI gui) {
        long now = System.currentTimeMillis();
        if (now - lastscan < 1000L || gui.maininv == null) {
            return;
        }
        lastscan = now;
        Inventory pack = backpack(gui);
        Map<String, Map<String, Integer>> live = new HashMap<>();
        for (Inventory inv : FayteBagSel.inventories(gui)) {
            if (!carried(gui, inv, pack)) {
                continue;
            }
            Map<String, Integer> s = live.computeIfAbsent(title(inv), x -> new TreeMap<>());
            for (Map.Entry<String, Integer> e : snapshot(inv).entrySet()) {
                s.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
        boolean changed = false;
        synchronized (FayteStash.class) {
            for (Map.Entry<String, Map<String, Integer>> e : live.entrySet()) {
                if (!e.getValue().equals(cache().get(e.getKey()))) {
                    cache().put(e.getKey(), e.getValue());
                    changed = true;
                }
            }
        }
        if (changed) {
            save();
        }
    }

    public static synchronized int stored(List<String> names) {
        int n = 0;
        for (Map<String, Integer> c : cache().values()) {
            for (Map.Entry<String, Integer> e : c.entrySet()) {
                for (String want : names) {
                    if (FayteRecipes.same(want, e.getKey())) {
                        n += e.getValue();
                        break;
                    }
                }
            }
        }
        return n;
    }

    public static int inbag(GameUI gui, List<String> names) {
        int n = 0;
        for (Map.Entry<String, Integer> e : bagcontents(gui).entrySet()) {
            for (String want : names) {
                if (FayteRecipes.same(want, e.getKey())) {
                    n += e.getValue();
                    break;
                }
            }
        }
        return n;
    }

    public static List<String> names(String ingredient) {
        List<String> v = FayteRecipes.variants(ingredient);
        if (v.isEmpty()) {
            v = new ArrayList<>();
            v.add(ingredient);
        }
        return v;
    }
}
