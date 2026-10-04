package haven;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

public class FayteAlmanac {
    public static final String ITEMS = "Items";
    public static final String CREATURES = "Creatures";
    public static final String RECIPES = "Recipes";
    public static final String SKILLS = "Skills";
    public static final String PROFICIENCIES = "Proficiencies";
    public static final String BIOMES = "Biomes";
    public static final String JOURNAL = "Journal";
    public static final String HISTORY = "History";
    public static final String FAVORITES = "Favorites";
    public static final String[] CATS = {ITEMS, CREATURES, RECIPES, SKILLS, PROFICIENCIES, BIOMES, JOURNAL};
    private static final long SCAN_MS = 2000L;
    private static final long SAVE_MS = 30000L;
    private static final long ACT_MS = 8000L;
    private static final Pattern DONE = Pattern.compile("\\s*\\(\\d+% done\\)\\s*$");
    private static final Pattern DIGITS = Pattern.compile("\\d+$");
    private static final Map<String, String> TILES = new HashMap<>();
    private static final Map<String, String> VERBS = new HashMap<>();
    private static FayteAlmanac.Book book;
    private static File bookfile;
    private static GameUI bookgui;
    private static boolean dirty = false;
    private static boolean seeded = false;
    private static long lastscan = 0L;
    private static long lastsave = 0L;
    private static long lastmissing = 0L;
    private static long lasticons = 0L;
    private static Set<String> exported = null;
    private static String rcname;
    private static long rctime;
    private static final Set<String> unmapped = new HashSet<>();

    static {
        TILES.put("grass", "Grassland");
        TILES.put("savanna", "Badlands");
        TILES.put("crag", "Crag");
        TILES.put("greenwood", "Greenwood");
        TILES.put("leaf", "Autumnal");
        TILES.put("shrub", "Shrub");
        TILES.put("oldblack", "Coniferous");
        TILES.put("brush", "Woodland");
        TILES.put("fen", "Marshland");
        TILES.put("swamp", "Swamp");
        TILES.put("deep", "Bodies of Water");
        TILES.put("water", "Bodies of Water");
        TILES.put("beach", "Beaches");
        TILES.put("rbeach", "Beaches");
        TILES.put("clay", "Clay Pit");
        TILES.put("limestone", "Lime Pit");
        TILES.put("stone", "Quarry");
        TILES.put("snow", "Snow");
        TILES.put("apath", "Game Trail");
        TILES.put("dirt", "Dirt");
        TILES.put("mountain", "Mountain");
        VERBS.put("Eat", "Eaten");
        VERBS.put("Study", "Studied");
        VERBS.put("Drink", "Drunk");
        Runtime.getRuntime().addShutdownHook(new Thread(FayteAlmanac::flush, "Almanac save"));
    }

    public static void flush() {
        save(true);
        FayteNotesPanel.save(true);
        FaytePilgrims.flush();
    }

    public static class Rec {
        public String cat;
        public String name;
        public String icon;
        public long first;
        public boolean fresh;
        public Map<String, Integer> counts = new TreeMap<>();
        public List<String[]> rows;
        public String note;
        public String label;

        public int total() {
            int n = 0;

            for (int v : counts.values()) {
                n += v;
            }
            return n;
        }
    }

    public static class Book {
        public Map<String, FayteAlmanac.Rec> recs = new TreeMap<>();
        public Map<String, Long> achieved = new TreeMap<>();
        public Map<String, Integer> tally = new TreeMap<>();
        public boolean achinit;
        public boolean gainsfixed;
        public List<String> favs = new ArrayList<>();
        public List<String> history = new ArrayList<>();
        public Map<String, Integer> studied = new TreeMap<>();
    }

    public static synchronized int studiedsince(String name) {
        if (book == null || book.studied == null || name == null) {
            return 0;
        }
        return book.studied.getOrDefault(clean(name), 0);
    }

    private static synchronized void studiedonce(String name) {
        if (book == null) {
            return;
        }
        if (book.studied == null) {
            book.studied = new TreeMap<>();
        }
        book.studied.merge(clean(name), 1, Integer::sum);
        dirty = true;
    }

    public static synchronized void studyreset() {
        if (book != null && book.studied != null && !book.studied.isEmpty()) {
            book.studied.clear();
            dirty = true;
        }
    }

    public static synchronized void visited(FayteAlmanac.Rec r) {
        if (book == null || r == null) {
            return;
        }
        if (book.history == null) {
            book.history = new ArrayList<>();
        }
        String k = key(r.cat, r.name);
        book.history.remove(k);
        book.history.add(0, k);
        while (book.history.size() > 100) {
            book.history.remove(book.history.size() - 1);
        }
        dirty = true;
    }

    public static synchronized boolean isfav(FayteAlmanac.Rec r) {
        return book != null && r != null && book.favs != null && book.favs.contains(key(r.cat, r.name));
    }

    public static synchronized boolean togglefav(FayteAlmanac.Rec r) {
        if (book == null || r == null) {
            return false;
        }
        if (book.favs == null) {
            book.favs = new ArrayList<>();
        }
        String k = key(r.cat, r.name);
        boolean on = !book.favs.remove(k);
        if (on) {
            book.favs.add(0, k);
        }
        dirty = true;
        return on;
    }

    public static synchronized List<FayteAlmanac.Rec> saved(String which) {
        List<FayteAlmanac.Rec> ret = new ArrayList<>();
        List<String> keys = book == null ? null : (FAVORITES.equals(which) ? book.favs : book.history);
        if (keys != null) {
            for (String k : keys) {
                FayteAlmanac.Rec r = book.recs.get(k);
                if (r != null) {
                    ret.add(r);
                }
            }
        }
        return ret;
    }

    public static synchronized boolean ready() {
        return book != null;
    }

    public static synchronized void tally(String k, int n) {
        if (book != null) {
            if (book.tally == null) {
                book.tally = new TreeMap<>();
            }
            book.tally.merge(k, n, Integer::sum);
            dirty = true;
        }
    }

    public static synchronized int tallied(String k) {
        return book == null || book.tally == null ? 0 : book.tally.getOrDefault(k, 0);
    }

    public static synchronized int tallysum(String prefix, String part) {
        return tallysum(prefix, part, null);
    }

    public static synchronized int tallysum(String prefix, String part, String not) {
        int n = 0;
        if (book != null && book.tally != null) {
            for (Map.Entry<String, Integer> e : book.tally.entrySet()) {
                if (e.getKey().startsWith(prefix)
                        && e.getKey().contains(part)
                        && (not == null || !e.getKey().contains(not))) {
                    n += e.getValue();
                }
            }
        }
        return n;
    }

    public static synchronized Long achieved(String id) {
        return book == null || book.achieved == null ? null : book.achieved.get(id);
    }

    public static synchronized void achieve(String id) {
        if (book != null) {
            if (book.achieved == null) {
                book.achieved = new TreeMap<>();
            }
            book.achieved.put(id, System.currentTimeMillis());
            dirty = true;
        }
    }

    public static synchronized boolean achinit() {
        return book != null && book.achinit;
    }

    public static synchronized void setachinit() {
        if (book != null) {
            book.achinit = true;
            dirty = true;
        }
    }

    public static synchronized int counted(String cat, String verb) {
        int n = 0;
        if (book != null) {
            for (FayteAlmanac.Rec r : book.recs.values()) {
                if (r.cat.equals(cat)) {
                    n += r.counts.getOrDefault(verb, 0);
                }
            }
        }
        return n;
    }

    public static final String[] JKINDS = {
        "All", "Feasts", "Cravings", "Humours", "Proficiencies", "Achievements", "Other"
    };

    public static String jtitle(FayteAlmanac.Rec r) {
        int dot = r.name.indexOf(" \u00b7 ");
        return dot > 0 ? r.name.substring(dot + 3) : r.name;
    }

    public static String jstamp(FayteAlmanac.Rec r) {
        int dot = r.name.indexOf(" \u00b7 ");
        return dot > 0 ? r.name.substring(0, dot) : "";
    }

    public static String shown(FayteAlmanac.Rec r) {
        if (JOURNAL.equals(r.cat) && r.label != null) {
            String st = jstamp(r);
            return st.isEmpty() ? r.label : st + " \u00b7 " + r.label;
        }
        return r.name;
    }

    public static String jkind(FayteAlmanac.Rec r) {
        String t = jtitle(r);
        if (t.startsWith("Achievement")) {
            return "Achievements";
        } else if (t.startsWith("Feast")) {
            return "Feasts";
        } else if (t.startsWith("Craving")) {
            return "Cravings";
        } else if (t.startsWith("Humours")) {
            return "Humours";
        } else if (t.startsWith("Proficiency")) {
            return "Proficiencies";
        }
        return "Other";
    }

    public static synchronized void relabel(FayteAlmanac.Rec r, String text) {
        r.label = text == null || text.trim().isEmpty() ? null : text.trim();
        dirty = true;
    }

    public static synchronized void note(FayteAlmanac.Rec r, String text) {
        r.note = text == null || text.trim().isEmpty() ? null : text.trim();
        dirty = true;
    }

    private static String key(String cat, String name) {
        return cat + "|" + name;
    }

    private static Gson gson() {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    public static synchronized FayteAlmanac.Rec get(String cat, String name) {
        return book == null ? null : book.recs.get(key(cat, name));
    }

    public static synchronized List<FayteAlmanac.Rec> list(String cat) {
        List<FayteAlmanac.Rec> ret = new ArrayList<>();
        if (book != null) {
            for (FayteAlmanac.Rec r : book.recs.values()) {
                if (r.cat.equals(cat)) {
                    ret.add(r);
                }
            }
        }
        return ret;
    }

    public static synchronized void seen(FayteAlmanac.Rec r) {
        if (r != null && r.fresh) {
            r.fresh = false;
            dirty = true;
        }
    }

    public static synchronized int seenall() {
        int n = 0;
        if (book != null) {
            for (FayteAlmanac.Rec r : book.recs.values()) {
                if (r.fresh) {
                    r.fresh = false;
                    n++;
                }
            }
            dirty = n > 0 || dirty;
        }
        return n;
    }

    public static synchronized int freshall() {
        int n = 0;
        if (book != null) {
            for (FayteAlmanac.Rec r : book.recs.values()) {
                if (r.fresh && (RECIPES.equals(r.cat) || SKILLS.equals(r.cat))) {
                    n++;
                }
            }
        }
        return n;
    }

    public static synchronized int fresh(String cat) {
        int n = 0;
        if (book != null) {
            for (FayteAlmanac.Rec r : book.recs.values()) {
                if (r.fresh && r.cat.equals(cat)) {
                    n++;
                }
            }
        }
        return n;
    }

    public static synchronized FayteAlmanac.Rec match(String target) {
        if (book == null || target == null) {
            return null;
        }
        FayteWikiData.Entry we = FayteWikiData.find(target);

        for (FayteAlmanac.Rec r : book.recs.values()) {
            if (r.name.equalsIgnoreCase(target) || (we != null && FayteWikiData.find(r.name) == we)) {
                return r;
            }
        }
        return null;
    }

    public static synchronized int size(String cat) {
        int n = 0;
        if (book != null) {
            for (FayteAlmanac.Rec r : book.recs.values()) {
                if (r.cat.equals(cat)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static String charname(GameUI gui) {
        String n = Config.currentCharName;
        if (n == null || n.isEmpty()) {
            n = gui.chrid;
        }
        return n == null || n.isEmpty() ? "unknown" : n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static synchronized void open(GameUI gui) {
        if (book != null) {
            save(true);
        }
        bookgui = gui;
        seeded = false;
        bookfile = new File(
                new File(
                        new File(new File(Config.userhome, "fayte"), "almanac"),
                        Config.server == null ? "server" : Config.server),
                charname(gui) + ".json");
        book = null;
        if (bookfile.exists()) {
            try (Reader r = new InputStreamReader(new FileInputStream(bookfile), StandardCharsets.UTF_8)) {
                book = gson().fromJson(r, FayteAlmanac.Book.class);
            } catch (Exception e) {
                FayteLog.log("Almanac: could not read " + bookfile, e);
                try {
                    Files.copy(
                            bookfile.toPath(),
                            new File(bookfile.getPath() + ".bad").toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e2) {
                    FayteLog.once("FayteAlmanac.open", e2);
                }
            }
        }
        if (book == null || book.recs == null) {
            book = new FayteAlmanac.Book();
        }
        merge();

        FayteLog.log("Almanac: opened " + bookfile + " (" + book.recs.size() + " entries)");
    }

    private static synchronized void save(boolean force) {
        if (book != null && bookfile != null && dirty && (force || System.currentTimeMillis() - lastsave > SAVE_MS)) {
            lastsave = System.currentTimeMillis();
            dirty = false;

            try {
                bookfile.getParentFile().mkdirs();
                File tmp = new File(bookfile.getPath() + ".tmp");
                try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                    gson().toJson(book, w);
                }
                Files.move(tmp.toPath(), bookfile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                dirty = true;
                FayteLog.log("Almanac: could not save", e);
            }
        }
    }

    private static final Pattern STACK = Pattern.compile("^\\d+\\s+([a-z].*)$");
    private static final Pattern WEIGHT = Pattern.compile("^\\d+(?:\\.\\d+)?\\s*kg of (.+)$");

    public static String clean(String name) {
        if (name == null) {
            return null;
        }
        Matcher m = STACK.matcher(name.trim());
        if (m.matches()) {
            String n = m.group(1);
            return Character.toUpperCase(n.charAt(0)) + n.substring(1);
        }
        m = WEIGHT.matcher(name.trim());
        if (m.matches()) {
            return m.group(1);
        }
        return name;
    }

    private static void merge() {
        Map<String, FayteAlmanac.Rec> out = new LinkedHashMap<>();
        int merged = 0;
        for (FayteAlmanac.Rec r : book.recs.values()) {
            String n = clean(r.name);
            if (!n.equals(r.name)) {
                r.name = n;
                merged++;
            }
            String k = key(r.cat, r.name);
            FayteAlmanac.Rec o = out.get(k);
            if (o == null) {
                out.put(k, r);
            } else {
                o.first = Math.min(o.first, r.first);
                if (o.icon == null) {
                    o.icon = r.icon;
                }
                for (Map.Entry<String, Integer> c : r.counts.entrySet()) {
                    o.counts.merge(c.getKey(), c.getValue(), Integer::sum);
                }
            }
        }
        int junk = 0;
        Pattern delta = Pattern.compile("\\(\\+([0-9.]+)\\)");
        for (Iterator<Map.Entry<String, FayteAlmanac.Rec>> it = out.entrySet().iterator();
                !book.gainsfixed && it.hasNext(); ) {
            FayteAlmanac.Rec r = it.next().getValue();
            if (!JOURNAL.equals(r.cat) || r.rows == null) {
                continue;
            }
            String t = jtitle(r);
            if (!t.startsWith("Humours raised") && !t.startsWith("Proficiency up")) {
                continue;
            }
            boolean bad = false;
            for (String[] row : r.rows) {
                if (row.length < 2) {
                    continue;
                }
                Matcher m = delta.matcher(row[1]);
                if (m.find()) {
                    double v = Double.parseDouble(m.group(1));
                    bad |= row[1].contains(".") ? v > 30 : v > 20;
                }
            }
            if (bad) {
                it.remove();
                junk++;
            }
        }
        if (!book.gainsfixed) {
            book.gainsfixed = true;
            dirty = true;
        }
        if (junk > 0) {
            merged += junk;
            FayteLog.log("Almanac: removed " + junk + " bogus gain entries from logging in");
        }
        if (merged > 0) {
            book.recs.clear();
            book.recs.putAll(out);
            dirty = true;
            FayteLog.log("Almanac: merged " + merged + " stack names like \"72 seeds of X\"");
        }
    }

    public static synchronized FayteAlmanac.Rec record(String cat, String name, String icon) {
        name = clean(name);
        if (book == null || name == null || name.isEmpty()) {
            return null;
        }
        String k = key(cat, name);
        FayteAlmanac.Rec r = book.recs.get(k);
        if (r == null) {
            r = new FayteAlmanac.Rec();
            r.cat = cat;
            r.name = name;
            r.icon = icon;
            r.first = System.currentTimeMillis();
            r.fresh = seeded;
            book.recs.put(k, r);
            dirty = true;
            if (seeded && SKILLS.equals(cat)) {
                studyreset();
            }
        } else if (r.icon == null && icon != null) {
            r.icon = icon;
            dirty = true;
        }
        return r;
    }

    public static synchronized void count(String cat, String name, String verb) {
        FayteAlmanac.Rec r = record(cat, name, null);
        if (r != null) {
            r.counts.merge(verb, 1, Integer::sum);
            dirty = true;
        }
    }

    private static void exporticons() {
        File dir = FaytePaths.icons();
        if (exported == null) {
            exported = new HashSet<>();
            String[] have = dir.list();
            if (have != null) {
                for (String f : have) {
                    exported.add(f.toLowerCase());
                }
            }
        }
        List<FayteAlmanac.Rec> todo = new ArrayList<>();
        synchronized (FayteAlmanac.class) {
            if (book != null) {
                for (FayteAlmanac.Rec r : book.recs.values()) {
                    if (r.icon != null && !exported.contains((FaytePaths.safename(r.name) + ".png").toLowerCase())) {
                        todo.add(r);
                    }
                }
            }
        }
        int n = 0;

        for (FayteAlmanac.Rec r : todo) {
            if (n >= 20) {
                break;
            }
            String file = FaytePaths.safename(r.name) + ".png";
            try {
                BufferedImage img = Resource.load(r.icon).layer(Resource.imgc).img;
                dir.mkdirs();
                ImageIO.write(img, "png", new File(dir, file));
                exported.add(file.toLowerCase());
                FayteEntries.forget(r.name);
                n++;
            } catch (Loading l) {
            } catch (Exception ex) {
                exported.add(file.toLowerCase());
            }
        }
    }

    public static String singular(String cat) {
        switch (cat) {
            case ITEMS:
                return "item";
            case CREATURES:
                return "creature";
            case RECIPES:
                return "recipe";
            case SKILLS:
                return "skill";
            case PROFICIENCIES:
                return "proficiency";
            case BIOMES:
                return "biome";
            default:
                return cat;
        }
    }

    public static String itemname(GItem item) {
        try {
            item.info();
            String n = item.name();
            return n == null ? null : DONE.matcher(n).replaceAll("").trim();
        } catch (Exception e) {
            return null;
        }
    }

    public static void itemrc(WItem w) {
        rcname = itemname(w.item);
        rctime = System.currentTimeMillis();
    }

    public static synchronized void journal(String title, List<String[]> rows) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        FayteAlmanac.Rec r = record(JOURNAL, stamp + " \u00b7 " + title, null);
        if (r != null) {
            r.rows = new ArrayList<>(rows);
            dirty = true;
        }
    }

    public static void flowerchosen(String option) {
        String verb = VERBS.get(option);
        FayteAchieve.chose(
                option,
                "Eaten".equals(verb) && rcname != null && System.currentTimeMillis() - rctime < ACT_MS ? rcname : null);
        if ("Eaten".equals(verb) && rcname != null && System.currentTimeMillis() - rctime < ACT_MS) {
            FayteGains.ate(rcname);
        }
        if (verb != null && rcname != null && System.currentTimeMillis() - rctime < ACT_MS) {
            count(ITEMS, rcname, verb);
            if ("Studied".equals(verb)) {
                studiedonce(rcname);
            }
        }
        rcname = null;
    }

    public static void crafted(String recipe) {
        if (recipe != null) {
            count(RECIPES, recipe, "Crafted");
        }
    }

    public static void tick(GameUI gui) {
        long now = System.currentTimeMillis();
        if (bookgui != gui) {
            open(gui);
        }
        if (now - lastscan >= SCAN_MS) {
            lastscan = now;

            try {
                scan(gui);
            } catch (RuntimeException e) {
                FayteLog.log("Almanac: scan failed", e);
            }

            synchronized (FayteAlmanac.class) {
                seeded = true;
            }
        }
        if (now - lasticons > 3000L) {
            lasticons = now;
            exporticons();
        }
        if (now - lastmissing > 60000L && FayteWikiData.get() != null) {
            lastmissing = now;
            List<FayteAlmanac.Rec> all = new ArrayList<>();
            synchronized (FayteAlmanac.class) {
                if (book != null) {
                    all.addAll(book.recs.values());
                }
            }
            for (FayteAlmanac.Rec r : all) {
                if (JOURNAL.equals(r.cat)) {
                    continue;
                }
                if (FayteWikiData.find(r.name) == null && (r.icon == null || FayteWikiData.find(r.icon) == null)) {
                    FayteMissing.note(singular(r.cat), r.name, r.icon);
                }
            }
        }
        save(false);
    }

    private static void scanitem(GItem item) {
        String n = itemname(item);
        if (n != null) {
            record(ITEMS, n, item.resname());
        }
    }

    private static void scan(GameUI gui) {
        if (gui.maininv != null) {
            for (Widget w = gui.maininv.child; w != null; w = w.next) {
                if (w instanceof WItem) {
                    scanitem(((WItem) w).item);
                }
            }
        }
        Equipory eq = gui.getEquipory();
        if (eq != null) {
            for (WItem w : eq.slots) {
                if (w != null) {
                    scanitem(w.item);
                }
            }
        }
        for (GItem g : new ArrayList<>(gui.hand)) {
            scanitem(g);
        }
        scancreatures(gui);
        scanrecipes(gui);
        scanskills(gui);
        scanprofs(gui);
        scanbiome(gui);
    }

    private static void scancreatures(GameUI gui) {
        OCache oc = gui.ui.sess.glob.oc;
        List<String> names = new ArrayList<>();
        synchronized (oc) {
            for (Gob g : oc) {
                String rn = FayteMsg.resname(g);
                if (rn != null && rn.startsWith("gfx/kritter/")) {
                    names.add(rn);
                }
            }
        }
        for (String rn : names) {
            record(CREATURES, FayteWorldNames.display(rn), null);
        }
    }

    private static void scanrecipes(GameUI gui) {
        Glob glob = gui.ui.sess.glob;
        List<Glob.Pagina> pags;
        synchronized (glob.paginae) {
            pags = new ArrayList<>(glob.paginae);
        }
        for (Glob.Pagina p : pags) {
            try {
                Resource r = p.res();
                if (r != null && (r.name.startsWith("paginae/craft/") || r.name.startsWith("paginae/bld/"))) {
                    Resource.AButton act = r.layer(Resource.action);
                    if (act != null && act.name != null) {
                        record(RECIPES, act.name, r.name);
                    }
                }
            } catch (Loading l) {
            }
        }
    }

    private static void scanskills(GameUI gui) {
        if (gui.chrwdg != null && gui.chrwdg.csk != null && gui.chrwdg.csk.skills != null) {
            for (CharWnd.Skill sk : gui.chrwdg.csk.skills) {
                try {
                    Resource r = sk.res.get();
                    Resource.AButton act = r.layer(Resource.action);
                    if (act != null) {
                        record(SKILLS, act.name, r.name);
                    }
                } catch (Loading l) {
                }
            }
        }
    }

    private static void scanprofs(GameUI gui) {
        Map<String, Glob.CAttr> ca = gui.ui.sess.glob.cattr;
        synchronized (ca) {
            for (Map.Entry<String, String> a : CharWnd.attrnm.entrySet()) {
                Glob.CAttr v = ca.get(a.getKey());
                if (v != null && v.getBase() > 0) {
                    record(PROFICIENCIES, a.getValue(), "gfx/hud/skills/" + a.getKey());
                }
            }
        }
    }

    private static void scanbiome(GameUI gui) {
        if (gui.map == null) {
            return;
        }
        Gob pl = gui.map.player();
        if (pl == null) {
            return;
        }
        try {
            MCache map = gui.ui.sess.glob.map;
            int t = map.gettile(pl.rc.div(MCache.tilesz));
            Resource r = map.tilesetr(t);
            if (r != null && r.name.startsWith("gfx/tiles/")) {
                String code = DIGITS.matcher(r.name.substring(10)).replaceAll("");
                String biome = TILES.get(code);
                if (biome != null) {
                    record(BIOMES, biome, null);
                } else if (unmapped.add(code)) {
                    FayteMissing.note("terrain tile", code, r.name);
                }
            }
        } catch (Loading l) {
        } catch (RuntimeException e) {
            FayteLog.once("FayteAlmanac.scanbiome", e);
        }
    }
}
