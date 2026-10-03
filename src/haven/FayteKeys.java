package haven;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FayteKeys {
    public static final int SHIFT = 1;
    public static final int CTRL = 2;
    public static final int ALT = 4;
    public static final String GAME = "Game";
    public static final String BELTS = "Belts";
    public static final String FORK = "Arcana windows";
    public static final String GRID = "Action grid";
    private static final List<FayteKeys.Action> fixed = new ArrayList<>();
    private static final Map<String, int[]> binds = new LinkedHashMap<>();
    private static final Map<String, String> gridnames = new LinkedHashMap<>();
    private static boolean loaded = false;
    private static boolean emulating = false;
    private static int swallow = 0;
    private static String capture = null;
    private static String capturelabel = null;

    private static final Map<String, String> DESCS = new HashMap<>();

    static {
        DESCS.put("game.inventory", "Open or close your inventory.");
        DESCS.put("game.equipment", "Open or close the equipment window.");
        DESCS.put("game.study", "Open or close the game's character / study window.");
        DESCS.put("game.buddies", "Open the kin list (Pilgrims when the Almanac module is on).");
        DESCS.put("game.town", "Open the town window.");
        DESCS.put("game.options", "Open Options (Arcana Options when the Arcana UI is on).");
        DESCS.put("game.chat", "Toggle the size of the classic chat.");
        DESCS.put("game.landscape", "The game's flatness (Landscape) tool.");
        DESCS.put("game.stance", "Toggle the current combat maneuver.");
        DESCS.put("game.overview", "Inventory abacus: count what you carry.");
        DESCS.put("game.cartographer", "Latikai's cartographer (unfinished).");
        DESCS.put("game.darkness", "Shows the current light / darkness level.");
        DESCS.put("game.night", "Toggle forced bright display (old night vision).");
        DESCS.put("game.toolbelt", "Show or hide the old toolbelt.");
        DESCS.put("game.backpack", "Open or close the backpack.");
        DESCS.put("game.center", "Toggle clicking on tile centres.");
        DESCS.put("game.radius", "Show effect radii of braziers, mine supports and similar.");
        DESCS.put("game.craft", "Latikai's crafting window.");
        DESCS.put("game.filter", "Latikai's item filter window.");
        DESCS.put("cmd:worldmap", "Open the world map.");
        DESCS.put("cmd:almanac", "Open the Almanac.");
        DESCS.put("cmd:chatwindow", "Open a new Arcana chat window.");
        DESCS.put("cmd:classicchat", "Show or hide the classic chat.");
        DESCS.put("cmd:lockui", "Lock or unlock moving windows and HUD pieces.");
        DESCS.put("cmd:gatherwindows", "Bring every window back onto the screen.");
        DESCS.put("cmd:invpack", "Open your inventory and backpack together.");
        DESCS.put("cmd:smart", "Use the nearest herb, item or object around you (set what counts in Tools).");
        DESCS.put("cmd:cancel", "Cancel the current tool or put down what you carry.");
        DESCS.put("cmd:keybinds", "Open this window.");
        DESCS.put("cmd:actions", "Open the Actions panel (actions, toggles, bars and macros).");
        DESCS.put("cmd:options", "Open Arcana Options.");
        DESCS.put("cmd:pilgrims", "Open Pilgrims, the book of kin.");
        DESCS.put("cmd:skills", "Open Skills & Proficiencies.");
        DESCS.put("cmd:recipes", "Open Recipes in the Almanac.");
    }

    public static String desc(Action a) {
        if (a.id.startsWith("belt.f")) {
            return "Use slot " + a.id.substring(6) + " of the game's F-belt (hidden while our bars are used).";
        } else if (a.id.startsWith("belt.n")) {
            return "Use slot " + a.id.substring(6) + " of the game's number belt (hidden while our bars are used).";
        }
        return DESCS.get(a.id);
    }

    public static class Action {
        public final String id;
        public final String label;
        public final String group;
        public final int dcode;
        public final int dmods;
        public int fcode = 0;
        public int fmods = 0;

        Action(String id, String label, String group, int dcode, int dmods) {
            this.id = id;
            this.label = label;
            this.group = group;
            this.dcode = dcode;
            this.dmods = dmods;
        }

        public boolean builtin() {
            return dcode != 0;
        }
    }

    private static void game(String id, String label, int code, int mods) {
        fixed.add(new FayteKeys.Action("game." + id, label, GAME, code, mods));
    }

    private static void fork(String cmd, String label) {
        fixed.add(new FayteKeys.Action("cmd:" + cmd, label, FORK, 0, 0));
    }

    private static void fork(String cmd, String label, int code, int mods) {
        FayteKeys.Action a = new FayteKeys.Action("cmd:" + cmd, label, FORK, 0, 0);
        a.fcode = code;
        a.fmods = mods;
        fixed.add(a);
    }

    static {
        game("inventory", "Inventory", KeyEvent.VK_TAB, 0);
        game("equipment", "Equipment", KeyEvent.VK_E, CTRL);
        game("study", "Character / study", KeyEvent.VK_T, CTRL);
        game("buddies", "Kin list", KeyEvent.VK_B, CTRL);
        game("options", "Options", KeyEvent.VK_O, CTRL);
        game("stance", "Toggle maneuver", KeyEvent.VK_S, CTRL);
        game("overview", "Abacus", KeyEvent.VK_A, CTRL);
        game("darkness", "Darkness window", KeyEvent.VK_D, CTRL);
        game("night", "Night vision mode", KeyEvent.VK_N, CTRL);
        game("toolbelt", "Toolbelt window", KeyEvent.VK_R, CTRL);
        game("backpack", "Backpack", KeyEvent.VK_G, CTRL);
        game("center", "Tile centering", KeyEvent.VK_Z, CTRL);
        game("radius", "Radius", KeyEvent.VK_R, ALT);

        for (int i = 0; i < 12; i++) {
            fixed.add(new FayteKeys.Action("belt.f" + (i + 1), "F-belt slot " + (i + 1), BELTS, KeyEvent.VK_F1 + i, 0));
        }
        int[] nk = {
            KeyEvent.VK_1,
            KeyEvent.VK_2,
            KeyEvent.VK_3,
            KeyEvent.VK_4,
            KeyEvent.VK_5,
            KeyEvent.VK_6,
            KeyEvent.VK_7,
            KeyEvent.VK_8,
            KeyEvent.VK_9,
            KeyEvent.VK_0,
            KeyEvent.VK_MINUS,
            KeyEvent.VK_EQUALS
        };

        for (int i = 0; i < nk.length; i++) {
            fixed.add(new FayteKeys.Action("belt.n" + (i + 1), "Number belt slot " + (i + 1), BELTS, nk[i], 0));
        }
        fork("worldmap", "World Map");
        fork("almanac", "Almanac");
        fork("chatwindow", "New Chat Window");
        fork("classicchat", "Classic chat on/off");
        fork("lockui", "Lock UI on/off");
        fork("gatherwindows", "Gather windows");
        fork("invpack", "Inventory + backpack");
        fork("smart", "Smart interact", KeyEvent.VK_SPACE, 0);
        fork("cancel", "Cancel action / put down (right-click ground)");
        fork("keybinds", "Key Bindings");
        fork("actions", "Actions panel", KeyEvent.VK_A, ALT);
        fork("options", "Arcana Options");
        fork("pilgrims", "Pilgrims (kin)");
        fork("skills", "Skills & Proficiencies");
        fork("recipes", "Recipes");
    }

    private static File file() {
        return new File(FaytePaths.fayte(), "keybinds.txt");
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File f = file();
        if (!f.exists()) {
            return;
        }
        try {
            for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                String[] p = line.trim().split("\t");
                if (p.length >= 3 && !line.startsWith("#")) {
                    binds.put(p[0], new int[] {Integer.parseInt(p[1]), Integer.parseInt(p[2])});
                    if (p[0].startsWith("pag:") && p.length >= 4) {
                        gridnames.put(p[0], p[3]);
                    }
                }
            }
        } catch (Exception e) {
            FayteLog.log("Keys: could not read " + f + ": " + e);
        }
    }

    private static synchronized void save() {
        StringBuilder sb =
                new StringBuilder("# Fayte key bindings: action, key code, modifiers (1 shift, 2 ctrl, 4 alt), name\n");

        for (Map.Entry<String, int[]> e : binds.entrySet()) {
            String name = gridnames.containsKey(e.getKey())
                    ? gridnames.get(e.getKey())
                    : combo(e.getValue()[0], e.getValue()[1]);
            sb.append(e.getKey())
                    .append('\t')
                    .append(e.getValue()[0])
                    .append('\t')
                    .append(e.getValue()[1])
                    .append('\t')
                    .append(name)
                    .append('\n');
        }
        try {
            file().getParentFile().mkdirs();
            FaytePaths.write(file(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.log("Keys: could not save: " + e);
        }
    }

    public static synchronized List<FayteKeys.Action> actions() {
        load();
        List<FayteKeys.Action> ret = new ArrayList<>(fixed);

        for (Map.Entry<String, String> e : gridnames.entrySet()) {
            if (binds.containsKey(e.getKey())) {
                ret.add(new FayteKeys.Action(e.getKey(), e.getValue(), GRID, 0, 0));
            }
        }
        return ret;
    }

    public static synchronized int[] effective(FayteKeys.Action a) {
        load();
        int[] b = binds.get(a.id);
        if (b != null) {
            return b;
        } else if (a.builtin()) {
            return new int[] {a.dcode, a.dmods};
        } else {
            return a.fcode != 0 ? new int[] {a.fcode, a.fmods} : null;
        }
    }

    public static boolean changed(FayteKeys.Action a) {
        int[] e = effective(a);
        return a.builtin() && (e == null || e[0] != a.dcode || e[1] != a.dmods);
    }

    public static String combo(int[] c) {
        return c == null || c[0] == 0 ? "\u2014" : combo(c[0], c[1]);
    }

    public static String combo(int code, int mods) {
        if (code == 0) {
            return "\u2014";
        }
        StringBuilder sb = new StringBuilder();
        if ((mods & CTRL) != 0) {
            sb.append("Ctrl+");
        }
        if ((mods & ALT) != 0) {
            sb.append("Alt+");
        }
        if ((mods & SHIFT) != 0) {
            sb.append("Shift+");
        }
        return sb.append(KeyEvent.getKeyText(code)).toString();
    }

    private static int mods(KeyEvent ev) {
        return (ev.isShiftDown() ? SHIFT : 0)
                | (ev.isControlDown() ? CTRL : 0)
                | (ev.isAltDown() || ev.isMetaDown() ? ALT : 0);
    }

    private static boolean modkey(int code) {
        return code == KeyEvent.VK_SHIFT
                || code == KeyEvent.VK_CONTROL
                || code == KeyEvent.VK_ALT
                || code == KeyEvent.VK_META
                || code == KeyEvent.VK_ALT_GRAPH;
    }

    public static synchronized void startcapture(String id, String label) {
        capture = id;
        capturelabel = label;
        FayteMsg.say("Press a key for " + label + " (Esc cancels, Backspace removes the key)");
    }

    public static String capturing() {
        return capture;
    }

    public static void bindgrid(Glob.Pagina p) {
        String name = p.res().name;
        String label = name;
        try {
            label = p.res().layer(Resource.action).name;
        } catch (Exception e) {
            FayteLog.once("FayteKeys.bindgrid", e);
        }

        synchronized (FayteKeys.class) {
            load();
            gridnames.put("pag:" + name, label);
        }
        startcapture("pag:" + name, label);
    }

    public static synchronized void reset(FayteKeys.Action a) {
        load();
        binds.remove(a.id);
        if (a.id.startsWith("pag:")) {
            gridnames.remove(a.id);
        }
        save();
    }

    private static synchronized void bind(String id, int code, int mods) {
        load();
        FayteKeys.Action self = null;

        for (FayteKeys.Action a : actions()) {
            if (a.id.equals(id)) {
                self = a;
            }
        }
        if (code != 0) {
            for (FayteKeys.Action a : actions()) {
                int[] e = effective(a);
                if (!a.id.equals(id) && e != null && e[0] == code && e[1] == mods) {
                    binds.put(a.id, new int[] {0, 0});
                    FayteMsg.say(a.label + " no longer has a key (" + combo(code, mods) + " moved)");
                }
            }
        }
        if (self != null && self.builtin() && code == self.dcode && mods == self.dmods) {
            binds.remove(id);
        } else if (code == 0 && self != null && !self.builtin() && self.fcode == 0) {
            binds.remove(id);
            gridnames.remove(id);
        } else {
            binds.put(id, new int[] {code, mods});
        }
        save();
    }

    private static boolean texting(UI ui) {
        if (ui.keygrabbed()) {
            return true;
        }
        Widget f = ui.root;
        while (f != null && f.focused != null) {
            f = f.focused;
        }
        if (f instanceof TextEntry) {
            TextEntry t = (TextEntry) f;
            return !t.clicktotype || TextEntry.armed == t;
        }
        return f instanceof FayteTextArea;
    }

    public static boolean keydown(UI ui, KeyEvent ev) {
        if (emulating) {
            return false;
        }
        int code = ev.getKeyCode();
        int mods = mods(ev);
        String cap;
        synchronized (FayteKeys.class) {
            cap = capture;
        }
        if (cap != null) {
            if (modkey(code)) {
                return true;
            }
            String label = capturelabel;
            synchronized (FayteKeys.class) {
                capture = null;
                capturelabel = null;
            }
            swallow = code;
            swallowtype = true;
            if (code == KeyEvent.VK_ESCAPE) {
                FayteMsg.say("Key binding cancelled");
                if (cap.startsWith("pag:") && !binds.containsKey(cap)) {
                    synchronized (FayteKeys.class) {
                        gridnames.remove(cap);
                    }
                }
            } else if (code == KeyEvent.VK_BACK_SPACE || code == KeyEvent.VK_DELETE) {
                bind(cap, 0, 0);
                FayteMsg.say(label + " has no key now");
            } else {
                bind(cap, code, mods);
                FayteMsg.say(label + " is now on " + combo(code, mods));
            }
            return true;
        }
        boolean plain = (mods & (CTRL | ALT)) == 0 && !(code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F12);
        if (modkey(code) || (plain && texting(ui))) {
            return false;
        }
        FayteKeys.Action hit = null;
        boolean blocked = false;

        for (FayteKeys.Action a : actions()) {
            int[] e = effective(a);
            if (e != null && e[0] == code && e[1] == mods) {
                hit = a;
                break;
            }
            if (a.builtin() && a.dcode == code && a.dmods == mods && changed(a)) {
                blocked = true;
            }
        }
        if (hit == null) {
            if (blocked) {
                swallow = code;
                swallowtype = true;
                return true;
            }
            return false;
        }
        if (hit.builtin() && !changed(hit)) {
            return false;
        }
        swallow = code;
        swallowtype = true;
        run(ui, hit, ev.getComponent());
        return true;
    }

    private static boolean swallowtype = false;

    public static boolean type(UI ui, KeyEvent ev) {
        if (emulating || !swallowtype) {
            return false;
        }
        swallowtype = false;
        return true;
    }

    static void reset() {
        swallow = 0;
        swallowtype = false;
    }

    public static boolean keyup(UI ui, KeyEvent ev) {
        if (emulating || swallow == 0 || ev.getKeyCode() != swallow) {
            return false;
        }
        swallow = 0;
        swallowtype = false;
        return true;
    }

    private static int awtmods(int mods) {
        int m = 0;
        if ((mods & SHIFT) != 0) {
            m |= InputEvent.SHIFT_DOWN_MASK | InputEvent.SHIFT_MASK;
        }
        if ((mods & CTRL) != 0) {
            m |= InputEvent.CTRL_DOWN_MASK | InputEvent.CTRL_MASK;
        }
        if ((mods & ALT) != 0) {
            m |= InputEvent.ALT_DOWN_MASK | InputEvent.ALT_MASK;
        }
        return m;
    }

    private static char keychar(int code, int mods) {
        if (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z) {
            if ((mods & CTRL) != 0) {
                return (char) (code - 64);
            }
            return (mods & SHIFT) != 0 ? (char) code : Character.toLowerCase((char) code);
        } else if (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9) {
            return (mods & CTRL) != 0 ? KeyEvent.CHAR_UNDEFINED : (char) code;
        } else if (code == KeyEvent.VK_TAB) {
            return '\t';
        } else if (code == KeyEvent.VK_SPACE) {
            return ' ';
        } else if (code == KeyEvent.VK_ENTER) {
            return '\n';
        } else if (code == KeyEvent.VK_MINUS) {
            return '-';
        } else if (code == KeyEvent.VK_EQUALS) {
            return '=';
        } else {
            return KeyEvent.CHAR_UNDEFINED;
        }
    }

    private static void run(UI ui, FayteKeys.Action a, Component src) {
        try {
            if (a.builtin()) {
                emulate(ui, a.dcode, a.dmods, src);
            } else if (a.id.startsWith("cmd:")) {
                ui.cons.run(new String[] {a.id.substring(4)});
            } else if (a.id.startsWith("pag:") && ui.gui != null && ui.gui.menu != null) {
                ui.gui.menu.use(ui.sess.glob.paginafor(Resource.load(a.id.substring(4))));
            }
        } catch (Exception e) {
            FayteMsg.say("Could not run " + a.label + ": " + e.getMessage(), GameUI.MsgType.BAD);
        }
    }

    private static void emulate(UI ui, int code, int mods, Component src) {
        long now = System.currentTimeMillis();
        int am = awtmods(mods);
        char ch = keychar(code, mods);
        emulating = true;

        try {
            ui.keydown(new KeyEvent(src, KeyEvent.KEY_PRESSED, now, am, code, ch));
            if (ch != KeyEvent.CHAR_UNDEFINED) {
                ui.type(new KeyEvent(src, KeyEvent.KEY_TYPED, now, am, KeyEvent.VK_UNDEFINED, ch));
            }
            ui.keyup(new KeyEvent(src, KeyEvent.KEY_RELEASED, now, am, code, ch));
        } finally {
            emulating = false;
        }
    }
}
