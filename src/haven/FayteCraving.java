package haven;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FayteCraving {
    private static WItem item = null;
    private static Coord itemc = null;
    private static long itemat = 0L;
    private static String repick = null;
    private static long repickat = 0L;
    private static Map<String, List<String>> known = null;
    private static boolean dirty = false;
    private static long lastlearn = 0L;

    public static void rc(WItem w, Coord c) {
        item = w;
        itemc = c;
        itemat = System.currentTimeMillis();
    }

    private static File file() {
        return new File(FaytePaths.fayte(), "foodgroups.tsv");
    }

    private static synchronized Map<String, List<String>> known() {
        if (known == null) {
            known = new HashMap<>();
            File f = file();
            if (f.exists()) {
                try {
                    for (String l : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                        String[] p = l.trim().split("\t");
                        if (p.length == 2) {
                            known.put(p[0], new ArrayList<>(Arrays.asList(p[1].split("\\|"))));
                        }
                    }
                } catch (Exception e) {
                    FayteLog.log("Food groups: could not read " + f + ": " + e);
                }
            }
        }
        return known;
    }

    private static synchronized void save() {
        if (!dirty) {
            return;
        }
        dirty = false;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : known().entrySet()) {
            sb.append(e.getKey())
                    .append('\t')
                    .append(String.join("|", e.getValue()))
                    .append('\n');
        }
        try {
            FaytePaths.write(file(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.log("Food groups: could not save: " + e);
        }
    }

    public static List<String> groups(GItem it) {
        List<String> ret = new ArrayList<>();
        try {
            GobbleInfo gi = ItemInfo.find(GobbleInfo.class, it.info());
            if (gi != null) {
                for (GobbleInfo.Event ev : gi.evs) {
                    for (ItemInfo ii : ev.info) {
                        if (ii instanceof GobbleEventInfo) {
                            Resource r = ((GobbleEventInfo) ii).res.get();
                            Resource.Tooltip t = r.layer(Resource.tooltip);
                            ret.add(t != null ? t.t : r.name);
                        }
                    }
                }
            }
        } catch (Loading e) {
            return ret;
        } catch (RuntimeException e) {
            return ret;
        }
        if (!ret.isEmpty()) {
            String rn = it.resname();
            if (rn != null) {
                synchronized (FayteCraving.class) {
                    List<String> old = known().get(rn);
                    if (old == null || !old.equals(ret)) {
                        known().put(rn, new ArrayList<>(ret));
                        dirty = true;
                    }
                }
            }
        }
        return ret;
    }

    public static void learn(GameUI gui) {
        long now = System.currentTimeMillis();
        if (now - lastlearn < 5000L) {
            return;
        }
        lastlearn = now;
        for (Inventory inv : FayteBagSel.inventories(gui)) {
            for (Widget w = inv.child; w != null; w = w.next) {
                if (w instanceof WItem) {
                    groups(((WItem) w).item);
                }
            }
        }
        save();
    }

    private static Resource cravres(GameUI gui) {
        if (gui == null || gui.tm == null || gui.tm.cravail == null) {
            return null;
        }
        try {
            return gui.tm.cravail.get();
        } catch (Loading e) {
            return null;
        }
    }

    public static String cravname(Resource r) {
        Resource.Tooltip t = r.layer(Resource.tooltip);
        return t != null ? t.t : r.name;
    }

    public static List<String> wikigroups(String name) {
        List<String> ret = new ArrayList<>();
        FayteWikiData.Entry e = name == null ? null : FayteWikiData.find(name);
        if (e != null && e.tpl != null) {
            for (Map<String, String> t : e.tpl.values()) {
                for (Map.Entry<String, String> p : t.entrySet()) {
                    if (p.getKey().trim().equalsIgnoreCase("Food Groups")) {
                        for (String g : p.getValue().split(",")) {
                            if (!g.trim().isEmpty()) {
                                ret.add(g.trim());
                            }
                        }
                    }
                }
            }
        }
        if (ret.isEmpty()) {
            String w = winegroup(name);
            if (w != null) {
                ret.add(w);
            }
        }
        return ret;
    }

    private static Map<String, String> wines = null;
    private static Object winesof = null;

    public static synchronized String winegroup(String name) {
        if (name == null) {
            return null;
        }
        FayteWikiData.Store st = FayteWikiData.get();
        if (st == null) {
            return null;
        }
        if (wines == null || winesof != st) {
            winesof = st;
            wines = new HashMap<>();
            FayteWikiData.Raw r = st.pages.get("Craving");
            if (r != null && r.text != null) {
                Matcher m = Pattern.compile(
                                "\\{\\{i2\\|([^}|]+)\\}\\}\\s*\\{\\{!\\}\\}\\s*\\{\\{i\\|\\s*([^}|]+)\\}\\}")
                        .matcher(r.text);
                while (m.find()) {
                    wines.put(norm(m.group(2).trim()), m.group(1).trim());
                }
            }
        }
        return wines.get(norm(name));
    }

    public static String norm(String s) {
        return s.toLowerCase().replaceAll("\\band\\b", "").replaceAll("[^a-z]", "");
    }

    public static synchronized List<String> cravgroups(Resource r) {
        List<String> g = known().get(r.name);
        if (g != null && !g.isEmpty()) {
            return g;
        }
        return wikigroups(cravname(r));
    }

    public static String matches(GameUI gui, GItem it) {
        Resource cr = cravres(gui);
        if (cr == null) {
            return null;
        }
        String cn = cravname(cr);
        String nm = FayteAlmanac.itemname(it);
        List<String> ig = new ArrayList<>(groups(it));
        ig.addAll(wikigroups(nm));
        List<String> cg = cravgroups(cr);
        FayteLog.log("Craving check: craving " + cn + " (" + cr.name + ", groups " + cg + "); eating " + nm + " ("
                + it.resname() + ", groups " + ig + ")");
        if (cr.name.equals(it.resname()) || (nm != null && nm.equalsIgnoreCase(cn))) {
            return cn;
        }
        for (String g : ig) {
            if (norm(g).equals(norm(cn))) {
                return cn;
            }
            for (String c : cg) {
                if (norm(g).equals(norm(c))) {
                    return cn + " (" + c + ")";
                }
            }
        }
        return null;
    }

    public static boolean interceptstudy(FlowerMenu m, FlowerMenu.Petal p) {
        if (p == null || !"Study".equals(p.name)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (repick != null && now - repickat < 5000L) {
            repick = null;
            return false;
        }
        GameUI gui = m.ui.gui;
        final WItem w = item;
        final Coord wc = itemc;
        if (gui == null
                || w == null
                || !w.linked()
                || now - itemat > 15000L
                || !Utils.getpref("fayte_ask_studyagain", "").isEmpty()) {
            return false;
        }
        String nm = FayteAlmanac.itemname(w.item);
        FayteAlmanac.Rec r = nm == null ? null : FayteAlmanac.get(FayteAlmanac.ITEMS, nm);
        Integer times = r == null || r.counts == null ? null : r.counts.get("Studied");
        if (times == null || times < 1) {
            return false;
        }
        m.choose(null);
        FayteConfirm.ask(
                gui,
                "studyagain",
                "Study it again?",
                "You've studied " + nm + " " + (times == 1 ? "once" : times + " times")
                        + " already. Studying the same thing again costs more inspiration each time. Study it anyway?",
                () -> {
                    if (w.linked()) {
                        repick = "Study";
                        repickat = System.currentTimeMillis();
                        w.item.wdgmsg("iact", wc);
                    }
                });
        return true;
    }

    public static boolean intercept(FlowerMenu m, FlowerMenu.Petal p) {
        if (p == null || !"Eat".equals(p.name)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (repick != null && now - repickat < 5000L) {
            repick = null;
            return false;
        }
        GameUI gui = m.ui.gui;
        final WItem w = item;
        final Coord wc = itemc;
        if (gui == null
                || w == null
                || !w.linked()
                || now - itemat > 15000L
                || !Utils.getpref("fayte_ask_craving", "").isEmpty()) {
            return false;
        }
        String cr = matches(gui, w.item);
        if (cr == null) {
            return false;
        }
        String nm = FayteAlmanac.itemname(w.item);
        m.choose(null);
        FayteConfirm.ask(
                gui,
                "craving",
                "Fulfil your craving?",
                (nm == null ? "This" : nm) + " counts for your craving: " + cr
                        + ". Eating it now fulfils the craving. Eat it?",
                () -> {
                    if (w.linked()) {
                        repick = "Eat";
                        repickat = System.currentTimeMillis();
                        w.item.wdgmsg("iact", wc);
                    }
                });
        return true;
    }

    public static FlowerMenu.Petal repick(FlowerMenu.Petal[] opts) {
        if (repick == null || System.currentTimeMillis() - repickat > 5000L) {
            return null;
        }
        for (FlowerMenu.Petal p : opts) {
            if (repick.equals(p.name)) {
                return p;
            }
        }
        return null;
    }
}
