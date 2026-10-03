package haven;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FaytePlacer {
    private static final long SETTLE = 1500;
    private static final List<FaytePlacer.Entry> placing = new ArrayList<>();
    private static Coord anchor = null;
    private static long anchoruntil = 0;

    private static class Entry {
        final WeakReference<Window> w;
        final Coord want;
        final long until;
        Coord lastset = null;
        Coord lastsz = null;

        Entry(Window w, Coord want) {
            this.w = new WeakReference<>(w);
            this.want = want;
            until = System.currentTimeMillis() + SETTLE;
        }
    }

    public static void anchor(Coord c) {
        anchor = c;
        anchoruntil = System.currentTimeMillis() + 5000;
    }

    public static void enqueue(Window w) {
        Coord want = w.c;
        if (anchor != null && System.currentTimeMillis() < anchoruntil) {
            want = anchor;
        }
        placing.add(new FaytePlacer.Entry(w, want));
    }

    private static boolean overlaps(Coord c, Coord sz, Coord oc, Coord osz) {
        return c.x < oc.x + osz.x && oc.x < c.x + sz.x && c.y < oc.y + osz.y && oc.y < c.y + sz.y;
    }

    private static boolean free(Coord c, Coord sz, Coord psz, List<Window> obst) {
        if (c.x < 0 || c.y < 0 || c.x + sz.x > psz.x || c.y + sz.y > psz.y) {
            return false;
        }
        for (Window o : obst) {
            if (overlaps(c, sz, o.c, wide(o))) {
                return false;
            }
        }
        return true;
    }

    private static Coord wide(Window w) {
        return w.sz.add(FayteAbacus.extra(w), 0);
    }

    private static Coord place(Window w, Coord want, Coord psz, List<Window> obst) {
        Coord sz = wide(w);
        if (free(want, sz, psz, obst)) {
            return want;
        }
        List<Coord> cand = new ArrayList<>();
        for (Window o : obst) {
            Coord osz = wide(o);
            cand.add(new Coord(o.c.x + osz.x, o.c.y));
            cand.add(new Coord(o.c.x, o.c.y + osz.y));
            cand.add(new Coord(o.c.x - sz.x, o.c.y));
            cand.add(new Coord(o.c.x, o.c.y - sz.y));
            cand.add(new Coord(o.c.x + osz.x, o.c.y + osz.y - sz.y));
            cand.add(new Coord(o.c.x + osz.x - sz.x, o.c.y + osz.y));
        }
        Coord best = null;
        double bd = Double.MAX_VALUE;
        for (Coord c : cand) {
            if (free(c, sz, psz, obst)) {
                double d = c.dist(want);
                if (d < bd) {
                    bd = d;
                    best = c;
                }
            }
        }
        if (best == null) {
            int step = 10;
            for (int y = 0; y + sz.y <= psz.y; y += step) {
                for (int x = 0; x + sz.x <= psz.x; x += step) {
                    Coord c = new Coord(x, y);
                    if (free(c, sz, psz, obst)) {
                        double d = c.dist(want);
                        if (d < bd) {
                            bd = d;
                            best = c;
                        }
                    }
                }
            }
        }
        return best != null ? best : GameUI.onScreen(want, sz, psz);
    }

    public static void tick(GameUI gui) {
        if (placing.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Window> settled = new ArrayList<>();
        for (Widget c = gui.child; c != null; c = c.next) {
            if (c instanceof Window && c.visible && ((Window) c).hasinv() && !queued((Window) c)) {
                settled.add((Window) c);
            }
        }
        Iterator<FaytePlacer.Entry> it = placing.iterator();
        while (it.hasNext()) {
            FaytePlacer.Entry e = it.next();
            Window w = e.w.get();
            if (w == null || !w.linked() || w.dm || w.pinned || now > e.until) {
                it.remove();
                continue;
            }
            if (!w.visible || w.sz.x <= 0 || w.sz.y <= 0) {
                continue;
            }
            if (e.lastset != null && !e.lastset.equals(w.c)) {
                it.remove();
                continue;
            }
            if (e.lastset != null && w.sz.equals(e.lastsz)) {
                settled.add(w);
                continue;
            }
            e.lastsz = w.sz;
            Coord nc = GameUI.onScreen(place(w, GameUI.onScreen(e.want, w.sz, gui.sz), gui.sz, settled), w.sz, gui.sz);
            if (!nc.equals(w.c)) {
                w.c = nc;
            }
            e.lastset = nc;

            settled.add(w);
        }
    }

    private static boolean queued(Window w) {
        for (FaytePlacer.Entry e : placing) {
            if (e.w.get() == w) {
                return true;
            }
        }
        return false;
    }
}
