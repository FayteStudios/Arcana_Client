package haven;

import java.util.HashSet;
import java.util.Set;

public class FayteLock {
    private static final String PREF = "fayte_freemove";
    public static final String OTHER = "All other windows";
    public static final String[][] WINDOWS = {
        {"Almanac", "almanac"},
        {"Inspect", "inspect"},
        {"Chat windows", "chat window"},
        {"Selections", "selections"},
        {"Tools & Automation", "tools & automation"},
        {"View & Performance", "view & performance"},
        {"Key Bindings", "key bindings"},
        {"Arcana Modules", "arcana modules"},
        {"Character (game)", "character"},
        {"Kin (game)", "kin"},
        {"Crafting (game)", "crafting"},
        {"Equipment", "equipment"},
        {OTHER, null},
    };
    private static Set<String> free = null;

    private static Set<String> load() {
        if (free == null) {
            free = new HashSet<>();
            String v = Utils.getpref(PREF, "Almanac,Inspect");
            for (String s : v.split(",")) {
                if (!s.trim().isEmpty()) {
                    free.add(s.trim());
                }
            }
        }
        return free;
    }

    public static boolean on(String label) {
        return load().contains(label);
    }

    public static void set(String label, boolean on) {
        Set<String> f = load();
        if (on) {
            f.add(label);
        } else {
            f.remove(label);
        }
        Utils.setpref(PREF, String.join(",", f));
    }

    public static boolean free(Window w) {
        if (w instanceof FayteOptWnd || w instanceof FayteTimersWnd) {
            return true;
        }
        String cap = w.cap == null ? "" : w.cap.text.toLowerCase();
        for (String[] e : WINDOWS) {
            if (e[1] != null && cap.startsWith(e[1])) {
                return on(e[0]);
            }
        }
        return on(OTHER);
    }

    public static class Wnd extends Window {
        private static Wnd instance = null;

        private Wnd(Widget parent) {
            super(
                    new Coord(FayteSkin.s(260), FayteSkin.s(140)),
                    new Coord(FayteSkin.s(260), 30 + WINDOWS.length * FayteSkin.s(20)),
                    parent,
                    "Movable while locked");
            justclose = true;
            new Label(new Coord(0, 0), this, "Inventories and containers always move.");
            int y = FayteSkin.s(20);
            for (String[] e : WINDOWS) {
                final String l = e[0];
                CheckBox cb = new CheckBox(new Coord(0, y), this, l) {
                    @Override
                    public void changed(boolean val) {
                        super.changed(val);
                        FayteLock.set(l, val);
                    }
                };
                cb.a = on(l);
                y += FayteSkin.s(20);
            }
            pack();
        }

        public static void toggle(GameUI gui) {
            if (instance != null && instance.attached()) {
                instance.ui.destroy(instance);
                instance = null;
            } else if (gui != null) {
                instance = new Wnd(gui);
            }
        }

        @Override
        public void destroy() {
            if (instance == this) {
                instance = null;
            }
            super.destroy();
        }

        @Override
        protected boolean compactstyle() {
            return true;
        }
    }
}
