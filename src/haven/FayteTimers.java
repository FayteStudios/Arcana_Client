package haven;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FayteTimers {
    public static final int RATIO = 3;
    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([hms])");
    private static Store store = null;
    private static String loadedfor = null;

    public static class Timer {
        public long id;
        public String name;
        public long duration;
        public long end = -1;
        public boolean popped;
        public boolean done;
        public String who;
    }

    private static final Pattern OWNER = Pattern.compile("^(.*) \\(([^()]+)\\)$");

    public static String me() {
        String c = Config.currentCharName;
        return c == null || c.isEmpty() ? null : c;
    }

    public static boolean mine(FayteTimers.Timer t) {
        return t.who == null || t.who.equals(me());
    }

    private static boolean migrate(Store st) {
        boolean ch = false;
        for (FayteTimers.Timer t : st.timers) {
            if (t.who == null && t.name != null) {
                Matcher m = OWNER.matcher(t.name);
                if (m.matches()) {
                    t.name = m.group(1);
                    t.who = m.group(2);
                    ch = true;
                }
            }
            if (t.name != null && t.name.startsWith("Trade order: next one")) {
                t.name = "Trade Order";
                ch = true;
            } else if (t.name != null && t.name.equals("Next craving (estimate)")) {
                t.name = "Next craving";
                ch = true;
            }
        }
        return ch;
    }

    public static class Store {
        public long nextid = 1;
        public List<FayteTimers.Timer> timers = new ArrayList<>();
        public List<String> alerts = new ArrayList<>();
    }

    public static synchronized boolean alerting(String effect) {
        return effect != null && get().alerts != null && get().alerts.contains(effect);
    }

    public static synchronized boolean togglealert(String effect) {
        if (get().alerts == null) {
            get().alerts = new ArrayList<>();
        }
        boolean on = !get().alerts.remove(effect);
        if (on) {
            get().alerts.add(effect);
        }
        save();
        return on;
    }

    private static File file() {
        return new File(
                new File(FaytePaths.fayte(), "timers"), FaytePaths.safename(String.valueOf(Config.server)) + ".json");
    }

    private static Gson gson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }

    public static synchronized Store get() {
        String k = String.valueOf(Config.server);
        if (store == null || !k.equals(loadedfor)) {
            loadedfor = k;
            store = null;
            File f = file();
            if (f.exists()) {
                try {
                    store = gson().fromJson(
                                    new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), Store.class);
                } catch (Exception e) {
                    FayteLog.log("Timers: could not read " + f + ": " + e);
                }
            }
            if (store == null) {
                store = new Store();
            }
            if (migrate(store)) {
                save();
            }
        }
        return store;
    }

    public static synchronized void save() {
        try {
            File f = file();
            f.getParentFile().mkdirs();
            FaytePaths.write(f, gson().toJson(get()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.log("Timers: could not save: " + e);
        }
    }

    public static long parse(String s) {
        if (s == null) {
            return -1;
        }
        s = s.trim().toLowerCase();
        if (s.matches("\\d+(:\\d+){1,2}")) {
            String[] p = s.split(":");
            long t = 0;
            for (String x : p) {
                t = t * 60 + Long.parseLong(x);
            }
            return (p.length == 2 ? t * 60 : t) * 1000;
        }
        if (s.matches("\\d+")) {
            return Long.parseLong(s) * 60000;
        }
        Matcher m = PART.matcher(s);
        long t = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            long v = Long.parseLong(m.group(1));
            t += m.group(2).equals("h") ? v * 3600000 : m.group(2).equals("m") ? v * 60000 : v * 1000;
        }
        return any ? t : -1;
    }

    public static String fmt(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        long s = (ms + 999) / 1000;
        long h = s / 3600;
        long m = (s / 60) % 60;
        s %= 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    private static Glob glob() {
        UI ui = UI.instance;
        return ui == null || ui.sess == null ? null : ui.sess.glob;
    }

    public static final long OFFLINE = Long.MIN_VALUE;

    public static long remaining(FayteTimers.Timer t) {
        if (t.end < 0) {
            return t.duration;
        }
        Glob g = glob();
        if (g == null) {
            return OFFLINE;
        }
        return (t.end - g.globtime()) / RATIO;
    }

    public static synchronized FayteTimers.Timer add(String name, long duration) {
        return addfor(name, duration, FayteConfig.timersPerChar.get() ? me() : null);
    }

    public static synchronized FayteTimers.Timer addfor(String name, long duration, String who) {
        FayteTimers.Timer t = new FayteTimers.Timer();
        t.id = get().nextid++;
        t.who = who;
        t.name = name;
        t.duration = duration;
        get().timers.add(t);
        save();
        return t;
    }

    public static synchronized FayteTimers.Timer restart(String name, long duration) {
        return restart(name, duration, true);
    }

    public static synchronized FayteTimers.Timer restart(String name, long duration, boolean pop) {
        FayteTimers.Timer t = null;
        String who = me();
        for (FayteTimers.Timer o : get().timers) {
            if (name.equals(o.name) && (who == null ? o.who == null : who.equals(o.who))) {
                t = o;
            }
        }
        if (t == null) {
            t = addfor(name, duration, who);
        }
        t.duration = duration;
        if (pop) {
            t.popped = true;
        }
        start(t);
        return t;
    }

    public static synchronized void start(FayteTimers.Timer t) {
        Glob g = glob();
        if (g == null) {
            return;
        }
        t.end = g.globtime() + t.duration * RATIO;
        t.done = false;
        save();
    }

    public static synchronized void stop(FayteTimers.Timer t) {
        t.end = -1;
        t.done = false;
        save();
    }

    public static synchronized void remove(FayteTimers.Timer t) {
        get().timers.remove(t);
        save();
    }

    public static synchronized void rename(FayteTimers.Timer t, String name) {
        if (name != null && !name.trim().isEmpty()) {
            t.name = name.trim();
            save();
        }
    }

    public static synchronized void setpopped(FayteTimers.Timer t, boolean p) {
        t.popped = p;
        save();
    }

    public static synchronized List<FayteTimers.Timer> all() {
        List<FayteTimers.Timer> l = new ArrayList<>();
        for (FayteTimers.Timer t : get().timers) {
            if (mine(t)) {
                l.add(t);
            }
        }
        return l;
    }

    public static void tick(GameUI gui) {
        boolean changed = false;
        for (FayteTimers.Timer t : all()) {
            if (t.end >= 0 && !t.done) {
                long r = remaining(t);
                if (r != OFFLINE && r <= 0) {
                    t.done = true;
                    changed = true;
                    FayteMsg.say("Timer finished: " + t.name, GameUI.MsgType.GOOD);
                    if ("Next craving".equals(t.name)) {
                        remove(t);
                    }
                }
            }
        }
        if (changed) {
            save();
        }
        FayteTimerChip.sync(gui);
    }
}
