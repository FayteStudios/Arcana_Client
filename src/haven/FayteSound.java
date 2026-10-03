package haven;

public class FayteSound {
    public static final String[] CATS = {"effects", "ambient", "music", "interface"};
    public static final String[] NAMES = {"Effects", "Ambient", "In-world music", "Interface"};
    public static final String[] DESCS = {
        "Sounds of actions, creatures and objects around you",
        "Wind, water, forests and other background loops",
        "Instruments and music played in the world (e.g. the violin player)",
        "Error beeps, chat notifications and button clicks",
    };
    private static final double[] gains = new double[CATS.length];
    private static boolean loaded = false;

    private static void load() {
        if (!loaded) {
            loaded = true;
            for (int i = 0; i < CATS.length; i++) {
                gains[i] = Utils.getpreff("fayte_vol_" + CATS[i], 1.0f);
            }
        }
    }

    public static int idx(String cat) {
        for (int i = 0; i < CATS.length; i++) {
            if (CATS[i].equals(cat)) {
                return i;
            }
        }
        return 0;
    }

    public static double gain(String cat) {
        load();
        return gains[idx(cat)];
    }

    public static void set(String cat, double v) {
        load();
        v = Math.max(0.0, Math.min(1.0, v));
        gains[idx(cat)] = v;
        Utils.setpreff("fayte_vol_" + cat, (float) v);
    }

    public static String cat(String resname) {
        if (resname == null) {
            return "effects";
        }
        String n = resname.toLowerCase();
        if (n.startsWith("sfx/bgm/")) {
            return "music";
        } else if (n.contains("amb")) {
            return "ambient";
        } else if (n.startsWith("sfx/error")
                || n.contains("/hud/")
                || n.contains("/ui/")
                || n.contains("notif")
                || n.contains("chat")) {
            return "interface";
        }
        return "effects";
    }
}
