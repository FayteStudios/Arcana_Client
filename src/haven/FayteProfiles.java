package haven;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

public class FayteProfiles {
    public static final String[] KINDS = {"ui", "hotbars", "markers"};
    private static final String[] HUDPREFIX = {"fayte_hud_", "fayte_alm_size2", "fayte_ask_"};
    public static final String DEFAULT = "Default";
    private static final String WHY = "history.why";
    private static final String OWNER = "fayte_profile_owner";
    private static final int KEEP = 20;
    private static String loggedin = null;
    private static final FayteConfig.CoordSetting[] POSSET = {
        FayteConfig.minimapPos, FayteConfig.actionGridPos, FayteConfig.buttonPanelPos
    };

    private static File dir() {
        return new File(FaytePaths.fayte(), "profiles");
    }

    private static File file(String name) {
        return new File(dir(), FaytePaths.safename(name) + ".properties");
    }

    private static File oldfile(String kind, String name) {
        return new File(new File(dir(), kind), FaytePaths.safename(name) + ".properties");
    }

    public static boolean isdefault(String name) {
        return name != null && name.trim().equalsIgnoreCase(DEFAULT);
    }

    public static List<String> list() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names(dir(), names);
        for (String k : KINDS) {
            names(new File(dir(), k), names);
        }
        List<String> ret = new ArrayList<>(names);
        if (exists(DEFAULT)) {
            ret.add(0, DEFAULT);
        }
        return ret;
    }

    private static void names(File d, TreeSet<String> into) {
        String[] fs = d.list();
        if (fs == null) {
            return;
        }
        for (String f : fs) {
            if (f.endsWith(".properties") && !f.startsWith("@")) {
                String n = f.substring(0, f.length() - ".properties".length());
                if (!isdefault(n)) {
                    into.add(n);
                }
            }
        }
    }

    private static String readtext(File f) {
        try {
            return f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            FayteLog.log("Profiles: could not read " + f + ": " + e);
            return null;
        }
    }

    private static void write(File f, String text) {
        try {
            f.getParentFile().mkdirs();
            FaytePaths.write(f, text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.log("Profiles: could not write " + f + ": " + e);
        }
    }

    private static List<String> hudkeys() {
        List<String> ret = new ArrayList<>();
        try {
            for (String k : Utils.prefs().keys()) {
                for (String p : HUDPREFIX) {
                    if (k.startsWith(p)) {
                        ret.add(k);
                    }
                }
            }
        } catch (Exception e) {
            FayteLog.log("Profiles: could not list settings: " + e);
        }
        return ret;
    }

    private static Properties capture(String kind) {
        Properties p = new Properties();
        p.setProperty("kind", kind);
        if (kind.equals("ui")) {
            synchronized (Config.window_props) {
                for (String k : Config.window_props.stringPropertyNames()) {
                    p.setProperty("win." + k, Config.window_props.getProperty(k));
                }
            }
            for (String k : hudkeys()) {
                p.setProperty("pref." + k, Utils.getpref(k, ""));
            }
            if (FayteChatWindow.anyopen()) {
                FayteChatWindow.save();
            }
            String chat = readtext(FayteChatWindow.file());
            for (FayteConfig.CoordSetting cs : POSSET) {
                Coord v = cs.get();
                if (v != null) {
                    p.setProperty("cfg." + cs.key, v.x + "," + v.y);
                }
            }
            if (chat != null) {
                p.setProperty("file.chat", chat);
            }
        } else if (kind.equals("hotbars")) {
            String b = readtext(FayteBars.datafile());
            String m = readtext(FayteMacros.datafile());
            if (b != null) {
                p.setProperty("file.bars", b);
            }
            if (m != null) {
                p.setProperty("file.macros", m);
            }
            String mn = readtext(FayteMenus.datafile());
            String fv = readtext(FayteRecipes.favdatafile());
            String ch = readtext(FayteRecipes.choicedatafile());
            p.setProperty("file.menus", mn == null ? "{}" : mn);
            p.setProperty("file.favorites", fv == null ? "" : fv);
            p.setProperty("file.choices", ch == null ? "" : ch);
        } else if (kind.equals("markers")) {
            String h = readtext(FayteHighlights.datafile());
            if (h != null) {
                p.setProperty("file.highlights", h);
            }
        }
        return p;
    }

    private static void apply(GameUI gui, String kind, Properties p) {
        if (kind.equals("ui")) {
            synchronized (Config.window_props) {
                Config.window_props.clear();
                for (String k : p.stringPropertyNames()) {
                    if (k.startsWith("win.")) {
                        Config.window_props.setProperty(k.substring(4), p.getProperty(k));
                    }
                }
            }
            Config.saveWindowOpt();
            for (String k : hudkeys()) {
                Utils.prefs().remove(k);
            }
            for (String k : p.stringPropertyNames()) {
                if (k.startsWith("pref.")) {
                    Utils.setpref(k.substring(5), p.getProperty(k));
                }
            }
            for (FayteConfig.CoordSetting cs : POSSET) {
                String v = p.getProperty("cfg." + cs.key);
                if (v != null) {
                    try {
                        String[] xy = v.split(",");
                        cs.set(new Coord(Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim())));
                    } catch (RuntimeException e) {
                        FayteLog.once("FayteProfiles.apply", e);
                    }
                }
            }
            FayteHud.resetcache();
            if (gui != null) {
                if (gui.mmap != null && FayteConfig.minimapMovable.get() && FayteConfig.minimapPos.get() != null) {
                    gui.mmap.c = GameUI.onScreen(FayteConfig.minimapPos.get(), gui.mmap.sz, gui.sz);
                }
                for (Widget w = gui.child; w != null; w = w.next) {
                    if (w instanceof Window && !(w instanceof LocalMiniMap) && !FayteHud.is(w) && w.visible) {
                        w.c = GameUI.onScreen(((Window) w).getOptCoord("_pos", w.c), w.sz, gui.sz);
                    }
                }
                String chat = p.getProperty("file.chat");
                if (chat != null) {
                    FayteChatWindow.closeall(gui);
                    write(FayteChatWindow.file(), chat);
                    FayteChatWindow.restore(gui);
                }
            }
        } else if (kind.equals("hotbars")) {
            if (p.getProperty("file.bars") != null) {
                write(FayteBars.datafile(), p.getProperty("file.bars"));
                FayteBars.reload();
            }
            if (p.getProperty("file.macros") != null) {
                write(FayteMacros.datafile(), p.getProperty("file.macros"));
                FayteMacros.reload();
            }
            if (p.getProperty("file.menus") != null) {
                write(FayteMenus.datafile(), p.getProperty("file.menus"));
                FayteMenus.reload();
            }
            if (p.getProperty("file.favorites") != null || p.getProperty("file.choices") != null) {
                if (p.getProperty("file.favorites") != null) {
                    write(FayteRecipes.favdatafile(), p.getProperty("file.favorites"));
                }
                if (p.getProperty("file.choices") != null) {
                    write(FayteRecipes.choicedatafile(), p.getProperty("file.choices"));
                }
                FayteRecipes.reloadprefs();
            }
        } else if (kind.equals("markers")) {
            if (p.getProperty("file.highlights") != null) {
                write(FayteHighlights.datafile(), p.getProperty("file.highlights"));
                FayteHighlights.reload();
            }
        }
    }

    private static Properties whole() {
        Properties all = new Properties();
        for (String k : KINDS) {
            Properties p = capture(k);
            for (String key : p.stringPropertyNames()) {
                all.setProperty(k + "." + key, p.getProperty(key));
            }
        }
        return all;
    }

    private static Properties part(Properties all, String kind) {
        Properties p = new Properties();
        String pre = kind + ".";
        for (String key : all.stringPropertyNames()) {
            if (key.startsWith(pre)) {
                p.setProperty(key.substring(pre.length()), all.getProperty(key));
            }
        }
        return p;
    }

    private static void applywhole(GameUI gui, Properties all) {
        for (String k : KINDS) {
            Properties p = part(all, k);
            if (!p.isEmpty()) {
                apply(gui, k, p);
            }
        }
    }

    private static Properties resource(String kind) {
        try (InputStream in =
                FayteProfiles.class.getResourceAsStream("/fayte/profiles/" + kind + "/" + DEFAULT + ".properties")) {
            if (in == null) {
                return null;
            }
            Properties p = new Properties();
            p.load(in);
            return p;
        } catch (Exception e) {
            FayteLog.log("Profiles: could not read the bundled Default: " + e);
            return null;
        }
    }

    private static Properties merge(Properties[] parts) {
        Properties all = new Properties();
        boolean any = false;
        for (int i = 0; i < KINDS.length; i++) {
            if (parts[i] != null) {
                any = true;
                for (String key : parts[i].stringPropertyNames()) {
                    all.setProperty(KINDS[i] + "." + key, parts[i].getProperty(key));
                }
            }
        }
        return any ? all : null;
    }

    private static Properties read(String name) {
        Properties[] parts = new Properties[KINDS.length];
        if (isdefault(name)) {
            for (int i = 0; i < KINDS.length; i++) {
                parts[i] = resource(KINDS[i]);
            }
            return merge(parts);
        }
        Properties p = readprops(file(name));
        if (p != null) {
            return p;
        }
        for (int i = 0; i < KINDS.length; i++) {
            parts[i] = readprops(oldfile(KINDS[i], name));
        }
        return merge(parts);
    }

    public static boolean exists(String name) {
        if (isdefault(name)) {
            return FayteProfiles.class.getResource("/fayte/profiles/ui/" + DEFAULT + ".properties") != null;
        }
        if (file(name).exists()) {
            return true;
        }
        for (String k : KINDS) {
            if (oldfile(k, name).exists()) {
                return true;
            }
        }
        return false;
    }

    private static boolean load(GameUI gui, String name) {
        Properties p = read(name);
        if (p == null) {
            return false;
        }
        applywhole(gui, p);
        return true;
    }

    private static void save(String name, Properties p, String why) {
        if (isdefault(name)) {
            return;
        }
        if (why != null) {
            Properties old = read(name);
            if (old != null) {
                if (same(old, p)) {
                    return;
                }
                keep(name, old, why);
            }
        }
        store(file(name), p, "Fayte profile: " + name);
    }

    private static void store(File f, Properties p, String comment) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            p.store(buf, comment);
            f.getParentFile().mkdirs();
            FaytePaths.write(f, buf.toByteArray());
        } catch (Exception e) {
            FayteLog.log("Profiles: could not save " + f + ": " + e);
        }
    }

    private static Properties readprops(File f) {
        if (!f.exists()) {
            return null;
        }
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
            return p;
        } catch (Exception e) {
            FayteLog.log("Profiles: could not read " + f + ": " + e);
            return null;
        }
    }

    private static boolean same(Properties a, Properties b) {
        Properties x = new Properties();
        Properties y = new Properties();
        x.putAll(a);
        y.putAll(b);
        x.remove(WHY);
        y.remove(WHY);
        return x.equals(y);
    }

    private static File histdir(String name) {
        return new File(new File(dir(), "history"), FaytePaths.safename(name));
    }

    private static void keep(String name, Properties p, String why) {
        File d = histdir(name);
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File f = new File(d, stamp + ".properties");
        for (int n = 2; f.exists(); n++) {
            f = new File(d, stamp + "-" + n + ".properties");
        }
        Properties c = new Properties();
        c.putAll(p);
        c.setProperty(WHY, why);
        store(f, c, "Fayte profile history: " + name);
        String[] fs = d.list();
        if (fs != null && fs.length > KEEP) {
            Arrays.sort(fs);
            for (int i = 0; i < fs.length - KEEP; i++) {
                new File(d, fs[i]).delete();
            }
        }
    }

    public static List<String> historynames() {
        List<String> ret = new ArrayList<>();
        String[] fs = new File(dir(), "history").list();
        if (fs != null) {
            for (String f : fs) {
                String[] in = histdir(f).list();
                if (in != null && in.length > 0 && !f.startsWith("@") && !isdefault(f)) {
                    ret.add(f);
                }
            }
        }
        Collections.sort(ret, String.CASE_INSENSITIVE_ORDER);
        return ret;
    }

    public static List<String[]> history(String name) {
        List<String[]> ret = new ArrayList<>();
        String[] fs = histdir(name).list();
        if (fs == null) {
            return ret;
        }
        Arrays.sort(fs, Collections.reverseOrder());
        for (String f : fs) {
            if (!f.endsWith(".properties")) {
                continue;
            }
            String label = f;
            try {
                Date d = new SimpleDateFormat("yyyyMMdd-HHmmss").parse(f.substring(0, 15));
                label = new SimpleDateFormat("MMM d, HH:mm").format(d);
            } catch (Exception e) {
                FayteLog.once("FayteProfiles.history", e);
            }
            Properties p = readprops(new File(histdir(name), f));
            if (p != null && p.getProperty(WHY) != null) {
                label += "  \u00b7  " + p.getProperty(WHY);
            }
            ret.add(new String[] {f, label});
        }
        return ret;
    }

    public static boolean restore(String name, String version) {
        Properties p = readprops(new File(histdir(name), version));
        if (p == null) {
            return false;
        }
        p.remove(WHY);
        save(name, p, "replaced by a restore");
        return true;
    }

    private static String charkey() {
        String c = Config.currentCharName;
        return c == null || c.isEmpty() ? null : "@" + c;
    }

    public static String active() {
        return loggedin != null ? loggedin : charkey();
    }

    public static String activelabel() {
        String a = active();
        return a == null ? "a new character (not saved until its next login)" : a.substring(1);
    }

    public static void saveas(String name) {
        save(name, whole(), "replaced by Save as");
    }

    public static boolean use(GameUI gui, String name) {
        String a = active();
        if (a == null || !exists(name)) {
            return false;
        }
        keep(a.substring(1), whole(), "before loading \"" + name + "\"");
        if (!load(gui, name)) {
            return false;
        }
        save(a, whole(), null);
        Utils.setpref(OWNER, a);
        return true;
    }

    private static String owner() {
        String o = Utils.getpref(OWNER, "");
        if (o.isEmpty()) {
            o = Utils.getpref(OWNER + "_ui", "");
        }
        return o;
    }

    public static void onlogin(GameUI gui) {
        String ck = charkey();
        loggedin = ck;
        String owner = owner();
        if (ck == null) {
            if (!owner.isEmpty() && !owner.startsWith("!")) {
                save(owner, whole(), null);
            }
            load(gui, DEFAULT);
            Utils.setpref(OWNER, "!unknown");
            FayteLog.log("Profiles: character name unknown (a brand-new character); started from Default, nothing"
                    + " will be saved this session");
            flush();
            return;
        }
        if (ck.equals(owner)) {
            FayteLog.log("Profiles: already in use (" + ck + ")");
        } else {
            if (!owner.isEmpty() && !owner.startsWith("!")) {
                save(owner, whole(), null);
            }
            if (load(gui, ck)) {
                FayteLog.log("Profiles: loaded \"" + ck + "\"");
            } else if (load(gui, ck.substring(1))) {
                save(ck, whole(), null);
                FayteLog.log("Profiles: started " + ck.substring(1) + " from its saved profile");
            } else if (load(gui, DEFAULT)) {
                save(ck, whole(), null);
                FayteLog.log("Profiles: started " + ck.substring(1) + " from Default");
            } else {
                save(ck, whole(), null);
                FayteLog.log("Profiles: started " + ck.substring(1) + " from the current setup");
            }
        }
        Utils.setpref(OWNER, ck);
        flush();
    }

    private static void flush() {
        try {
            Utils.prefs().flush();
        } catch (Exception e) {
            FayteLog.once("FayteProfiles.flush", e);
        }
    }

    private static long lastauto = 0L;

    public static void autosave() {
        long now = System.currentTimeMillis();
        if (loggedin == null || now - lastauto < 60000L) {
            return;
        }
        lastauto = now;
        save(loggedin, whole(), null);
    }

    public static void onlogout(GameUI gui) {
        if (loggedin == null) {
            return;
        }
        Properties p = whole();
        save(loggedin, p, null);
        save(loggedin.substring(1), p, "replaced at logout");
        loggedin = null;
    }
}
