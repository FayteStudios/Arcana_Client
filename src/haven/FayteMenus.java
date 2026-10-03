package haven;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class FayteMenus {
    public static final String ARRANGE = "Arrange\u2026";
    public static final String GLOBAL = "*";
    private static Store store = null;
    public static String lastkey = null;
    public static String lastlabel = null;
    public static List<String> lastopts = new ArrayList<>();

    public static class Rule {
        public String label;
        public List<String> order = new ArrayList<>();
        public Set<String> auto = new LinkedHashSet<>();
        public Set<String> hide = new LinkedHashSet<>();
        public Set<String> seen = new LinkedHashSet<>();
    }

    public static class Store {
        public Rule global = new Rule();
        public Map<String, Rule> targets = new TreeMap<>();
    }

    public static boolean on() {
        return FayteModules.TOOLS.on();
    }

    private static File file() {
        return new File(FaytePaths.fayte(), "menus.json");
    }

    public static File datafile() {
        return file();
    }

    public static synchronized void reload() {
        store = null;
    }

    private static Gson gson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }

    public static synchronized Store store() {
        if (store == null) {
            File f = file();
            if (f.exists()) {
                try {
                    store = gson().fromJson(
                                    new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), Store.class);
                } catch (Exception e) {
                    FayteLog.log("Menus: could not read " + f + ": " + e);
                }
            }
            if (store == null) {
                store = new Store();
            }
            if (store.global == null) {
                store.global = new Rule();
            }
            if (store.targets == null) {
                store.targets = new TreeMap<>();
            }
            purge(store.global);
            for (Rule r : store.targets.values()) {
                purge(r);
            }
        }
        return store;
    }

    private static final String[] OURS = {
        "Inspect", "Almanac", ARRANGE, "Fill\u2026", "Arrange right-click options\u2026"
    };

    private static boolean ours(String n) {
        for (String o : OURS) {
            if (o.equals(n)) {
                return true;
            }
        }
        return false;
    }

    private static void purge(Rule r) {
        for (String o : OURS) {
            r.seen.remove(o);
            r.order.remove(o);
            r.auto.remove(o);
            r.hide.remove(o);
        }
    }

    public static synchronized void save() {
        try {
            File f = file();
            f.getParentFile().mkdirs();
            FaytePaths.write(f, gson().toJson(store()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.log("Menus: could not save: " + e);
        }
    }

    public static synchronized Rule rule(String key, boolean make) {
        if (key == null || key.equals(GLOBAL)) {
            return store().global;
        }
        Rule r = store().targets.get(key);
        if (r == null && make) {
            r = new Rule();
            store().targets.put(key, r);
        }
        return r;
    }

    private static int rank(String name, Rule t, Rule g, int orig) {
        if (t != null) {
            int i = t.order.indexOf(name);
            if (i >= 0) {
                return i;
            }
        }
        int j = g.order.indexOf(name);
        if (j >= 0) {
            return 1000 + j;
        }
        return 2000 + orig;
    }

    public static synchronized FlowerMenu.Petal[] arrange(FlowerMenu m, FlowerMenu.Petal[] opts, boolean noauto) {
        if (!on() || opts.length == 0) {
            return opts;
        }
        String[] tg = FayteTools.menutarget(m.ui.gui);
        lastkey = tg == null ? null : tg[0];
        lastlabel = tg == null ? null : tg[1];
        lastopts = new ArrayList<>();
        for (FlowerMenu.Petal p : opts) {
            lastopts.add(p.name);
        }
        Rule g = store().global;
        Rule t = lastkey == null ? null : rule(lastkey, false);
        boolean changed = false;
        for (FlowerMenu.Petal p : opts) {
            if (!ours(p.name)) {
                changed |= g.seen.add(p.name);
            }
        }
        if (lastkey != null && opts.length > 1) {
            Rule tt = rule(lastkey, true);
            if (tt.label == null && lastlabel != null) {
                tt.label = lastlabel;
                changed = true;
            }
            for (FlowerMenu.Petal p : opts) {
                if (!ours(p.name)) {
                    changed |= tt.seen.add(p.name);
                }
            }
            t = tt;
        }
        if (changed) {
            save();
        }
        List<FlowerMenu.Petal> keep = new ArrayList<>();
        for (int i = 0; i < opts.length; i++) {
            FlowerMenu.Petal p = opts[i];
            boolean hid = (t != null && t.hide.contains(p.name)) || g.hide.contains(p.name);
            if (hid) {
                p.hide();
            } else {
                keep.add(p);
            }
        }
        final Rule ft = t;
        final List<FlowerMenu.Petal> orig = Arrays.asList(opts);
        keep.sort(
                (a, b) -> Integer.compare(rank(a.name, ft, g, orig.indexOf(a)), rank(b.name, ft, g, orig.indexOf(b))));
        if (!noauto) {
            for (FlowerMenu.Petal p : keep) {
                if ((t != null && t.auto.contains(p.name)) || g.auto.contains(p.name)) {
                    m.autopick(p);
                    break;
                }
            }
        }
        if (keep.isEmpty()) {
            return opts;
        }
        return keep.toArray(new FlowerMenu.Petal[0]);
    }
}
