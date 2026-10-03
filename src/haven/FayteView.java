package haven;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public class FayteView {
    public static final int OTHER = 0;
    public static final int LIFE = 1;
    public static final int TREE = 2;
    public static final int BUSH = 3;
    public static final int BOULDER = 4;
    public static final int CROP = 5;
    public static final int FENCE = 6;
    public static final int DRAW = 0;
    public static final int SKIP = 1;
    public static final int RING = 2;
    public static final int DRAWRING = 3;
    private static final Map<String, Integer> cats = new HashMap<>();
    private static final Map<Gob, ColoredRadius> rings = new WeakHashMap<>();
    private static final ColoredRadius.Cfg ringcfg = new ColoredRadius.Cfg();

    static {
        ringcfg.scol = "30F0EBDD";
        ringcfg.ecol = "A0F0EBDD";
    }

    public static boolean on() {
        return FayteModules.VIEW.on();
    }

    public static int terrain() {
        return on() ? FayteConfig.viewTerrain.get() : 2;
    }

    public static boolean grass() {
        return !on() || FayteConfig.viewGrass.get();
    }

    public static long framems(boolean active) {
        if (!on()) {
            return -1;
        }
        String c = active ? FayteConfig.fpsCap.get() : FayteConfig.fpsBackground.get();
        if ("same".equals(c)) {
            c = FayteConfig.fpsCap.get();
        }
        if ("unlimited".equals(c)) {
            return 1;
        }
        try {
            return Math.max(1, 1000 / Integer.parseInt(c));
        } catch (NumberFormatException e) {
            return 20;
        }
    }

    private static int classify(String n) {
        if (n.startsWith("gfx/borka") || n.startsWith("gfx/kritter")) {
            return LIFE;
        } else if (n.contains("/trees/") || n.contains("/logs/") || n.contains("/stumps/")) {
            return TREE;
        } else if (n.contains("/bushes/")) {
            return BUSH;
        } else if (n.contains("bumling") || n.contains("boulder")) {
            return BOULDER;
        } else if (n.startsWith("gfx/terobjs/plants/")) {
            return CROP;
        } else if (n.contains("palisade") || n.contains("fence") || n.contains("wall")) {
            return FENCE;
        }
        return OTHER;
    }

    public static int cat(Gob g) {
        if (g.getattr(Drawable.class) instanceof Composite) {
            return LIFE;
        }
        String n = FayteMsg.resname(g);
        if (n == null) {
            return -1;
        }
        Integer c = cats.get(n);
        if (c == null) {
            c = classify(n);
            cats.put(n, c);
        }
        return c;
    }

    private static boolean hidden(int c) {
        switch (c) {
            case TREE:
                return FayteConfig.hideTrees.get();
            case BUSH:
                return FayteConfig.hideBushes.get();
            case BOULDER:
                return FayteConfig.hideBoulders.get();
            case CROP:
                return FayteConfig.hideCrops.get();
            case FENCE:
                return FayteConfig.hideFences.get();
            default:
                return false;
        }
    }

    public static int check(Gob g, Coord3f pc, long plid) {
        if (!on() || g.id == plid) {
            return DRAW;
        }
        int c = cat(g);
        if (c < 0 || c == LIFE) {
            return DRAW;
        }
        if (hidden(c)) {
            return RING;
        }
        int lim =
                (c == TREE || c == BUSH || c == BOULDER) ? FayteConfig.distScenery.get() : FayteConfig.distOther.get();
        if (lim > 0 && pc != null) {
            Coord3f gc = g.getc();
            float dx = gc.x - pc.x;
            float dy = gc.y - pc.y;
            float d = lim * MCache.tilesz.x;
            if (dx * dx + dy * dy > d * d) {
                return SKIP;
            }
        }
        return FayteConfig.outlineAll.get() ? DRAWRING : DRAW;
    }

    public static float radius(Gob g) {
        float rad = 5.0f;
        Resource.Neg neg = g.getneg();
        if (neg != null && neg.ep != null) {
            double m = 0;
            for (Coord[] l : neg.ep) {
                if (l != null) {
                    for (Coord p : l) {
                        m = Math.max(m, Math.hypot(p.x, p.y));
                    }
                }
            }
            if (m > 0) {
                rad = (float) Math.min(m, 60);
            }
        }
        return rad;
    }

    public static ColoredRadius ring(Gob g) {
        ColoredRadius r = rings.get(g);
        if (r == null) {
            float rad;
            try {
                rad = radius(g);
            } catch (Loading e) {
                return null;
            }
            ColoredRadius.Cfg cfg = new ColoredRadius.Cfg();
            cfg.scol = ringcfg.scol;
            cfg.ecol = ringcfg.ecol;
            cfg.radius = rad;
            r = new ColoredRadius(g, cfg, 0.5f);
            rings.put(g, r);
        }
        return r;
    }
}
