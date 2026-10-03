package haven;

import java.util.HashMap;
import java.util.Map;

public class FayteScaleConfirm extends Window {
    private static final long WAIT = 15000L;
    private static FayteScaleConfirm current = null;
    private final double old;
    private final long start;
    private final Label count;
    private boolean placed = false;
    private static Map<Widget, Coord> snap = null;
    private static Coord snapsz = null;
    private static boolean restoring = false;
    private static boolean spread = false;

    private static void snapshot(GameUI gui) {
        snap = new HashMap<>();
        snapsz = gui.sz;
        for (Widget w = gui.child; w != null; w = w.next) {
            if ((w instanceof Window || w instanceof FayteChatWindow)
                    && !(w instanceof FayteScaleConfirm)
                    && !FayteHud.is(w)) {
                snap.put(w, w.c);
            }
        }
    }

    public static void follow(GameUI gui) {
        if (snap == null || snapsz == null) {
            return;
        }
        if (restoring) {
            if (gui.sz.equals(snapsz)) {
                for (Map.Entry<Widget, Coord> e : snap.entrySet()) {
                    if (e.getKey().attached()) {
                        e.getKey().c = e.getValue();
                    }
                }
                snap = null;
                restoring = false;
            }
        } else if (spread && !gui.sz.equals(snapsz)) {
            for (Map.Entry<Widget, Coord> e : snap.entrySet()) {
                Widget w = e.getKey();
                if (w.attached()) {
                    Coord o = e.getValue();
                    int nx = (int) Math.round((o.x + w.sz.x / 2.0) * gui.sz.x / (double) snapsz.x - w.sz.x / 2.0);
                    int ny = (int) Math.round((o.y + w.sz.y / 2.0) * gui.sz.y / (double) snapsz.y - w.sz.y / 2.0);
                    w.c = GameUI.onScreen(new Coord(nx, ny), w.sz, gui.sz);
                }
            }
            spread = false;
        }
    }

    public static void apply(GameUI gui, double v) {
        double old = FayteConfig.uiScale.get();
        if (current != null && current.attached()) {
            old = current.old;
            current.ui.destroy(current);
        }
        if (Math.abs(v - old) < 0.001) {
            return;
        }
        if (snap == null) {
            snapshot(gui);
        }
        spread = true;
        restoring = false;
        FayteConfig.uiScale.set(v);
        current = new FayteScaleConfirm(gui, old);
    }

    private FayteScaleConfirm(GameUI gui, double old) {
        super(Coord.z, new Coord(FayteSkin.s(300), FayteSkin.s(96)), gui, "Keep this scale?");
        this.old = old;
        start = System.currentTimeMillis();
        justclose = true;
        new Label(
                new Coord(0, 0), this, String.format("Interface scale is now %.2f\u00d7.", FayteConfig.uiScale.get()));
        count = new Label(new Coord(0, FayteSkin.labelf.height() + 4), this, "");
        int by = FayteSkin.labelf.height() * 2 + 14;
        new Button(new Coord(0, by), FayteSkin.s(120), this, "Keep") {
            @Override
            public void click() {
                FayteScaleConfirm.this.keep();
            }
        };
        new Button(new Coord(FayteSkin.s(130), by), FayteSkin.s(120), this, "Revert") {
            @Override
            public void click() {
                FayteScaleConfirm.this.revert();
            }
        };
    }

    private void keep() {
        GameUI gui = getparent(GameUI.class);
        if (gui != null) {
            for (Widget w = gui.child; w != null; w = w.next) {
                if (w instanceof Window && w != this && !FayteHud.is(w)) {
                    ((Window) w).storeOpt("_pos", w.c);
                }
            }
        }
        snap = null;
        FayteChatWindow.save();
        FayteMsg.say(String.format("Interface scale kept at %.2f\u00d7.", FayteConfig.uiScale.get()));
        ui.destroy(this);
    }

    private void revert() {
        restoring = true;
        spread = false;
        FayteConfig.uiScale.set(old);
        FayteMsg.say(String.format("Interface scale back to %.2f\u00d7.", old));
        ui.destroy(this);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if (parent != null) {
            Coord want = parent.sz.sub(sz).div(2);
            if (!placed || !c.equals(want)) {
                c = want;
                placed = true;
            }
        }
        long left = WAIT - (System.currentTimeMillis() - start);
        if (left <= 0) {
            revert();
            return;
        }
        count.settext("Going back in " + ((left + 999) / 1000) + " seconds unless you press Keep.");
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (sender == cbtn) {
            revert();
            return;
        }
        super.wdgmsg(sender, msg, args);
    }

    @Override
    public void destroy() {
        if (current == this) {
            current = null;
        }
        super.destroy();
    }

    @Override
    protected boolean compactstyle() {
        return true;
    }
}
