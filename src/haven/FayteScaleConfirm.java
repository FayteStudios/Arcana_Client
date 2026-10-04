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
    private static Map<Widget, int[]> snap = null;
    private static Coord snapsz = null;
    private static Coord placedsz = null;
    private static boolean restoring = false;

    private static void snapshot(GameUI gui) {
        snap = new HashMap<>();
        snapsz = gui.sz;
        placedsz = gui.sz;
        for (Widget w = gui.child; w != null; w = w.next) {
            boolean moved = (w instanceof MenuGrid && FayteConfig.actionGridPos.get() != null)
                    || (w instanceof GameUI.MainMenu && FayteConfig.buttonPanelPos.get() != null);
            if (moved || ((w instanceof Window || w instanceof FayteChatWindow)
                    && !(w instanceof FayteScaleConfirm)
                    && !FayteHud.is(w))) {
                snap.put(w, FayteHud.anchor(w.c, w.sz, gui.sz));
            }
        }
    }

    public static void follow(GameUI gui) {
        if (snap == null) {
            return;
        }
        if (!gui.sz.equals(placedsz)) {
            for (Map.Entry<Widget, int[]> e : snap.entrySet()) {
                Widget w = e.getKey();
                if (w.attached()) {
                    w.c = FayteHud.at(e.getValue(), gui.sz, w.sz);
                    if (w instanceof MenuGrid) {
                        FayteConfig.actionGridPos.set(w.c);
                    } else if (w instanceof GameUI.MainMenu) {
                        FayteConfig.buttonPanelPos.set(w.c);
                    }
                }
            }
            gui.menumoved();
            placedsz = gui.sz;
        }
        if (restoring && gui.sz.equals(snapsz)) {
            snap = null;
            restoring = false;
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
