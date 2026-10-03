package haven;

import java.util.Map;
import java.util.WeakHashMap;

public class WindowSnap {
    public static final int RIGHT = 1;
    public static final int BOTTOM = 2;
    private static final Map<Widget, Integer> anchors = new WeakHashMap<>();

    public static boolean on() {
        return FayteModules.STYLE.on() && FayteConfig.windowSnap.get();
    }

    private static boolean candidate(Widget w, Widget parent, Widget o) {
        return o != w
                && o.visible
                && o.sz.x > 0
                && o.sz.y > 0
                && !o.sz.equals(parent.sz)
                && (o instanceof Window
                        || o instanceof MenuGrid
                        || o instanceof GameUI.MainMenu
                        || o instanceof ChatUI
                        || FayteHud.is(o));
    }

    private static int near(int v, int[] targets, int dist) {
        int best = Integer.MIN_VALUE;
        int bd = dist + 1;

        for (int t : targets) {
            int d = Math.abs(v - t);
            if (d < bd) {
                bd = d;
                best = t;
            }
        }
        return best;
    }

    public static Coord snap(Widget w, Coord want) {
        return w.parent == null ? want : snap(w.parent, w, w.sz, want);
    }

    public static Coord snap(Widget parent, Widget w, Coord sz, Coord want) {
        if (!on() || parent == null) {
            return want;
        }
        int dist = FayteConfig.snapDist.get();
        int grid = FayteConfig.snapGrid.get();
        Coord psz = parent.sz;
        int nx = Integer.MIN_VALUE;
        int ny = Integer.MIN_VALUE;
        int bdx = dist + 1;
        int bdy = dist + 1;
        int[] xs = {0, psz.x};
        int[] ys = {0, psz.y};

        for (int i = 0; i < 2; i++) {
            int sx = near(want.x, new int[] {xs[i]}, dist);
            if (sx != Integer.MIN_VALUE && Math.abs(want.x - sx) < bdx) {
                bdx = Math.abs(want.x - sx);
                nx = sx;
            }
            sx = near(want.x + sz.x, new int[] {xs[i]}, dist);
            if (sx != Integer.MIN_VALUE && Math.abs(want.x + sz.x - sx) < bdx) {
                bdx = Math.abs(want.x + sz.x - sx);
                nx = sx - sz.x;
            }
            int sy = near(want.y, new int[] {ys[i]}, dist);
            if (sy != Integer.MIN_VALUE && Math.abs(want.y - sy) < bdy) {
                bdy = Math.abs(want.y - sy);
                ny = sy;
            }
            sy = near(want.y + sz.y, new int[] {ys[i]}, dist);
            if (sy != Integer.MIN_VALUE && Math.abs(want.y + sz.y - sy) < bdy) {
                bdy = Math.abs(want.y + sz.y - sy);
                ny = sy - sz.y;
            }
        }
        for (Widget o = parent.child; o != null; o = o.next) {
            if (candidate(w, parent, o)) {
                boolean vov = want.y < o.c.y + o.sz.y + dist && want.y + sz.y > o.c.y - dist;
                boolean hov = want.x < o.c.x + o.sz.x + dist && want.x + sz.x > o.c.x - dist;
                if (vov) {
                    int[][] pairs = {
                        {want.x, o.c.x + o.sz.x, 0},
                        {want.x + sz.x, o.c.x, sz.x},
                        {want.x, o.c.x, 0},
                        {want.x + sz.x, o.c.x + o.sz.x, sz.x}
                    };
                    for (int[] p : pairs) {
                        int d = Math.abs(p[0] - p[1]);
                        if (d < bdx) {
                            bdx = d;
                            nx = p[1] - p[2];
                        }
                    }
                }
                if (hov) {
                    int[][] pairs = {
                        {want.y, o.c.y + o.sz.y, 0},
                        {want.y + sz.y, o.c.y, sz.y},
                        {want.y, o.c.y, 0},
                        {want.y + sz.y, o.c.y + o.sz.y, sz.y}
                    };
                    for (int[] p : pairs) {
                        int d = Math.abs(p[0] - p[1]);
                        if (d < bdy) {
                            bdy = d;
                            ny = p[1] - p[2];
                        }
                    }
                }
            }
        }
        if (nx == Integer.MIN_VALUE) {
            nx = Math.round((float) want.x / grid) * grid;
        }
        if (ny == Integer.MIN_VALUE) {
            ny = Math.round((float) want.y / grid) * grid;
        }
        Coord ret = new Coord(nx, ny);
        int a = 0;
        if (ret.x + sz.x == psz.x && ret.x != 0) {
            a |= RIGHT;
        }
        if (ret.y + sz.y == psz.y && ret.y != 0) {
            a |= BOTTOM;
        }
        if (w == null) {
            return ret;
        } else if (a == 0) {
            anchors.remove(w);
        } else {
            anchors.put(w, a);
        }
        return ret;
    }

    public static void resized(Widget parent, Coord osz, Coord nsz) {
        if (osz == null || osz.equals(nsz)) {
            return;
        }
        Coord d = nsz.sub(osz);

        for (Map.Entry<Widget, Integer> e : anchors.entrySet()) {
            Widget w = e.getKey();
            if (w != null && w.parent == parent) {
                int a = e.getValue();
                w.c = w.c.add((a & RIGHT) != 0 ? d.x : 0, (a & BOTTOM) != 0 ? d.y : 0);
                if (w instanceof Window) {
                    ((Window) w).storeOpt("_pos", w.c);
                } else if (w instanceof MenuGrid) {
                    FayteConfig.actionGridPos.set(w.c);
                } else if (w instanceof GameUI.MainMenu) {
                    FayteConfig.buttonPanelPos.set(w.c);
                }
            }
        }
    }
}
