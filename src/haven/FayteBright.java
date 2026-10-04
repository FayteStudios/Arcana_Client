package haven;

import haven.resutil.CaveTile;

public class FayteBright {
    private static Float surface = null;
    private static Float cave = null;
    private static final String CAVEFLOOR = "gfx/tiles/mountain";
    private static Boolean incave = null;
    private static GameUI lastgui = null;
    private static Coord lastpg = null;
    private static long last = 0L;

    public static float surface() {
        if (surface == null) {
            surface = Utils.getpreff("fayte_bright_surface", Config.brighten);
        }
        return surface;
    }

    public static float cave() {
        if (cave == null) {
            cave = Utils.getpreff("fayte_bright_cave", Config.brighten);
        }
        return cave;
    }

    public static void set(boolean c, float v, GameUI gui) {
        v = Math.max(0f, Math.min(1f, v));
        if (c) {
            cave = v;
            Utils.setpreff("fayte_bright_cave", v);
        } else {
            surface = v;
            Utils.setpreff("fayte_bright_surface", v);
        }
        last = 0L;
        apply(gui);
    }

    public static boolean incave(GameUI gui) {
        return Boolean.TRUE.equals(incave);
    }

    private static boolean detect(GameUI gui) {
        if (gui.map == null) {
            return false;
        }
        Gob pl = gui.map.player();
        if (pl == null || pl.rc == null) {
            return Boolean.TRUE.equals(incave);
        }
        try {
            MCache map = gui.ui.sess.glob.map;
            Coord tc = pl.rc.div(MCache.tilesz);
            Coord pg = tc.div(MCache.cmaps);
            int t = map.gettile(tc);
            if (map.tiler(t) instanceof CaveTile) {
                lastgui = gui;
                lastpg = pg;
                return true;
            }
            boolean jumped = gui != lastgui || lastpg == null || FayteMapStore.bigjump(lastpg, pg);
            Resource r = map.tilesetr(t);
            lastgui = gui;
            lastpg = pg;
            if (!jumped) {
                return Boolean.TRUE.equals(incave);
            }
            return r != null && CAVEFLOOR.equals(r.name);
        } catch (Loading e) {
            return Boolean.TRUE.equals(incave);
        } catch (RuntimeException e) {
            return Boolean.TRUE.equals(incave);
        }
    }

    public static void apply(GameUI gui) {
        if (gui == null) {
            return;
        }
        boolean c = detect(gui);
        incave = c;
        float want = c ? cave() : surface();
        if (Config.brighten != want) {
            Config.brighten = want;
            gui.ui.sess.glob.brighten();
        }
    }

    public static void tick(GameUI gui) {
        long now = System.currentTimeMillis();
        if (now - last > 1000L) {
            last = now;
            apply(gui);
        }
    }
}
