package haven;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteTimersWnd extends Window {
    private static final int W = Math.max(440, FayteSkin.s(380));
    private static final int ROWH = FayteSkin.s(24);
    private static final int ROWS = 10;
    private static final int BW = 34;
    private static final Color ALARM = new Color(0xD0, 0x50, 0x40);
    private static FayteTimersWnd instance = null;
    private final TextEntry name;
    private final TextEntry dur;
    private final Map<String, Text> texts = new HashMap<>();
    private int scroll = 0;
    private final Button addb;
    private final Label help;
    private static final int BOTTOM = FayteSkin.s(60);
    private int edge = 0;
    private Coord estart = null;
    private Coord ec = null;
    private Coord esz = null;

    private static Coord savedsz() {
        String[] p = Utils.getpref("fayte_timers_size", "").split(",");
        try {
            if (p.length == 2) {
                return new Coord(W, Math.max(ROWH * 3 + BOTTOM, Integer.parseInt(p[1])));
            }
        } catch (NumberFormatException e) {
        }
        return new Coord(W, ROWS * ROWH + BOTTOM);
    }

    private int rows() {
        return Math.max(3, (asz.y - BOTTOM) / ROWH);
    }

    private FayteTimersWnd(Widget parent) {
        super(new Coord(FayteSkin.s(240), FayteSkin.s(140)), savedsz(), parent, "Timers");
        justclose = true;
        name = new TextEntry(Coord.z, FayteSkin.s(180), this, "");
        name.clicktotype = true;
        dur = new TextEntry(Coord.z, FayteSkin.s(90), this, "") {
            public void activate(String text) {
                FayteTimersWnd.this.add();
            }
        };
        dur.clicktotype = true;
        addb = new Button(Coord.z, FayteSkin.s(70), this, "Add") {
            public void click() {
                FayteTimersWnd.this.add();
            }
        };
        help = new Label(Coord.z, this, "Name, then a time like 1h30m, 45m, 90s or 1:30:00");
        place();
    }

    private void place() {
        int y = rows() * ROWH + 8;
        name.c = new Coord(0, y);
        dur.c = new Coord(FayteSkin.s(186), y);
        addb.c = new Coord(FayteSkin.s(282), y - FayteSkin.s(2));
        help.c = new Coord(0, y + FayteSkin.s(26));
    }

    public static void open(GameUI gui) {
        if (gui == null) {
            return;
        }
        if (instance == null || !instance.attached()) {
            instance = new FayteTimersWnd(gui);
        }
        instance.raise();
    }

    public static void prefill(GameUI gui, String name) {
        open(gui);
        if (instance != null && instance.attached()) {
            instance.name.settext(name);
            instance.dur.arm();
            FayteMsg.say("Type how long " + name + " takes (for example 1h30m), then press Enter.");
        }
    }

    public static void toggle(GameUI gui) {
        if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
        } else {
            open(gui);
        }
    }

    private void add() {
        String n = name.text.trim();
        long d = FayteTimers.parse(dur.text);
        if (n.isEmpty() || d <= 0) {
            FayteMsg.say("Give the timer a name and a time like 1h30m, 45m or 1:30:00", GameUI.MsgType.BAD);
            return;
        }
        FayteTimers.Timer t = FayteTimers.add(n, d);
        FayteTimers.start(t);
        name.settext("");
        dur.settext("");
    }

    private Text text(String s, Color c) {
        String k = c.getRGB() + "|" + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 300) {
                texts.clear();
            }
            t = FayteSkin.labelf.render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private void button(GOut g, int x, int y, String label, boolean on) {
        FayteSkin.box(
                g,
                new Coord(x, y + 3),
                new Coord(BW - 4, ROWH - 6),
                on ? FayteSkin.BORDER : FayteSkin.PANEL,
                FayteSkin.BORDER);
        g.aimage(text(label, FayteSkin.TEXT).tex(), new Coord(x + (BW - 4) / 2, y + ROWH / 2), 0.5, 0.5);
    }

    @Override
    public void cdraw(GOut g) {
        List<FayteTimers.Timer> ts = FayteTimers.all();
        scroll = Math.max(0, Math.min(scroll, ts.size() - rows()));
        int bx = asz.x - (BW * 3);
        if (ts.isEmpty()) {
            g.image(
                    text("No timers yet. Add one below.", FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.6))
                            .tex(),
                    new Coord(4, 4));
        }
        for (int i = 0; i < rows() && i + scroll < ts.size(); i++) {
            FayteTimers.Timer t = ts.get(i + scroll);
            int y = i * ROWH;
            boolean flash = t.done && (System.currentTimeMillis() / 500) % 2 == 0;
            if (flash) {
                FayteSkin.box(
                        g, new Coord(0, y), new Coord(asz.x, ROWH), FayteSkin.mix(FayteSkin.PANEL, ALARM, 0.4), null);
            }
            g.aimage(text(t.name, FayteSkin.TEXT).tex(), new Coord(4, y + ROWH / 2), 0.0, 0.5);
            long r = FayteTimers.remaining(t);
            String tl = t.done
                    ? "Done"
                    : t.end < 0
                            ? FayteTimers.fmt(t.duration)
                            : r == FayteTimers.OFFLINE ? "Offline" : FayteTimers.fmt(Math.max(0, r));
            Text tt = text(tl, t.done ? ALARM : FayteSkin.TEXT);
            int lx0 = 150;
            int lx1 = bx - tt.sz().x - 12;
            g.chcolor(FayteSkin.BORDER);
            g.frect(new Coord(lx0, y + ROWH / 2), new Coord(Math.max(0, lx1 - lx0), 1));
            if (t.end >= 0 && !t.done && r >= 0 && t.duration > 0) {
                double f = 1.0 - Math.min(1.0, (double) r / t.duration);
                g.chcolor(FayteSkin.TEXT);
                g.frect(new Coord(lx0, y + ROWH / 2 - 1), new Coord((int) (Math.max(0, lx1 - lx0) * f), 3));
            }
            g.chcolor();
            g.aimage(tt.tex(), new Coord(bx - 8, y + ROWH / 2), 1.0, 0.5);
            button(g, bx, y, t.end >= 0 ? (t.done ? "Off" : "Stop") : "Go", t.end >= 0);
            button(g, bx + BW, y, "Pop", t.popped);
            button(g, bx + BW * 2, y, "X", false);
        }
    }

    @Override
    public void mousemove(Coord c) {
        if (edge != 0) {
            Coord frame = sz.sub(asz);
            Coord d = rootpos().add(c).sub(estart);
            Coord[] r = FayteEdges.apply(edge, ec, esz, d, new Coord(W, ROWH * 3 + BOTTOM).add(frame));
            this.c = r[0];
            Coord nasz = r[1].sub(frame);
            if (!nasz.equals(asz)) {
                resize(nasz);
                place();
            }
            return;
        }
        super.mousemove(c);
    }

    @Override
    public boolean mouseup(Coord c, int button) {
        if (edge != 0 && button == 1) {
            edge = 0;
            ui.grabmouse(null);
            Utils.setpref("fayte_timers_size", asz.x + "," + asz.y);
            storeOpt("_pos", this.c);
            return true;
        }
        return super.mouseup(c, button);
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        int e = FayteEdges.at(c, sz, FayteSkin.s(5)) & (FayteEdges.TOP | FayteEdges.BOTTOM);
        if (e != 0 && button == 1) {
            edge = e;
            estart = rootpos().add(c);
            ec = this.c;
            esz = sz;
            ui.grabmouse(this);
            return true;
        }
        Coord p = c.sub(atl);
        List<FayteTimers.Timer> ts = FayteTimers.all();
        int bx = asz.x - (BW * 3);
        if (button == 1 && p.x >= 0 && p.x < 150 && p.y >= 0 && p.y < rows() * ROWH) {
            int i = p.y / ROWH + scroll;
            if (i < ts.size()) {
                final FayteTimers.Timer t = ts.get(i);
                new FayteAsk(
                        getparent(GameUI.class),
                        "Rename timer",
                        "New name for this timer. Enter to save.",
                        t.name,
                        (n) -> FayteTimers.rename(t, n));
                return true;
            }
        }
        if (button == 1 && p.x >= bx && p.y >= 0 && p.y < rows() * ROWH) {
            int i = p.y / ROWH + scroll;
            if (i < ts.size()) {
                FayteTimers.Timer t = ts.get(i);
                int b = (p.x - bx) / BW;
                if (b == 0) {
                    if (t.end >= 0) {
                        FayteTimers.stop(t);
                    } else {
                        FayteTimers.start(t);
                    }
                } else if (b == 1) {
                    FayteTimers.setpopped(t, !t.popped);
                } else if (b == 2) {
                    FayteTimers.remove(t);
                }
                return true;
            }
        }
        return super.mousedown(c, button);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        Coord p = c.sub(atl);
        if (p.x >= 0
                && p.x < 150
                && p.y >= 0
                && p.y < rows() * ROWH
                && p.y / ROWH + scroll < FayteTimers.all().size()) {
            return "Click the name to rename this timer";
        }
        return super.tooltip(c, prev);
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        scroll = Math.max(0, scroll + (amount > 0 ? 1 : -1));
        return true;
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
