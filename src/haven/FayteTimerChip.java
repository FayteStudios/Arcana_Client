package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteTimerChip extends Widget {
    public static final Coord SZ = new Coord(FayteSkin.s(220), FayteSkin.labelf.height() + FayteSkin.s(10));
    private static final Color GOLD = new Color(0xF0, 0xDA, 0x9A);
    private static final Color ALARM = new Color(0xD0, 0x50, 0x40);
    private static final Color GREEN = new Color(0x58, 0xC8, 0x60);
    private static FayteTimerChip instance = null;
    private final Map<String, Text> texts = new HashMap<>();
    private List<FayteTimers.Timer> rows = new ArrayList<>();

    private FayteTimerChip(Coord c, Widget parent) {
        super(c, SZ, parent);
    }

    public static void sync(GameUI gui) {
        boolean want = false;
        for (FayteTimers.Timer t : FayteTimers.all()) {
            want |= t.popped;
        }
        if (want && (instance == null || !instance.linked() || instance.parent != gui)) {
            instance = FayteLanding.add(gui, FayteHud.reg(new FayteTimerChip(FayteLanding.anchor(gui), gui), "timers"));
        } else if (!want && instance != null) {
            if (instance.linked()) {
                instance.ui.destroy(instance);
            }
            instance = null;
        }
    }

    private Text text(String s, Color c) {
        String k = c.getRGB() + "|" + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 200) {
                texts.clear();
            }
            t = FayteSkin.labelf.render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private int rowh() {
        return SZ.y;
    }

    @Override
    public void draw(GOut g) {
        List<FayteTimers.Timer> l = new ArrayList<>();
        for (FayteTimers.Timer t : FayteTimers.all()) {
            if (t.popped) {
                l.add(t);
            }
        }
        rows = l;
        int rh = rowh();
        int h = Math.max(1, l.size()) * rh + 4;
        if (sz.y != h) {
            sz = new Coord(SZ.x, h);
        }
        FayteSkin.box(g, Coord.z, sz, FayteSkin.PANEL, FayteSkin.BORDER);
        for (int i = 0; i < l.size(); i++) {
            FayteTimers.Timer t = l.get(i);
            int y = 2 + i * rh;
            Coord rc = new Coord(2, y);
            Coord rs = new Coord(sz.x - 4, rh - 2);
            boolean trade = FayteTrade.istrade(t);
            boolean tready = trade && t.done && FayteTrade.ready();
            boolean flash = t.done && !trade && (System.currentTimeMillis() / 500) % 2 == 0;
            if (trade && t.done) {
                FayteSkin.box(
                        g,
                        rc,
                        rs,
                        tready ? FayteSkin.mix(FayteSkin.PANEL, GREEN, 0.35) : FayteSkin.PANEL,
                        tready ? GREEN : GOLD);
            } else if (t.done) {
                FayteSkin.box(g, rc, rs, flash ? FayteSkin.mix(FayteSkin.PANEL, ALARM, 0.5) : FayteSkin.PANEL, ALARM);
            }
            long r = FayteTimers.remaining(t);
            String ts = t.done
                    ? (trade ? (tready ? "Ready" : FayteTrade.waitlabel()) : "Done")
                    : t.end < 0 ? "Stopped" : r == FayteTimers.OFFLINE ? "Offline" : FayteTimers.fmt(Math.max(0, r));
            Text tt = text(ts, t.done ? GOLD : FayteSkin.TEXT);
            int room = sz.x - tt.sz().x - 24;
            String shown = t.name;
            Text nt = text(shown, FayteSkin.TEXT);
            while (nt.sz().x > room && shown.length() > 2) {
                shown = shown.substring(0, shown.length() - 1);
                nt = text(shown.trim() + "\u2026", FayteSkin.TEXT);
            }
            g.aimage(nt.tex(), new Coord(8, y + rh / 2 - 1), 0.0, 0.5);
            g.aimage(tt.tex(), new Coord(sz.x - 8, y + rh / 2 - 1), 1.0, 0.5);
            if (t.end >= 0 && !t.done && r >= 0 && t.duration > 0) {
                double f = 1.0 - Math.min(1.0, (double) r / t.duration);
                g.chcolor(FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.6));
                g.frect(new Coord(6, y + rh - 5), new Coord((int) ((sz.x - 12) * f), 2));
                g.chcolor();
            }
        }
    }

    private FayteTimers.Timer at(Coord c) {
        int i = (c.y - 2) / rowh();
        return i >= 0 && i < rows.size() ? rows.get(i) : null;
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (button == 1 && !FayteHud.editing()) {
            FayteTimers.Timer t = at(c);
            if (t != null && t.done) {
                FayteTimers.stop(t);
            } else {
                FayteTimersWnd.open(getparent(GameUI.class));
            }
            return true;
        }
        return false;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        FayteTimers.Timer t = at(c);
        return t != null && t.done
                ? "Click to turn this timer off"
                : "Your popped-out timers. Click to open the Timers window.";
    }
}
