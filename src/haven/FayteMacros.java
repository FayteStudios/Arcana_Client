package haven;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class FayteMacros {
    private static Store store = null;

    public static synchronized void reload() {
        store = null;
    }

    public static File datafile() {
        return file();
    }

    private static Macro running = null;
    private static int step = 0;
    private static long nextat = 0L;

    public static class Step {
        public String kind;
        public String value;
        public int delay = 500;

        public Step() {}

        public Step(String kind, String value, int delay) {
            this.kind = kind;
            this.value = value;
            this.delay = delay;
        }
    }

    public static class Macro {
        public String id;
        public String name;
        public String icon;
        public List<Step> steps = new ArrayList<>();
    }

    public static class Store {
        public List<Macro> macros = new ArrayList<>();
    }

    private static File file() {
        return new File(FaytePaths.fayte(), "macros.json");
    }

    private static synchronized Store store() {
        if (store == null) {
            File f = file();
            if (f.exists()) {
                try {
                    store = new Gson()
                            .fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), Store.class);
                } catch (Exception e) {
                    FayteLog.log("Macros: could not read " + f + ": " + e);
                }
            }
            if (store == null) {
                store = new Store();
            }
            if (store.macros == null) {
                store.macros = new ArrayList<>();
            }
        }
        return store;
    }

    public static synchronized void save() {
        File f = file();
        try {
            f.getParentFile().mkdirs();
            FaytePaths.write(
                    f,
                    new GsonBuilder()
                            .setPrettyPrinting()
                            .create()
                            .toJson(store())
                            .getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.log("Macros: could not write " + f + ": " + e);
        }
    }

    public static List<Macro> all() {
        return store().macros;
    }

    public static Macro get(String id) {
        for (Macro m : all()) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    public static Macro create(String name) {
        Macro m = new Macro();
        m.id = Long.toString(System.currentTimeMillis(), 36);
        m.name = name;
        all().add(m);
        save();
        return m;
    }

    public static void remove(Macro m) {
        all().remove(m);
        save();
    }

    public static String describe(GameUI gui, Step s) {
        switch (s.kind) {
            case "act":
                return FayteActs.name(gui, s.value)
                        + (usable(gui, s.value) ? "" : "  (not available to this character)");
            case "say":
                return "Say: " + s.value;
            case "cmd":
                return "Command: " + s.value;
            case "equip":
                return "Equip: " + s.value;
            default:
                return "Wait";
        }
    }

    public static boolean usable(GameUI gui, String id) {
        if (id == null || !id.startsWith("pag:") || gui == null || gui.ui.sess == null) {
            return true;
        }
        String rn = id.substring(4);
        synchronized (gui.ui.sess.glob.paginae) {
            for (Glob.Pagina p : gui.ui.sess.glob.paginae) {
                try {
                    if (rn.equals(p.res().name)) {
                        return true;
                    }
                } catch (Loading e) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean busy() {
        return running != null;
    }

    public static void stop() {
        if (running != null) {
            FayteMsg.say("Macro " + running.name + " stopped.");
        }
        running = null;
    }

    public static void start(GameUI gui, String id) {
        Macro m = get(id);
        if (m == null || m.steps.isEmpty()) {
            return;
        }
        if (running == m) {
            stop();
            return;
        }
        running = m;
        step = 0;
        nextat = 0L;
    }

    public static void tick(GameUI gui) {
        if (running == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextat) {
            return;
        }
        if (step >= running.steps.size()) {
            running = null;
            return;
        }
        Step s = running.steps.get(step++);
        try {
            switch (s.kind) {
                case "act":
                    if (!usable(gui, s.value)) {
                        FayteMsg.say(FayteActs.name(gui, s.value) + " isn't available to this character; skipped.");
                        break;
                    }
                    FayteActs.run(gui, s.value);
                    break;
                case "say":
                    ChatUI.Channel ch = gui.chat == null ? null : gui.chat.sel;
                    if (ch instanceof ChatUI.EntryChannel) {
                        ((ChatUI.EntryChannel) ch).send(s.value);
                    }
                    break;
                case "cmd":
                    gui.ui.cons.run(Utils.splitwords(s.value));
                    break;
                case "equip":
                    FayteTools.equip(gui, s.value);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            FayteMsg.say("Macro step failed: " + e.getMessage(), GameUI.MsgType.BAD);
        }
        nextat = now + Math.max(0, s.delay);
    }
}
