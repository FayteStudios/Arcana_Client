package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FayteStationPanel extends Widget {
    private static final Color HEAD = new Color(0xE3, 0xA8, 0x4A);
    public static WItem hl = null;
    private final List<Object[]> rows = new ArrayList<>();
    private Coord mouse = null;
    private static final String[] GUIDEONLY = {"compost"};
    private static final String[] OPENDEF = {"dframe", "rack", "ttub", "oiling", "oiltrough", "kiln", "smoker", "oven"};
    private final GameUI gui;
    private final Window win;
    private final String rn;
    private final long gobid;
    private final String key;
    private final FayteGuide.Guide guide;
    private final List<String> stations;
    private final boolean cookpot;
    private final boolean herbpot;
    private final boolean guideonly;
    private final boolean progress;
    private final FayteTitleButton toggle;
    private final Button donebtn;
    private final Button fuelbtn;
    private final Button recipebtn;
    private final Map<String, Text> texts = new HashMap<>();
    private List<Text> guidelines = null;
    private int guidew = -1;
    private int scroll = 0;
    private int tab = 0;
    private boolean open;
    private final int tabh;
    private final int pw;

    public static FayteStationPanel attach(GameUI gui, Window win, String rn, long gobid) {
        String cap = win.cap.text.toLowerCase();
        boolean herb = rn.contains("herbpot");
        boolean cook = !herb && (cap.contains("pot") || cap.contains("kettle"));
        List<String> st;
        if (herb) {
            st = Arrays.asList("Gardening Pot");
        } else if (cook) {
            st = Arrays.asList("Any Pot", win.cap.text);
        } else {
            st = FayteStations.stations(rn);
        }
        boolean rec = FayteConfig.stationRecipes.get() && !st.isEmpty() && FayteStations.count(gui, st) > 0;
        FayteGuide.Guide gd = FayteGuide.find(rn);
        boolean go = false;
        for (String k : GUIDEONLY) {
            if (rn.contains(k)) {
                go = true;
            }
        }
        if (go && gd == null) {
            return null;
        }
        return new FayteStationPanel(gui, win, rn, gobid, gd, st, !go && rec, cook, herb, go);
    }

    private FayteStationPanel(
            GameUI gui,
            Window win,
            String rn,
            long gobid,
            FayteGuide.Guide g,
            List<String> st,
            boolean rec,
            boolean cook,
            boolean herb,
            boolean go) {
        super(Coord.z, new Coord(FayteSkin.s(260), FayteSkin.s(120)), win);
        this.gui = gui;
        this.win = win;
        this.rn = rn;
        this.gobid = gobid;
        guide = g;
        stations = st;
        cookpot = cook;
        herbpot = herb;
        guideonly = go;
        pw = FayteSkin.s(260);
        key = g != null ? g.key : rn.substring(rn.lastIndexOf('/') + 1);
        tabh = FayteSkin.labelf.height() + 8;
        donebtn = new Button(Coord.z, FayteSkin.s(120), this, "Timer: all done") {
            @Override
            public void click() {
                FayteStationPanel.this.donetimer();
            }
        };
        fuelbtn = new Button(Coord.z, FayteSkin.s(120), this, "Timer: fuel out") {
            @Override
            public void click() {
                FayteStationPanel.this.fueltimer();
            }
        };
        recipebtn = new Button(Coord.z, FayteSkin.s(90), this, herb ? "Can grow" : "Recipes") {
            @Override
            public void click() {
                FayteBenchCard.open(gui, FayteStationPanel.this.gobid, FayteStationPanel.this.stations);
            }
        };
        recipebtn.visible = rec;
        boolean def = false;
        for (String k : OPENDEF) {
            if (rn.contains(k)) {
                def = true;
            }
        }
        progress = def;
        if (go) {
            tab = 1;
            def = false;
        }
        open = Utils.getprefb("fayte_station_open_" + key, def);
        toggle = new FayteTitleButton(
                win,
                label(),
                go ? "Show or hide the guide" : "Show or hide this station's status, timers and guide",
                this::flip);
        win.addtwdg(toggle);
        fit(FayteSkin.s(120));
    }

    private void flip() {
        open = !open;
        Utils.setprefb("fayte_station_open_" + key, open);
        toggle.setlabel(label());
        fit(sz.y);
    }

    private String label() {
        String n = guideonly ? "Guide" : "Info";
        return open ? "\u25c2 " + n : n + " \u25b8";
    }

    private void fit(int want) {
        visible = false;
        Coord base = win.contentsz();
        c = new Coord(base.x + FayteSkin.s(8), 0);
        sz = new Coord(pw, Math.max(base.y, want));
        visible = open;
        win.pack();
        win.placetwdgs();
    }

    private String place() {
        String n = gobid >= 0 ? FayteLabels.get("gob:" + gobid) : null;
        return n != null ? n : win.cap.text;
    }

    private void donetimer() {
        long e = FayteProgress.longest(win);
        if (e < 0) {
            FayteTimersWnd.prefill(getparent(GameUI.class), place() + ": all done");
            return;
        }
        FayteTimers.restart(place() + ": all done", e);
        FayteMsg.say("Timer set: " + place() + " done in " + FayteProgress.fmt(e) + ".");
    }

    private int fuelamount = 0;

    private void fueltimer() {
        long e = FayteProgress.fuelleft(gobid, System.currentTimeMillis());
        if (e < 0) {
            e = FayteProgress.fuelguess(rn, fuelamount);
        }
        if (e < 0) {
            FayteTimersWnd.prefill(getparent(GameUI.class), place() + ": fuel out");
            return;
        }
        FayteTimers.restart(place() + ": fuel out", e);
        FayteMsg.say("Timer set: " + place() + " fuel out in " + FayteProgress.fmt(e) + ".");
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

    private static void collect(Widget w, List<VMeter> meters, List<WItem> items) {
        for (Widget c = w.child; c != null; c = c.next) {
            if (c instanceof FayteStationPanel) {
                continue;
            }
            if (c instanceof VMeter) {
                meters.add((VMeter) c);
            } else if (c instanceof WItem) {
                items.add((WItem) c);
                continue;
            }
            collect(c, meters, items);
        }
    }

    private String growing() {
        Gob g = gobid >= 0 ? gui.ui.sess.glob.oc.getgob(gobid) : null;
        byte[] b = null;
        if (g != null) {
            synchronized (g.ols) {
                for (Gob.Overlay ol : g.ols) {
                    try {
                        Resource or = ol.res.get();
                        if (or != null && or.name.contains("eqres") && ol.sdt != null) {
                            b = ol.sdt.blob;
                        }
                    } catch (Loading e) {
                        return "...";
                    }
                }
            }
        }
        if (b == null || b.length < 2) {
            return null;
        }
        int id = (b[0] & 0xff) | ((b[1] & 0xff) << 8);
        try {
            Resource r = gui.ui.sess.getres(id).get();
            if (r == null || !(r.name.contains("plant") || r.name.contains("herb") || r.name.contains("invobjs"))) {
                return null;
            }
            String d = null;
            try {
                d = FayteWorldNames.display(r.name);
            } catch (RuntimeException e) {
                FayteLog.once("FayteStationPanel.growing", e);
            }
            String n = d != null ? d : r.name.substring(r.name.lastIndexOf('/') + 1);
            return n;
        } catch (Loading e) {
            return "...";
        } catch (RuntimeException e) {
            return null;
        }
    }

    private int tabx(int i) {
        return i * FayteSkin.s(70);
    }

    private int ntabs() {
        return guideonly ? 0 : (guide != null ? 2 : 1);
    }

    @Override
    public void draw(GOut g) {
        FayteSkin.box(g, Coord.z, sz, FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.15), FayteSkin.BORDER);
        String[] tabs = {"Status", "Guide"};
        for (int i = 0; i < ntabs(); i++) {
            boolean sel = i == tab;
            FayteSkin.box(
                    g,
                    new Coord(tabx(i), 0),
                    new Coord(FayteSkin.s(68), tabh),
                    sel ? FayteSkin.mix(FayteSkin.PANEL, HEAD, 0.25) : FayteSkin.PANEL,
                    FayteSkin.BORDER);
            g.aimage(
                    text(tabs[i], sel ? HEAD : FayteSkin.TEXT).tex(),
                    new Coord(tabx(i) + FayteSkin.s(34), tabh / 2),
                    0.5,
                    0.5);
        }
        recipebtn.c = new Coord(sz.x - recipebtn.sz.x - 3, 1);
        donebtn.visible = false;
        fuelbtn.visible = false;
        int need = tab == 0 ? drawstatus(g) : drawguide(g);
        visible = false;
        int basey = win.contentsz().y;
        visible = true;
        if (Math.max(basey, need) != sz.y) {
            fit(need);
        }
        super.draw(g);
    }

    private int drawguide(GOut g) {
        int w = sz.x - 16;
        if (guidelines == null || guidew != w) {
            guidew = w;
            guidelines = new ArrayList<>();
            List<String> src = new ArrayList<>(guide.lines);
            for (String l : src) {
                String t = l.replaceAll("\\[\\[([^\\]]+)\\]\\]", "$1");
                if (t.startsWith("##")) {
                    guidelines.add(FayteSkin.labelf.render(t.substring(2).trim(), HEAD));
                } else if (t.startsWith("-")) {
                    guidelines.add(FayteSkin.labelf.renderwrap(
                            "\u2022 " + t.substring(1).trim(), FayteSkin.TEXT, w));
                } else {
                    guidelines.add(
                            FayteSkin.labelf.renderwrap(t, FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.8), w));
                }
            }
        }
        int maxh = FayteSkin.s(320);
        int total = 0;
        for (Text t : guidelines) {
            total += t.sz().y + 4;
        }
        int view = Math.min(total, maxh);
        scroll = Math.max(0, Math.min(scroll, total - view));
        int top = guideonly ? 0 : tabh;
        GOut cg = g.reclip(new Coord(8, top + 6), new Coord(w, view));
        int y = -scroll;
        for (Text t : guidelines) {
            if (y + t.sz().y > 0 && y < view) {
                cg.image(t.tex(), new Coord(0, y));
            }
            y += t.sz().y + 4;
        }
        return top + 6 + view + 8;
    }

    private int drawstatus(GOut g) {
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.7);
        int x = 8, w = sz.x - 16, y = tabh + 8, lh = FayteSkin.labelf.height();
        boolean any = false;
        if (herbpot) {
            String gr = growing();
            g.image(text("Growing", HEAD).tex(), new Coord(x, y));
            g.aimage(
                    text(gr == null ? "can't tell" : gr, gr == null ? dim : FayteSkin.TEXT)
                            .tex(),
                    new Coord(x + w, y),
                    1.0,
                    0.0);
            y += lh + 6;
            any = true;
        }
        List<VMeter> meters = new ArrayList<>();
        List<WItem> items = new ArrayList<>();
        collect(win, meters, items);
        Map<String, int[]> work = new LinkedHashMap<>();
        long slow = -1;
        boolean measuring = false;
        for (WItem it : items) {
            if ((it.item.meter > 0 || (progress && FayteProgress.base(FayteAlmanac.itemname(it.item)) > 0))
                    && it.item.meter < 100) {
                String n = FayteAlmanac.itemname(it.item);
                n = n == null ? "?" : n;
                int[] a = work.get(n);
                if (a == null) {
                    work.put(n, new int[] {1, it.item.meter});
                } else {
                    a[0]++;
                    a[1] = Math.min(a[1], it.item.meter);
                }
                long e = FayteProgress.eta(it.item);
                if (e < 0) {
                    measuring = true;
                }
                slow = Math.max(slow, e);
            }
        }
        rows.clear();
        WItem hov = null;
        if (!work.isEmpty()) {
            any = true;
            g.image(text("Working", HEAD).tex(), new Coord(x, y));
            y += lh + 4;
            List<WItem> each = new ArrayList<>();
            for (WItem it : items) {
                if ((it.item.meter > 0 || (progress && FayteProgress.base(FayteAlmanac.itemname(it.item)) > 0))
                        && it.item.meter < 100) {
                    each.add(it);
                }
            }
            if (each.size() <= 8) {
                for (WItem it : each) {
                    long e = FayteProgress.eta(it.item);
                    String n = FayteAlmanac.itemname(it.item);
                    boolean over = mouse != null && mouse.y >= y - 1 && mouse.y < y + lh + 2;
                    if (over) {
                        hov = it;
                        g.chcolor(FayteSkin.mix(FayteSkin.PANEL, HEAD, 0.2));
                        g.frect(new Coord(2, y - 1), new Coord(sz.x - 4, lh + 3));
                        g.chcolor();
                    }
                    g.image(
                            text((n == null ? "?" : n) + "  " + it.item.meter + "%", FayteSkin.TEXT)
                                    .tex(),
                            new Coord(x + 6, y));
                    g.aimage(
                            text(e >= 0 ? FayteProgress.clock(e) : "...", e >= 0 ? FayteSkin.TEXT : dim)
                                    .tex(),
                            new Coord(x + w, y),
                            1.0,
                            0.0);
                    rows.add(new Object[] {y, it, e, n});
                    y += lh + 3;
                }
            } else {
                for (Map.Entry<String, int[]> e : work.entrySet()) {
                    g.image(
                            text(e.getValue()[0] + "\u00d7 " + e.getKey(), FayteSkin.TEXT)
                                    .tex(),
                            new Coord(x + 6, y));
                    g.aimage(text(e.getValue()[1] + "%", dim).tex(), new Coord(x + w, y), 1.0, 0.0);
                    y += lh + 3;
                }
            }
            String all = slow >= 0
                    ? "All done at " + FayteProgress.clock(slow) + " (" + FayteProgress.fmt(slow) + " left)"
                    : (measuring ? "Measuring speed..." : "");
            g.image(text(all, FayteSkin.TEXT).tex(), new Coord(x + 6, y));
            y += lh + 6;
            if (slow >= 0) {
                donebtn.c = new Coord(x, y);
                donebtn.visible = true;
                y += Button.bh() + 6;
            }
        }
        VMeter fuel = null;
        if (!cookpot && !herbpot) {
            for (VMeter m : meters) {
                if (m.tipt == null) {
                    fuel = m;
                    break;
                }
            }
        }
        if (fuel != null && fuel.amount > 0) {
            any = true;
            long fe = FayteProgress.fuelleft(gobid, System.currentTimeMillis());
            if (fe < 0) {
                fe = FayteProgress.fuelguess(rn, fuel.amount);
            }
            fuelamount = fuel.amount;
            g.image(text("Fuel", HEAD).tex(), new Coord(x, y));
            g.aimage(
                    text(
                                    fe >= 0
                                            ? "out at " + FayteProgress.clock(fe) + " (" + FayteProgress.fmt(fe) + ")"
                                            : "measuring burn rate...",
                                    fe >= 0 ? FayteSkin.TEXT : dim)
                            .tex(),
                    new Coord(x + w, y),
                    1.0,
                    0.0);
            y += lh + 6;
            if (fe >= 0) {
                fuelbtn.c = new Coord(x, y);
                fuelbtn.visible = true;
                y += Button.bh() + 6;
            }
        }
        String base = place() + ": ";
        List<FayteTimers.Timer> mine = new ArrayList<>();
        for (FayteTimers.Timer t : FayteTimers.all()) {
            if (t.name != null && t.name.startsWith(base) && t.end >= 0 && !t.done) {
                mine.add(t);
            }
        }
        if (!mine.isEmpty()) {
            any = true;
            g.image(text("Timers", HEAD).tex(), new Coord(x, y));
            y += lh + 4;
            for (FayteTimers.Timer t : mine) {
                long r = FayteTimers.remaining(t);
                g.image(text(t.name.substring(base.length()), FayteSkin.TEXT).tex(), new Coord(x + 6, y));
                g.aimage(
                        text(t.done ? "Done" : r >= 0 ? FayteTimers.fmt(r) : "?", dim)
                                .tex(),
                        new Coord(x + w, y),
                        1.0,
                        0.0);
                y += lh + 3;
            }
        }
        hl = hov;
        if (!any) {
            g.image(text("Nothing going on right now.", dim).tex(), new Coord(x, y));
            y += lh + 4;
        }
        return y + 6;
    }

    @Override
    public void mousemove(Coord c) {
        mouse = c.isect(Coord.z, sz) ? c : null;
        if (mouse == null && hl != null) {
            hl = null;
        }
        super.mousemove(c);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        for (Object[] r : rows) {
            int ry = (Integer) r[0];
            if (c.y >= ry - 1 && c.y < ry + FayteSkin.labelf.height() + 2) {
                long e = (Long) r[2];
                return e >= 0
                        ? r[3] + ": done at " + FayteProgress.clock(e) + " (" + FayteProgress.fmt(e)
                                + " left). Click to add a timer."
                        : r[3] + ": measuring...";
            }
        }
        return super.tooltip(c, prev);
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        if (tab == 1) {
            scroll += amount * 20;
            return true;
        }
        return super.mousewheel(c, amount);
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (button == 1 && tab == 0) {
            for (Object[] r : rows) {
                int ry = (Integer) r[0];
                if (c.y >= ry - 1 && c.y < ry + FayteSkin.labelf.height() + 2) {
                    long e = (Long) r[2];
                    if (e >= 0) {
                        FayteTimers.restart(place() + ": " + r[3] + " done", e);
                        FayteMsg.say("Timer set: " + r[3] + " done at " + FayteProgress.clock(e) + ".");
                    }
                    return true;
                }
            }
        }
        if (!guideonly && c.y < tabh && button == 1) {
            for (int i = 0; i < ntabs(); i++) {
                if (c.x >= tabx(i) && c.x < tabx(i) + FayteSkin.s(68)) {
                    tab = i;
                    scroll = 0;
                    return true;
                }
            }
        }
        return super.mousedown(c, button);
    }
}
