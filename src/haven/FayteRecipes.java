package haven;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FayteRecipes {
    private static final String CRAFTROOT = "paginae/act/craft";
    private static List<Recipe> cache = new ArrayList<>();
    private static int cacheseq = -1;
    private static Map<String, List<String>> skills = null;
    private static Recipe crafting = null;
    private static int left = 0;
    private static int stage = 0;
    private static long since = 0;
    private static int made = 0;
    private static int lastprog = 0;
    private static Makewindow hidden = null;
    private static final Map<String, String> choices = new HashMap<>();
    private static boolean choicesloaded = false;
    private static final Set<String> asidenames = new HashSet<>();
    private static final Map<Inventory, Set<GItem>> asidesnap = new WeakHashMap<>();
    private static int asidecount = 0;

    public static class Recipe {
        public String name;
        public Glob.Pagina pag;
        public String res;
        public List<String> path = new ArrayList<>();

        public String top() {
            return path.isEmpty() ? "Other" : path.get(0);
        }
    }

    public static synchronized List<Recipe> list(GameUI gui) {
        Glob glob = gui.ui.sess.glob;
        if (gui.menu == null) {
            return cache;
        }
        if (glob.pagseq == cacheseq && !cache.isEmpty()) {
            return cache;
        }
        List<Glob.Pagina> pags;
        synchronized (glob.paginae) {
            pags = new ArrayList<>(glob.paginae);
        }
        List<Recipe> ret = new ArrayList<>();
        boolean complete = true;
        for (Glob.Pagina p : pags) {
            try {
                Resource r = p.res();
                if (r == null || p == gui.menu.CRAFT || !gui.menu.isCrafting(p)) {
                    continue;
                }
                Resource.AButton act = r.layer(Resource.action);
                if (act == null || act.ad == null || act.ad.length == 0) {
                    continue;
                }
                Recipe rec = new Recipe();
                rec.name = act.name;
                rec.pag = p;
                rec.res = r.name;
                Resource pr = act.parent;
                for (int i = 0; pr != null && i < 8 && !pr.name.equals(CRAFTROOT); i++) {
                    Resource.AButton pa = pr.layer(Resource.action);
                    if (pa == null) {
                        break;
                    }
                    rec.path.add(0, pa.name);
                    pr = pa.parent;
                }
                ret.add(rec);
            } catch (Loading e) {
                complete = false;
            }
        }
        Collections.sort(ret, (a, b) -> a.name.compareToIgnoreCase(b.name));
        cache = ret;
        if (complete) {
            cacheseq = glob.pagseq;
        }
        return ret;
    }

    private static List<Recipe> bcache = new ArrayList<>();
    private static int bcacheseq = -1;

    public static synchronized List<Recipe> builds(GameUI gui) {
        Glob glob = gui.ui.sess.glob;
        if (glob.pagseq == bcacheseq && !bcache.isEmpty()) {
            return bcache;
        }
        List<Glob.Pagina> pags;
        synchronized (glob.paginae) {
            pags = new ArrayList<>(glob.paginae);
        }
        List<Recipe> ret = new ArrayList<>();
        boolean complete = true;
        for (Glob.Pagina p : pags) {
            try {
                Resource r = p.res();
                if (r == null) {
                    continue;
                }
                Resource.AButton act = r.layer(Resource.action);
                if (act == null || act.ad == null || act.ad.length == 0) {
                    continue;
                }
                String par = act.parent == null ? "" : act.parent.name;
                if (!(r.name.startsWith("paginae/bld/") || par.equals("paginae/act/bld"))
                        || r.name.equals("paginae/bld/claim")
                        || par.equals("paginae/bld/wasteclaims")) {
                    continue;
                }
                Recipe rec = new Recipe();
                rec.name = act.name;
                rec.pag = p;
                rec.res = r.name;
                if (!par.equals("paginae/act/bld") && act.parent != null) {
                    Resource.AButton pa = act.parent.layer(Resource.action);
                    if (pa != null) {
                        rec.path.add(pa.name);
                    }
                } else {
                    rec.path.add("General");
                }
                ret.add(rec);
            } catch (Loading e) {
                complete = false;
            }
        }
        Collections.sort(ret, (a, b) -> a.name.compareToIgnoreCase(b.name));
        bcache = ret;
        if (complete) {
            bcacheseq = glob.pagseq;
        }
        return ret;
    }

    private static Set<String> favs = null;

    private static File favfile() {
        return new File(FaytePaths.fayte(), "favorites.txt");
    }

    public static File favdatafile() {
        return favfile();
    }

    public static File choicedatafile() {
        return choicefile();
    }

    public static synchronized void reloadprefs() {
        favs = null;
        choices.clear();
        choicesloaded = false;
    }

    public static synchronized boolean fav(String res) {
        if (favs == null) {
            favs = new HashSet<>();
            File f = favfile();
            if (f.exists()) {
                try {
                    for (String l : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                        if (!l.trim().isEmpty()) {
                            favs.add(l.trim());
                        }
                    }
                } catch (Exception e) {
                    FayteLog.log("Favorites: could not read " + f + ": " + e);
                }
            }
        }
        return favs.contains(res);
    }

    public static synchronized void togglefav(String res) {
        fav(res);
        if (!favs.remove(res)) {
            favs.add(res);
        }
        try {
            favfile().getParentFile().mkdirs();
            FaytePaths.write(favfile(), String.join("\n", favs).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.log("Favorites: could not write: " + e);
        }
    }

    private static boolean buildwait = false;
    private static boolean buildseen = false;
    private static long buildat = 0L;
    private static Window buildhidden = null;

    public static void build(GameUI gui, Recipe r, Window hide) {
        if (gui == null || gui.menu == null || r == null) {
            return;
        }
        gui.menu.use(r.pag);
        MapView.placesent = false;
        buildwait = true;
        buildseen = false;
        buildat = System.currentTimeMillis();
        buildhidden = hide;
        if (hide != null) {
            hide.hide();
        }
    }

    private static void buildtick(GameUI gui) {
        if (!buildwait) {
            return;
        }
        boolean placing = gui.map != null && gui.map.placing();
        long el = System.currentTimeMillis() - buildat;
        if (placing) {
            buildseen = true;
        }
        boolean done = buildseen && !placing;
        if (done || (!buildseen && el > 2000L)) {
            buildwait = false;
            if (buildhidden != null && buildhidden.linked()) {
                buildhidden.show();
                buildhidden.raise();
            }
            buildhidden = null;
        }
    }

    public static List<String> categories(GameUI gui) {
        TreeSet<String> s = new TreeSet<>();
        for (Recipe r : list(gui)) {
            s.add(r.top());
        }
        return new ArrayList<>(s);
    }

    private static final Map<String, Object[]> ingrcache = new HashMap<>();

    public static List<String[]> ingredients(String recipe) {
        FayteWikiData.Entry e = FayteWikiData.find(recipe);
        synchronized (ingrcache) {
            Object[] c = ingrcache.get(recipe);
            if (c != null && c[0] == e) {
                @SuppressWarnings("unchecked")
                List<String[]> l = (List<String[]>) c[1];
                return l;
            }
        }
        List<String[]> ret = parseingr(e);
        synchronized (ingrcache) {
            ingrcache.put(recipe, new Object[] {e, ret});
        }
        return ret;
    }

    private static List<String[]> parseingr(FayteWikiData.Entry e) {
        List<String[]> ret = new ArrayList<>();
        if (e == null) {
            return ret;
        }
        for (Map.Entry<String, Map<String, String>> t : e.tpl.entrySet()) {
            String tn = t.getKey().split("#")[0].trim();
            if (!tn.equalsIgnoreCase("Crafted") && !tn.equalsIgnoreCase("Structure")) {
                continue;
            }
            String v = t.getValue().get("Objects required");
            if (v == null) {
                continue;
            }
            for (String part : FayteMarkup.plain(v).split(",")) {
                String p = part.trim();
                if (p.isEmpty()) {
                    continue;
                }
                int semi = p.lastIndexOf(';');
                String n = semi > 0 ? p.substring(0, semi).trim() : p;
                String c = semi > 0 ? p.substring(semi + 1).trim() : "1";
                ret.add(new String[] {n, c});
            }
        }
        if (ret.isEmpty()) {
            needs(e, ret);
        }
        return ret;
    }

    private static final Pattern NEED = Pattern.compile(
            "\ue001([^\ue002]+)\ue002[^\ue003]*\ue003\\s*(?:\u00d7\\s*(\\d+)|([\\d.]+\\s*[A-Za-z]+\\b))?");

    private static void needs(FayteWikiData.Entry e, List<String[]> ret) {
        for (Map.Entry<String, Map<String, String>> t : e.tpl.entrySet()) {
            String tn = t.getKey().split("#")[0].trim();
            String title = t.getValue().get("Title");
            String v = t.getValue().get("Needs");
            if (!tn.equalsIgnoreCase("Facts") || v == null || title == null) {
                continue;
            }
            String tt = title.trim().toLowerCase();
            if (!tt.equals("make")
                    && !tt.equals("cook")
                    && !tt.equals("bake")
                    && !tt.equals("craft")
                    && !tt.equals("build")) {
                continue;
            }
            Matcher m = NEED.matcher(v);
            while (m.find()) {
                String n = m.group(1).trim();
                if (n.equalsIgnoreCase("Fire") || isstation(n)) {
                    continue;
                }
                String c = m.group(2) != null
                        ? m.group(2)
                        : m.group(3) != null ? m.group(3).trim() : "1";
                ret.add(new String[] {n, c});
            }
            if (!ret.isEmpty()) {
                return;
            }
        }
    }

    private static final Set<String> GROUPS = new HashSet<>(Arrays.asList(
            "fibre",
            "flour",
            "poison items",
            "flowers & herbs",
            "cornmeal",
            "bar of any metal",
            "corn oil",
            "vegetable oil",
            "poultry broth ingredients",
            "meat broth ingredients",
            "wine ingredients",
            "pumpkin"));

    public static boolean same(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String x = FayteWikiData.squash(a);
        String y = FayteWikiData.squash(b);
        return x.equals(y) || x.equals(y + "s") || y.equals(x + "s") || x.equals(y + "es") || y.equals(x + "es");
    }

    public static boolean isstation(String n) {
        FayteWikiData.Entry e = n == null ? null : FayteWikiData.find(n);
        if (e == null || e.tpl == null) {
            return false;
        }
        for (String k : e.tpl.keySet()) {
            if (k.split("#")[0].trim().equalsIgnoreCase("Structure")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isgroup(String n) {
        return n != null
                && (n.toLowerCase().startsWith("any ")
                        || GROUPS.contains(n.trim().toLowerCase()));
    }

    public static List<String> variants(String ingredient) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        expand(ingredient, out, 0);
        return new ArrayList<>(out);
    }

    private static void expand(String ingredient, Set<String> out, int depth) {
        if (!isgroup(ingredient) || depth > 3) {
            return;
        }
        for (String[] m : ingredients(ingredient)) {
            if (isgroup(m[0])) {
                expand(m[0], out, depth + 1);
            } else if (depth < 3 && kinds(m[0])) {
                out.add(m[0]);
                for (String[] k : ingredients(m[0])) {
                    out.add(k[0]);
                }
            } else {
                out.add(m[0]);
            }
        }
    }

    private static boolean kinds(String n) {
        List<String[]> l = ingredients(n);
        if (l.size() < 2) {
            return false;
        }
        String w = n.trim().toLowerCase();
        for (String[] k : l) {
            if (!k[1].trim().equals("1") || !k[0].toLowerCase().contains(w)) {
                return false;
            }
        }
        return true;
    }

    private static File choicefile() {
        return new File(FaytePaths.fayte(), "recipe_choices.tsv");
    }

    private static synchronized void loadchoices() {
        if (choicesloaded) {
            return;
        }
        choicesloaded = true;
        File f = choicefile();
        if (!f.exists()) {
            return;
        }
        try {
            for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                String[] p = line.split("\t");
                if (p.length == 3) {
                    choices.put(p[0] + "|" + p[1], p[2].trim());
                }
            }
        } catch (Exception e) {
            FayteLog.log("Recipes: could not read " + f + ": " + e);
        }
    }

    public static synchronized String choice(String recipe, String ingredient) {
        loadchoices();
        return choices.get(recipe + "|" + ingredient);
    }

    public static synchronized void setchoice(String recipe, String ingredient, String variant) {
        loadchoices();
        if (variant == null) {
            choices.remove(recipe + "|" + ingredient);
        } else {
            choices.put(recipe + "|" + ingredient, variant);
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : choices.entrySet()) {
            int bar = e.getKey().indexOf('|');
            b.append(e.getKey(), 0, bar)
                    .append('\t')
                    .append(e.getKey().substring(bar + 1))
                    .append('\t')
                    .append(e.getValue())
                    .append('\n');
        }
        try {
            choicefile().getParentFile().mkdirs();
            FaytePaths.write(choicefile(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.log("Recipes: could not write choices: " + e);
        }
    }

    public static boolean excluded(String recipe, WItem w, String name) {
        if (FayteBagSel.excluded(w, name)) {
            return true;
        }
        for (String[] in : ingredients(recipe)) {
            String ch = choice(recipe, in[0]);
            if (ch != null && !ch.equalsIgnoreCase(name)) {
                for (String v : variants(in[0])) {
                    if (same(v, name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean restricted(String recipe, String name) {
        if (FayteBagSel.selnames().contains(name)) {
            return true;
        }
        for (String[] in : ingredients(recipe)) {
            String ch = choice(recipe, in[0]);
            if (ch != null && !ch.equalsIgnoreCase(name)) {
                for (String v : variants(in[0])) {
                    if (same(v, name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static List<String> contents(WItem w) {
        List<String> ret = new ArrayList<>();
        try {
            for (ItemInfo ii : w.item.info()) {
                if (ii instanceof ItemInfo.Contents) {
                    for (ItemInfo sub : ((ItemInfo.Contents) ii).sub) {
                        if (sub instanceof ItemInfo.Name) {
                            ret.add(((ItemInfo.Name) sub).str.text);
                        } else if (sub instanceof ItemInfo.AdHoc) {
                            ret.add(((ItemInfo.AdHoc) sub).str.text);
                        }
                    }
                }
            }
        } catch (Loading e) {
        }
        return ret;
    }

    private static Set<String> restrictednames(String recipe) {
        Set<String> r = new HashSet<>();
        for (String n : FayteBagSel.selnames()) {
            r.add(n.toLowerCase());
        }
        for (String[] in : ingredients(recipe)) {
            String ch = choice(recipe, in[0]);
            if (ch != null) {
                for (String v : variants(in[0])) {
                    if (!v.equalsIgnoreCase(ch)) {
                        r.add(v.toLowerCase());
                    }
                }
            }
        }
        return r;
    }

    public static boolean holdsrestricted(String recipe, WItem w) {
        List<String> c = contents(w);
        if (c.isEmpty()) {
            return false;
        }
        Set<String> r = restrictednames(recipe);
        for (String line : c) {
            String l = line.toLowerCase();
            for (String n : r) {
                if (l.contains(n)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static class Have {
        public int count;
        public double puritysum;
        public int puritycount;
        public int aside;

        public String purity() {
            return puritycount == 0 ? null : String.format("%.1f%%", 100 * puritysum / puritycount);
        }
    }

    public static Have have(GameUI gui, String recipe, String ingredient) {
        Have h = new Have();
        if (gui.maininv == null) {
            return h;
        }
        List<String> names = variants(ingredient);
        if (names.isEmpty()) {
            names = Collections.singletonList(ingredient);
        }
        for (WItem w : FayteXfer.items(gui.maininv)) {
            String n = FayteSort.name(w);
            boolean match = false;
            for (String v : names) {
                if (same(v, n)) {
                    match = true;
                }
            }
            if (!match) {
                continue;
            }
            if (excluded(recipe, w, n)) {
                h.aside++;
                continue;
            }
            h.count++;
            double pu = FayteSort.purity(w);
            if (pu >= 0) {
                h.puritysum += pu;
                h.puritycount++;
            }
        }
        return h;
    }

    public static synchronized Map<String, List<String>> skills() {
        if (skills != null) {
            return skills;
        }
        FayteWikiData.Store st = FayteWikiData.get();
        if (st == null || st.entries() == null) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> m = new HashMap<>();
        for (FayteWikiData.Entry e : st.entries().values()) {
            for (Map.Entry<String, Map<String, String>> t : e.tpl.entrySet()) {
                if (!t.getKey().split("#")[0].trim().equalsIgnoreCase("Skill")) {
                    continue;
                }
                String v = t.getValue().get("Crafts unlocked");
                if (v == null) {
                    continue;
                }
                for (String part : FayteMarkup.plain(v).split(",")) {
                    String p = part.trim().toLowerCase();
                    if (!p.isEmpty()) {
                        m.computeIfAbsent(p, k -> new ArrayList<>()).add(e.title);
                    }
                }
            }
        }
        skills = m;
        return m;
    }

    public static List<String> skillsfor(String recipe) {
        List<String> l = skills().get(recipe.toLowerCase());
        return l == null ? Collections.emptyList() : l;
    }

    public static List<String> allskills(GameUI gui) {
        TreeSet<String> s = new TreeSet<>();
        for (Recipe r : list(gui)) {
            s.addAll(skillsfor(r.name));
        }
        return new ArrayList<>(s);
    }

    public static boolean busy() {
        return crafting != null;
    }

    public static Recipe previewing = null;

    static void reset() {
        craftwin = null;
        progend = 0L;
        crafting = null;
        previewing = null;
        stage = 0;
        hidden = null;
        buildwait = false;
        buildhidden = null;
        graceuntil = 0L;
        asidesnap.clear();
        cacheseq = -1;
        bcacheseq = -1;
        pulled = 0;
        openedpack = false;
    }

    private static long graceuntil = 0L;

    private static String gracename = null;

    public static boolean quiet(String recipe) {
        if (crafting != null || previewing != null) {
            return true;
        }
        return System.currentTimeMillis() < graceuntil
                && gracename != null
                && recipe != null
                && named(recipe, gracename);
    }

    public static boolean unwanted() {
        return crafting == null && previewing == null;
    }

    public static void preview(GameUI gui, Recipe r) {
        if (gui == null || gui.menu == null || r == null || crafting != null) {
            return;
        }
        if (previewing != null && previewing.res.equals(r.res)) {
            return;
        }
        previewing = r;
        gui.menu.senduse(r.pag);
    }

    public static void endpreview(GameUI gui) {
        if (previewing == null) {
            return;
        }
        gracename = previewing.name;
        previewing = null;
        graceuntil = System.currentTimeMillis() + 4000L;
        if (crafting == null && gui != null) {
            Makewindow mw = find(gui, null);
            if (mw != null) {
                Window w = mw.getparent(Window.class);
                if (w != null && !w.visible) {
                    mw.wdgmsg("close");
                }
            }
        }
    }

    public static String status() {
        if (crafting == null) {
            return null;
        }
        return "Crafting " + crafting.name + ": " + made + " done, " + left + " to go";
    }

    public static void opengame(GameUI gui, Recipe r) {
        if (gui == null || gui.menu == null || r == null) {
            return;
        }
        stop(null);
        endpreview(gui);
        graceuntil = 0L;
        gracename = null;
        gui.menu.use(r.pag);
    }

    public static void craft(GameUI gui, Recipe r, int n) {
        if (gui == null || gui.menu == null || r == null || n < 1) {
            return;
        }
        crafting = r;
        left = n;
        made = 0;
        since = System.currentTimeMillis();
        asidenames.clear();
        asidesnap.clear();
        asidecount = 0;
        openedpack = false;
        pull(gui, r);
        stage = 10;
    }

    private static boolean openedpack = false;

    private static Set<String> wanted(Recipe r) {
        Set<String> w = new HashSet<>();
        for (String[] in : ingredients(r.name)) {
            w.add(FayteWikiData.squash(in[0]));
            for (String v : variants(in[0])) {
                w.add(FayteWikiData.squash(v));
            }
        }
        return w;
    }

    private static int pulled = 0;

    private static void pull(GameUI gui, Recipe r) {
        Set<String> w = wanted(r);
        int n = 0;
        for (Inventory inv : FayteBagSel.inventories(gui)) {
            if (inv == gui.maininv) {
                continue;
            }
            for (WItem it : FayteXfer.items(inv)) {
                if (FayteBagSel.selected(it) && w.contains(FayteWikiData.squash(FayteSort.name(it)))) {
                    it.item.wdgmsg("transfer", Coord.z);
                    n++;
                }
            }
        }
        pulled = n;
        if (n > 0) {
            FayteMsg.say("Moving " + n + " selected item" + (n == 1 ? "" : "s") + " into your inventory for crafting.");
        }
    }

    private static int setaside(GameUI gui, Recipe r) {
        if (gui.maininv == null) {
            return 0;
        }
        List<WItem> out = new ArrayList<>();
        for (WItem w : FayteXfer.items(gui.maininv)) {
            String n = FayteSort.name(w);
            if (excluded(r.name, w, n) || holdsrestricted(r.name, w)) {
                out.add(w);
                asidenames.add(n);
            }
        }
        if (out.isEmpty()) {
            return 0;
        }
        List<Inventory> others = new ArrayList<>();
        for (Inventory inv : FayteBagSel.inventories(gui)) {
            if (inv != gui.maininv) {
                others.add(inv);
            }
        }
        if (others.isEmpty()) {
            asidenames.clear();
            if (!openedpack) {
                openedpack = true;
                since = System.currentTimeMillis();
                FayteXfer.invpack(gui);
            }
            return -1;
        }
        for (Inventory inv : others) {
            Set<GItem> snap = Collections.newSetFromMap(new WeakHashMap<>());
            for (WItem w : FayteXfer.items(inv)) {
                snap.add(w.item);
            }
            asidesnap.put(inv, snap);
        }
        for (WItem w : out) {
            w.item.wdgmsg("transfer", Coord.z);
        }
        asidecount = out.size();
        FayteMsg.say("Setting aside " + out.size() + " " + String.join(", ", asidenames) + " while crafting.");
        return 1;
    }

    private static void restore() {
        if (asidenames.isEmpty()) {
            return;
        }
        int n = 0;
        for (Map.Entry<Inventory, Set<GItem>> e : asidesnap.entrySet()) {
            Inventory inv = e.getKey();
            if (inv == null || !inv.linked()) {
                continue;
            }
            for (WItem w : FayteXfer.items(inv)) {
                if (!e.getValue().contains(w.item) && asidenames.contains(FayteSort.name(w))) {
                    w.item.wdgmsg("transfer", Coord.z);
                    n++;
                }
            }
        }
        if (n < asidecount) {
            FayteMsg.say("Put back " + n + " of " + asidecount
                    + " set-aside items; check your open containers for the rest.");
        }
        asidenames.clear();
        asidesnap.clear();
        asidecount = 0;
    }

    public static void stop(String why) {
        if (crafting != null && why != null) {
            FayteMsg.say(why);
            FayteLog.log("Crafting: " + why + " (stage " + stage + ", made " + made + ")");
        }
        if (crafting != null && hidden != null && hidden.linked() && previewing == null) {
            hidden.wdgmsg("close");
        }
        hidden = null;
        craftwin = null;
        progend = 0L;
        restore();
        crafting = null;
        stage = 0;
    }

    private static boolean named(String recipe, String name) {
        if (recipe == null) {
            return false;
        }
        if (name.equals(recipe) || same(name, recipe)) {
            return true;
        }
        String a = FayteWikiData.squash(recipe);
        String b = FayteWikiData.squash(name);
        return !b.isEmpty() && (a.endsWith(b) || b.endsWith(a));
    }

    private static long namelog = 0L;

    private static void allnames(Widget w, StringBuilder sb) {
        for (Widget c = w.child; c != null; c = c.next) {
            if (c instanceof Makewindow) {
                Window wnd = c.getparent(Window.class);
                sb.append(sb.length() > 0 ? ", " : "")
                        .append('"')
                        .append(((Makewindow) c).recipe)
                        .append('"')
                        .append(wnd != null && !wnd.visible ? " (hidden)" : "");
            }
            allnames(c, sb);
        }
    }

    private static Makewindow find(Widget w, String name) {
        for (Widget c = w.child; c != null; c = c.next) {
            if (c instanceof Makewindow && (name == null || named(((Makewindow) c).recipe, name))) {
                return (Makewindow) c;
            }
            Makewindow m = find(c, name);
            if (m != null) {
                return m;
            }
        }
        return null;
    }

    private static long lastsweep = 0L;
    private static long madesig = 0L;
    private static long progend = 0L;
    private static Makewindow craftwin = null;

    private static long invsig(GameUI gui) {
        long n = 0L;
        if (gui.maininv != null && crafting != null) {
            String base = crafting.res == null ? null : crafting.res.substring(crafting.res.lastIndexOf('/') + 1);
            for (WItem w : FayteXfer.items(gui.maininv)) {
                String rn = null;
                try {
                    rn = w.item.res.get().name;
                } catch (Loading e) {
                }
                String nm = FayteSort.name(w);
                if (same(crafting.name, nm)
                        || (base != null
                                && rn != null
                                && rn.substring(rn.lastIndexOf('/') + 1).equals(base))
                        || (nm != null && FayteWikiData.squash(nm).endsWith(FayteWikiData.squash(crafting.name)))) {
                    n++;
                }
            }
        }
        return n;
    }

    private static void sweephidden(GameUI gui) {
        long now = System.currentTimeMillis();
        if (now - lastsweep < 1000L || now < graceuntil) {
            return;
        }
        lastsweep = now;
        for (Widget w = gui.child; w != null; w = w.next) {
            if (w instanceof Window && !w.visible && find(w, null) != null) {
                Makewindow mw = find(w, null);
                if (previewing != null && previewing.name != null && named(mw.recipe, previewing.name)) {
                    continue;
                }
                mw.wdgmsg("close");
            }
        }
    }

    public static void tick(GameUI gui) {
        buildtick(gui);
        if (crafting == null && previewing != null && !FayteAlmanacWnd.recipeslive() && !FayteBenchCard.isopen()) {
            endpreview(gui);
        }
        if (crafting == null && previewing == null && !FayteAlmanacWnd.recipeslive() && !FayteBenchCard.isopen()) {
            sweephidden(gui);
        }
        if (crafting == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Makewindow mw = find(gui, crafting.name);
        if (mw == null && stage >= 2 && craftwin != null && craftwin.attached()) {
            mw = craftwin;
        }
        if (mw == null && stage == 1 && now - since > 1500) {
            mw = find(gui, null);
            if (stage == 1 && FayteConfig.diagnostics.get() && now - namelog > 5000) {
                namelog = now;
                StringBuilder sb = new StringBuilder();
                allnames(gui, sb);
                FayteLog.log("Crafting: no window named " + crafting.name + " after 1.5 s (pagina " + crafting.res
                        + "); crafting windows open: " + sb);
            }
        }
        if (mw != null && hidden == null) {
            Window w = mw.getparent(Window.class);
            if (w != null && !w.visible) {
                hidden = mw;
            }
        }
        switch (stage) {
            case 10:
                if (pulled > 0 && now - since < 800) {
                    break;
                }
                int sa = setaside(gui, crafting);
                if (sa < 0) {
                    if (now - since > 3000) {
                        stop("Could not open your backpack or another container to set the unwanted ingredients"
                                + " aside.");
                    }
                } else if (sa == 0 && pulled == 0) {
                    stage = 1;
                    since = now;
                    if (find(gui, crafting.name) == null) {
                        gui.menu.senduse(crafting.pag);
                    }
                } else {
                    stage = 0;
                    since = now;
                }
                break;
            case 0:
                boolean still = false;
                if (gui.maininv != null) {
                    for (WItem w : FayteXfer.items(gui.maininv)) {
                        if (excluded(crafting.name, w, FayteSort.name(w)) || holdsrestricted(crafting.name, w)) {
                            still = true;
                        }
                    }
                }
                if ((!still && now - since > 500) || now - since > 3000) {
                    stage = 1;
                    since = now;
                    gui.menu.senduse(crafting.pag);
                }
                break;
            case 1:
                if (mw != null) {
                    craftwin = mw;
                    madesig = invsig(gui);
                    if (FayteConfig.diagnostics.get()) {
                        FayteLog.log("Crafting: make " + crafting.name + " in window \"" + mw.recipe
                                + "\", matching items in bag " + madesig);
                    }
                    mw.wdgmsg("make", 0);
                    stage = 2;
                    since = now;
                } else if (now - since > 4000) {
                    stop("Could not open the recipe for " + crafting.name);
                }
                break;
            case 2:
                if (gui.prog >= 0) {
                    stage = 3;
                    lastprog = gui.prog;
                } else if (now - since > 800 && invsig(gui) > madesig) {
                    made++;
                    left--;
                    if (left <= 0) {
                        stop("Crafted " + made + " " + crafting.name);
                    } else if (mw != null) {
                        madesig = invsig(gui);
                        mw.wdgmsg("make", 0);
                        since = now;
                    } else {
                        stop("Stopped after " + made + ": the crafting window closed");
                    }
                } else if (now - since > 3000) {
                    if (FayteConfig.diagnostics.get()) {
                        FayteLog.log("Crafting: no progress bar and no new " + crafting.name + " (bag count "
                                + invsig(gui) + ", was " + madesig + ", window "
                                + (mw == null ? "gone" : "\"" + mw.recipe + "\"") + ")");
                    }
                    stop(
                            made == 0
                                    ? "Could not craft " + crafting.name + ": missing ingredients or tools?"
                                    : "Stopped after " + made + " " + crafting.name);
                }
                break;
            case 3:
                if (gui.prog >= 0) {
                    lastprog = Math.max(lastprog, gui.prog);
                    progend = 0L;
                } else {
                    Gob pl = gui.map == null ? null : gui.map.player();
                    boolean moving = pl != null && pl.getattr(Moving.class) != null;
                    boolean appeared = invsig(gui) > madesig;
                    if (progend == 0L) {
                        progend = now;
                    }
                    if (!appeared && !moving && lastprog < 75 && now - progend < 1000L) {
                        break;
                    }
                    progend = 0L;
                    if (!appeared && (lastprog < 75 || moving)) {
                        stop(
                                made == 0
                                        ? "Crafting " + crafting.name + " was interrupted; nothing was made."
                                        : "Crafting interrupted after " + made + " " + crafting.name + ".");
                        break;
                    }
                    lastprog = 0;
                    made++;
                    left--;
                    if (left <= 0) {
                        stop("Crafted " + made + " " + crafting.name);
                    } else if (mw != null) {
                        madesig = invsig(gui);
                        mw.wdgmsg("make", 0);
                        stage = 2;
                        since = now;
                    } else {
                        stop("Stopped after " + made + ": the crafting window closed");
                    }
                }
                break;
            default:
                stop(null);
        }
    }
}
