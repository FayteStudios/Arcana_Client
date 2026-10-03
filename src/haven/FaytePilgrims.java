package haven;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public class FaytePilgrims {
    public static final String SELF = "self";
    public static final String[] COLORNAMES = {
        "Default", "White", "Green", "Red", "Blue", "Cyan", "Yellow", "Purple", "Orange"
    };
    private static final long REFRESH = 20L * 60L * 1000L;
    private static Store store = null;
    private static String storeof = null;
    private static File storefile = null;
    private static boolean dirty = false;
    private static long lastcap = 0L;
    private static long lastsave = 0L;
    private static final Map<String, Tex> pics = new HashMap<>();
    private static FaytePortraitCam cam = null;

    public static class Kin {
        public String desc;
        public int color = -1;
        public long seen;
        public long shot;
        public List<String> tabs;
    }

    public static class Store {
        public Map<String, Kin> kin = new HashMap<>();
        public List<String> tabs = new ArrayList<>();
    }

    private static File dir() {
        return new File(FaytePaths.fayte(), "pilgrims");
    }

    private static String who(GameUI gui) {
        return FaytePaths.safename(gui.chrid == null ? "unknown" : gui.chrid);
    }

    private static File file(GameUI gui) {
        return new File(dir(), who(gui) + ".json");
    }

    private static File picfile(GameUI gui, String key) {
        return new File(new File(dir(), who(gui)), FaytePaths.safename(key) + ".png");
    }

    public static synchronized Store get(GameUI gui) {
        String w = gui.chrid == null ? "" : gui.chrid;
        if (store == null || !w.equals(storeof)) {
            flush();
            storeof = w;
            storefile = file(gui);
            store = null;
            pics.clear();
            File f = file(gui);
            if (f.exists()) {
                try {
                    store = new Gson()
                            .fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), Store.class);
                } catch (Exception e) {
                    FayteLog.log("Pilgrims: could not read " + f + ": " + e);
                }
            }
            if (store == null) {
                store = new Store();
            }
            if (store.kin == null) {
                store.kin = new HashMap<>();
            }
            if (store.tabs == null) {
                store.tabs = new ArrayList<>();
            }
        }
        return store;
    }

    public static synchronized Kin kin(GameUI gui, String key) {
        return get(gui).kin.computeIfAbsent(key, k -> new Kin());
    }

    public static String key(BuddyWnd.Buddy b) {
        return "n:" + b.name;
    }

    public static synchronized void renamed(GameUI gui, String from, String to) {
        Store st = get(gui);
        Kin k = st.kin.remove("n:" + from);
        if (k != null) {
            st.kin.put("n:" + to, k);
            File of = picfile(gui, "n:" + from);
            if (of.exists()) {
                of.renameTo(picfile(gui, "n:" + to));
            }
            pics.remove("n:" + from);
            pics.remove("n:" + to);
            changed();
        }
    }

    public static synchronized void changed() {
        dirty = true;
    }

    private static synchronized void save(GameUI gui) {
        flush();
    }

    public static synchronized void flush() {
        if (!dirty || store == null || storefile == null) {
            return;
        }
        dirty = false;
        File f = storefile;
        try {
            f.getParentFile().mkdirs();
            FaytePaths.write(
                    f,
                    new GsonBuilder().setPrettyPrinting().create().toJson(store).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.log("Pilgrims: could not write " + f + ": " + e);
        }
    }

    public static boolean intab(GameUI gui, BuddyWnd.Buddy b, String tab) {
        Kin k = get(gui).kin.get(key(b));
        return k != null && k.tabs != null && k.tabs.contains(tab);
    }

    public static void settab(GameUI gui, BuddyWnd.Buddy b, String tab, boolean in) {
        Kin k = kin(gui, key(b));
        if (k.tabs == null) {
            k.tabs = new ArrayList<>();
        }
        k.tabs.remove(tab);
        if (in) {
            k.tabs.add(tab);
        }
        changed();
    }

    public static void addtab(GameUI gui, String tab) {
        if (!get(gui).tabs.contains(tab)) {
            get(gui).tabs.add(tab);
            changed();
        }
    }

    public static void renametab(GameUI gui, String from, String to) {
        Store s = get(gui);
        int i = s.tabs.indexOf(from);
        if (i < 0 || s.tabs.contains(to)) {
            return;
        }
        s.tabs.set(i, to);
        for (Kin k : s.kin.values()) {
            if (k.tabs != null && k.tabs.remove(from)) {
                k.tabs.add(to);
            }
        }
        changed();
    }

    public static void removetab(GameUI gui, String tab) {
        Store s = get(gui);
        s.tabs.remove(tab);
        for (Kin k : s.kin.values()) {
            if (k.tabs != null) {
                k.tabs.remove(tab);
            }
        }
        changed();
    }

    public static Color color(GameUI gui, BuddyWnd.Buddy b) {
        Kin k = get(gui).kin.get(key(b));
        int c = k == null ? -1 : k.color;
        if (c >= 0 && c < BuddyWnd.gc.length) {
            return BuddyWnd.gc[c];
        }
        return BuddyWnd.gc[Math.max(0, Math.min(BuddyWnd.gc.length - 1, b.group))];
    }

    public static List<BuddyWnd.Buddy> buddies(GameUI gui) {
        List<BuddyWnd.Buddy> l = new ArrayList<>();
        if (gui.buddies != null) {
            for (BuddyWnd.Buddy b : gui.buddies) {
                l.add(b);
            }
        }
        return l;
    }

    private static final Map<String, Tex> selfpics = new HashMap<>();

    public static Tex selfpic(String charname) {
        if (selfpics.containsKey(charname)) {
            return selfpics.get(charname);
        }
        Tex t = null;
        File f = new File(
                new File(new File(FaytePaths.fayte(), "pilgrims"), FaytePaths.safename(charname)), SELF + ".png");
        if (f.exists()) {
            try {
                BufferedImage img = ImageIO.read(f);
                if (img != null) {
                    t = new TexI(img);
                }
            } catch (Exception e) {
                FayteLog.once("FaytePilgrims.selfpic", e);
            }
        }
        selfpics.put(charname, t);
        return t;
    }

    public static Tex portrait(GameUI gui, String key) {
        get(gui);
        if (pics.containsKey(key)) {
            return pics.get(key);
        }
        Tex t = null;
        File f = picfile(gui, key);
        if (f.exists()) {
            try {
                BufferedImage img = ImageIO.read(f);
                if (img != null) {
                    t = new TexI(img);
                }
            } catch (Exception e) {
                FayteLog.log("Pilgrims: could not read " + f + ": " + e);
            }
        }
        pics.put(key, t);
        return t;
    }

    public static void refresh(GameUI gui, String key) {
        Kin k = get(gui).kin.get(key);
        if (k != null) {
            k.shot = 0;
        }
    }

    private static boolean stale(GameUI gui, String key) {
        Kin k = get(gui).kin.get(key);
        return k == null || k.shot == 0 || System.currentTimeMillis() - k.shot > REFRESH;
    }

    public static void tick(GameUI gui) {
        long now = System.currentTimeMillis();
        if (cam != null && (cam.over() || !cam.attached())) {
            if (cam.attached()) {
                gui.ui.destroy(cam);
            }
            cam = null;
        }
        if (now - lastcap > 3000L && gui.map != null && cam == null) {
            lastcap = now;
            Map<String, String> byname = new HashMap<>();
            for (BuddyWnd.Buddy b : buddies(gui)) {
                byname.put(b.name, key(b));
            }
            String pick = null;
            long gob = -1;
            Gob pl = gui.map.player();
            if (pl != null && stale(gui, SELF)) {
                pick = SELF;
                gob = pl.id;
            }
            if (pick == null) {
                synchronized (gui.ui.sess.glob.oc) {
                    for (Gob g : gui.ui.sess.glob.oc) {
                        KinInfo ki = g.getattr(KinInfo.class);
                        String k = ki == null ? null : byname.get(ki.name);
                        if (k != null) {
                            kin(gui, k).seen = now;
                            if (stale(gui, k)) {
                                pick = k;
                                gob = g.id;
                                break;
                            }
                        }
                    }
                }
            }
            if (pick != null) {
                final String key = pick;
                cam = new FaytePortraitCam(gui, gob, (img) -> {
                    if (img != null) {
                        File f = picfile(gui, key);
                        try {
                            f.getParentFile().mkdirs();
                            ImageIO.write(img, "PNG", f);
                            Tex old = pics.remove(key);
                            if (old != null) {
                                old.dispose();
                            }
                        } catch (Exception e) {
                            FayteLog.log("Pilgrims: could not save " + f + ": " + e);
                        }
                    }
                    kin(gui, key).shot =
                            img != null ? System.currentTimeMillis() : System.currentTimeMillis() - REFRESH + 60000L;
                    changed();
                });
            }
        }
        if (now - lastsave > 5000L) {
            lastsave = now;
            save(gui);
        }
    }
}
