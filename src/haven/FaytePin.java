package haven;

import java.awt.Color;

public class FaytePin extends Widget {
    private final Window win;
    private final String key;
    private boolean hov = false;

    public FaytePin(Window win) {
        super(Coord.z, new Coord(FayteSkin.s(16), FayteSkin.s(16)), win);
        this.win = win;
        key = "fayte_pin_" + (win.cap == null ? "" : win.cap.text.toLowerCase());
        win.pinned = Utils.getprefb(key, false);
    }

    @Override
    public void draw(GOut g) {
        boolean p = win.pinned;
        FayteSkin.box(
                g,
                Coord.z,
                sz,
                hov ? FayteSkin.HOVER : (p ? FayteSkin.mix(FayteSkin.PANEL, new Color(0xD0, 0x40, 0x40), 0.35) : null),
                p ? new Color(0xD0, 0x40, 0x40) : null);
        int w = sz.x, h = sz.y;
        Color c = p ? new Color(0xF0, 0x60, 0x60) : FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, hov ? 0.85 : 0.5);
        g.chcolor(c);
        g.fellipse(new Coord(w / 2, h * 5 / 16), new Coord(w / 4, h / 4));
        g.frect(new Coord(w / 2 - w / 4, h * 7 / 16), new Coord(w / 2 + 1, Math.max(1, h / 10)));
        g.chcolor(FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.8));
        g.frect(new Coord(w / 2, h / 2), new Coord(1, h * 3 / 8));
        g.chcolor();
    }

    @Override
    public void mousemove(Coord c) {
        hov = c.isect(Coord.z, sz);
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (button == 1) {
            win.pinned = !win.pinned;
            Utils.setprefb(key, win.pinned);
            return true;
        } else if (button == 3) {
            int n = 0;
            for (Widget w = win.parent.child; w != null; w = w.next) {
                if (w instanceof Window && ((Window) w).pinned) {
                    ((Window) w).pinned = false;
                    Utils.setprefb(
                            "fayte_pin_" + (((Window) w).cap == null ? "" : ((Window) w).cap.text.toLowerCase()),
                            false);
                    n++;
                }
            }
            FayteMsg.say(n == 0 ? "No windows were pinned." : "Unpinned " + n + (n == 1 ? " window." : " windows."));
            return true;
        }
        return false;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        return win.pinned
                ? "Pinned: this window can't be dragged and opens where you left it. Click to unpin. Right-click"
                        + " unpins every window."
                : "Pin: keep this window where it is. Right-click unpins every window.";
    }
}
