package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FaytePilgrimsPanel extends Widget {
    private static final int TOPH = FayteSkin.s(110);
    private static final int TABY = TOPH + 2;
    private static final int GRIDY = TOPH + 28;
    private static final int GAP = 6;
    private static final int SIDEW = FayteSkin.s(170);
    private static final int GX = SIDEW + 10;
    private static final int TABH = FayteSkin.s(28);
    private static final String ALL = "Everyone";
    private static final String UNSORTED = "Unsorted";
    private static final String ONLINE = "Online";
    private static final String NEWTAB = "+ New tab";
    private BuddyWnd.Buddy hovcard = null;
    private long hovsince = 0L;
    private static final Color READY = new Color(0x58, 0xC8, 0x60);
    private final GameUI gui;
    private final TextEntry search;
    private final TextEntry secret;
    private final Button enterbtn;
    private final FayteTitleButton classic;
    private final FayteTitleButton compactbtn;
    private final Label secretlbl;
    private final Map<String, Text> texts = new HashMap<>();
    private List<BuddyWnd.Buddy> shown = new ArrayList<>();
    private String tab = Utils.getpref("fayte_pilgrims_tab", UNSORTED);
    private int scroll = 0;
    private int lastserial = -1;
    private String lastq = null;
    private boolean compact;

    public FaytePilgrimsPanel(Coord c, Coord sz, Widget parent, GameUI gui) {
        super(c, sz, parent);
        this.gui = gui;
        compact = Utils.getprefb("fayte_pilgrims_compact", false);
        int x = FayteSkin.s(96);
        new Button(new Coord(x, FayteSkin.s(26)), FayteSkin.s(70), this, "Rename") {
            @Override
            public void click() {
                FaytePilgrimsPanel.this.askpname();
            }
        };
        new Button(new Coord(x, FayteSkin.s(70)), FayteSkin.s(60), this, "Set") {
            @Override
            public void click() {
                FaytePilgrimsPanel.this.asksecret();
            }
        };
        new Button(new Coord(x + FayteSkin.s(64), FayteSkin.s(70)), FayteSkin.s(70), this, "Random") {
            @Override
            public void click() {
                FaytePilgrimsPanel.this.setsecret(randpwd());
            }
        };
        new Button(new Coord(x + FayteSkin.s(138), FayteSkin.s(70)), FayteSkin.s(60), this, "Clear") {
            @Override
            public void click() {
                FaytePilgrimsPanel.this.setsecret("");
            }
        };
        new Button(new Coord(x + FayteSkin.s(202), FayteSkin.s(70)), FayteSkin.s(90), this, "New photo") {
            @Override
            public void click() {
                FaytePilgrims.refresh(gui, FaytePilgrims.SELF);
                FayteMsg.say("Taking a new portrait in a few seconds.");
            }
        };
        secretlbl = new Label(Coord.z, this, "Enter a secret to become kin:");
        secret = new TextEntry(Coord.z, FayteSkin.s(200), this, "") {
            @Override
            public void activate(String text) {
                FaytePilgrimsPanel.this.enter();
            }
        };
        secret.clicktotype = true;
        enterbtn = new Button(Coord.z, FayteSkin.s(60), this, "Enter") {
            @Override
            public void click() {
                FaytePilgrimsPanel.this.enter();
            }
        };
        classic = new FayteTitleButton(this, "Game window", "Open the game's own kin window", () -> {
            if (gui.buddies != null && gui.buddies.show(true)) {
                gui.buddies.raise();
            }
        });
        compactbtn = new FayteTitleButton(this, "Compact", "Smaller cards", () -> {
            compact = !compact;
            Utils.setprefb("fayte_pilgrims_compact", compact);
            FaytePilgrimsPanel.this.markcompact();
        });
        compactbtn.sel = compact;
        search = new TextEntry(Coord.z, FayteSkin.s(160), this, "") {
            @Override
            protected void changed() {
                FaytePilgrimsPanel.this.lastserial = -1;
            }
        };
        search.clicktotype = true;
        kinnotice = new FayteKinReq.Notice(this);
        layout();
    }

    private FayteKinReq.Notice kinnotice;

    private void markcompact() {
        compactbtn.sel = compact;
    }

    public void layout() {
        int rx = sz.x - FayteSkin.s(272);
        secretlbl.c = new Coord(rx, 4);
        secret.c = new Coord(rx, FayteSkin.s(22));
        enterbtn.c = new Coord(rx + FayteSkin.s(206), FayteSkin.s(20));
        classic.c = new Coord(sz.x - classic.sz.x, FayteSkin.s(52));
        search.c = new Coord(GX + FayteSkin.s(60), TABY + 1);
        compactbtn.c = new Coord(GX + FayteSkin.s(230), TABY + 3);
        if (kinnotice != null) {
            kinnotice.c = new Coord(0, sz.y - FayteKinReq.Notice.H);
            kinnotice.sz = new Coord(sz.x, FayteKinReq.Notice.H);
            kinnotice.raise();
        }
    }

    @Override
    public void resize(Coord sz) {
        super.resize(sz);
        layout();
    }

    private static String randpwd() {
        String cs = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            b.append(cs.charAt((int) (Math.random() * cs.length())));
        }
        return b.toString();
    }

    private void askpname() {
        if (gui.buddies != null) {
            new FayteAsk(
                    gui,
                    "Presentation name",
                    "The name your kin see for you. Enter to save.",
                    gui.buddies.pname(),
                    (t) -> gui.buddies.wdgmsg("pname", t));
        }
    }

    private void asksecret() {
        if (gui.buddies != null) {
            new FayteAsk(
                    gui,
                    "Homestead secret",
                    "Others who enter this secret become your kin. Enter to save.",
                    gui.buddies.secret(),
                    this::setsecret);
        }
    }

    private void setsecret(String s) {
        if (gui.buddies != null) {
            gui.buddies.wdgmsg("pwd", s);
        }
    }

    private void enter() {
        String t = secret.text.trim();
        if (!t.isEmpty() && gui.buddies != null) {
            gui.buddies.wdgmsg("bypwd", t);
            secret.settext("");
            TextEntry.armed = null;
            gui.setfocus(gui.map);
        }
    }

    private Text text(String s, Color c, boolean big) {
        String k = (big ? "b" : "") + c.getRGB() + "|" + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 400) {
                texts.clear();
            }
            t = (big ? Window.bigtf : FayteSkin.labelf).render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private int count(String tab) {
        int n = 0;
        for (BuddyWnd.Buddy b : FaytePilgrims.buddies(gui)) {
            if (intab(b, tab)) {
                n++;
            }
        }
        return n;
    }

    private boolean intab(BuddyWnd.Buddy b, String tab) {
        if (ALL.equals(tab)) {
            return true;
        } else if (ONLINE.equals(tab)) {
            return b.online > 0;
        } else if (UNSORTED.equals(tab)) {
            FaytePilgrims.Kin k = FaytePilgrims.get(gui).kin.get(FaytePilgrims.key(b));
            return k == null || k.tabs == null || k.tabs.isEmpty();
        }
        return FaytePilgrims.intab(gui, b, tab);
    }

    private List<String> sidetabs() {
        List<String> all = new ArrayList<>();
        all.add(UNSORTED);
        all.addAll(FaytePilgrims.get(gui).tabs);
        all.add(ONLINE);
        all.add(ALL);
        all.add(NEWTAB);
        return all;
    }

    private void settab(String t) {
        tab = t;
        scroll = 0;
        lastserial = -1;
        Utils.setpref("fayte_pilgrims_tab", t);
    }

    private int tabat(Coord c) {
        if (c.x < 0 || c.x >= SIDEW || c.y < TABY) {
            return -1;
        }
        List<String> t = sidetabs();
        int y = TABY;
        for (int i = 0; i < t.size(); i++) {
            int h = TABH + ((t.get(i).equals(ONLINE) || t.get(i).equals(NEWTAB)) ? FayteSkin.s(8) : 0);
            if (c.y >= y && c.y < y + h) {
                return i;
            }
            y += h;
        }
        return -1;
    }

    private void drawside(GOut g) {
        List<String> t = sidetabs();
        List<String> custom = FaytePilgrims.get(gui).tabs;
        FayteSkin.box(g, new Coord(0, TABY), new Coord(SIDEW, sz.y - TABY), FayteSkin.PANEL, FayteSkin.BORDER);
        int y = TABY;
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.6);
        for (String n : t) {
            if (n.equals(ONLINE) || n.equals(NEWTAB)) {
                g.chcolor(FayteSkin.BORDER);
                g.frect(new Coord(8, y + 3), new Coord(SIDEW - 16, 1));
                g.chcolor();
                y += FayteSkin.s(8);
            }
            boolean sel = n.equals(tab);
            if (sel) {
                FayteSkin.box(g, new Coord(3, y + 1), new Coord(SIDEW - 6, TABH - 2), FayteSkin.BORDER, null);
            }
            Text lt = text(n, n.equals(NEWTAB) ? dim : FayteSkin.TEXT, true);
            g.aimage(lt.tex(), new Coord(12, y + TABH / 2), 0.0, 0.5);
            if (!n.equals(NEWTAB)) {
                g.aimage(
                        text(Integer.toString(count(n)), dim, false).tex(),
                        new Coord(SIDEW - 10, y + TABH / 2),
                        1.0,
                        0.5);
            }
            if (custom.contains(n) && sel) {
                g.chcolor(READY);
                g.frect(new Coord(3, y + 4), new Coord(3, TABH - 8));
                g.chcolor();
            }
            y += TABH;
        }
    }

    private void newtab() {
        new FayteAsk(gui, "New kin tab", "Name the tab. Add kin to it with right-click \u2192 Tabs.", "", (t) -> {
            if (!t.isEmpty()) {
                FaytePilgrims.addtab(gui, t);
                settab(t);
            }
        });
    }

    private void tabmenu(String t, Coord at) {
        String[] opts = {"Rename tab", "Delete tab"};
        new FaytePopup(at, this, opts, -1, (i) -> {
            if (i == 0) {
                new FayteAsk(gui, "Rename tab", "New name for " + t + ".", t, (n) -> {
                    if (!n.isEmpty()) {
                        FaytePilgrims.renametab(gui, t, n);
                        if (tab.equals(t)) {
                            settab(n);
                        }
                    }
                });
            } else {
                FaytePilgrims.removetab(gui, t);
                if (tab.equals(t)) {
                    settab(UNSORTED);
                }
                lastserial = -1;
            }
        });
    }

    private void refresh() {
        String q = search.text == null ? "" : search.text.trim().toLowerCase();
        int serial = gui.buddies == null ? -1 : gui.buddies.serial;
        String key = q + "|" + tab;
        if (serial == lastserial && key.equals(lastq)) {
            return;
        }
        lastserial = serial;
        lastq = key;
        List<BuddyWnd.Buddy> l = new ArrayList<>();
        for (BuddyWnd.Buddy b : FaytePilgrims.buddies(gui)) {
            if (!intab(b, tab)) {
                continue;
            }
            FaytePilgrims.Kin k = FaytePilgrims.get(gui).kin.get(FaytePilgrims.key(b));
            String d = k == null || k.desc == null ? "" : k.desc.toLowerCase();
            if (q.isEmpty() || b.name.toLowerCase().contains(q) || d.contains(q)) {
                l.add(b);
            }
        }
        l.sort((a, b) -> {
            int on = Integer.compare(b.online, a.online);
            return on != 0 ? on : a.name.compareToIgnoreCase(b.name);
        });
        shown = l;
    }

    private void portrait(GOut g, String key, Coord c, Coord sz, boolean grey, String initial) {
        FayteSkin.box(g, c, sz, FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.3), FayteSkin.BORDER);
        Tex t = FaytePilgrims.portrait(gui, key);
        if (t != null) {
            Coord asz = t.sz();
            double f = Math.min((double) (sz.x - 2) / asz.x, (double) (sz.y - 2) / asz.y);
            Coord isz = new Coord((int) (asz.x * f), (int) (asz.y * f));
            if (grey) {
                g.chcolor(110, 110, 110, 200);
            }
            g.image(t, c.add(sz.sub(isz).div(2)), isz);
            g.chcolor();
        } else if (initial != null && !initial.isEmpty()) {
            g.aimage(
                    text(initial.substring(0, 1).toUpperCase(), FayteSkin.TEXT, sz.y > 40)
                            .tex(),
                    c.add(sz.div(2)),
                    0.5,
                    0.5);
        }
    }

    private Coord card() {
        return compact ? new Coord(FayteSkin.s(180), FayteSkin.s(40)) : new Coord(FayteSkin.s(200), FayteSkin.s(86));
    }

    private Coord port() {
        return compact ? new Coord(FayteSkin.s(30), FayteSkin.s(34)) : new Coord(FayteSkin.s(62), FayteSkin.s(73));
    }

    private int cols() {
        return Math.max(1, (sz.x - GX + GAP) / (card().x + GAP));
    }

    private int rows() {
        return Math.max(1, (sz.y - GRIDY + GAP) / (card().y + GAP));
    }

    private BuddyWnd.Buddy cardat(Coord c) {
        if (c.y < GRIDY) {
            return null;
        }
        Coord cd = card();
        if (c.x < GX) {
            return null;
        }
        int col = (c.x - GX) / (cd.x + GAP);
        int row = (c.y - GRIDY) / (cd.y + GAP);
        if (col >= cols() || row >= rows()) {
            return null;
        }
        int i = (scroll + row) * cols() + col;
        return i >= 0 && i < shown.size() ? shown.get(i) : null;
    }

    @Override
    public void draw(GOut g) {
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.7);
        FayteSkin.box(
                g,
                Coord.z,
                new Coord(Math.max(FayteSkin.s(300), sz.x - FayteSkin.s(280)), TOPH - 12),
                FayteSkin.PANEL,
                FayteSkin.BORDER);
        portrait(g, FaytePilgrims.SELF, new Coord(6, 4), new Coord(FayteSkin.s(80), FayteSkin.s(90)), false, gui.chrid);
        BuddyWnd bw = gui.buddies;
        String pn = bw == null ? "" : bw.pname();
        g.image(
                text(pn == null || pn.isEmpty() ? (gui.chrid == null ? "You" : gui.chrid) : pn, FayteSkin.TEXT, true)
                        .tex(),
                new Coord(FayteSkin.s(96), 4));
        String sec = bw == null ? "" : bw.secret();
        g.image(
                text("Homestead secret: " + (sec == null || sec.isEmpty() ? "(none)" : sec), dim, false)
                        .tex(),
                new Coord(FayteSkin.s(96), FayteSkin.s(52)));
        refresh();
        drawside(g);
        g.aimage(text("Search", dim, false).tex(), new Coord(GX, TABY + 11), 0.0, 0.5);
        int cols = this.cols();
        int rows = this.rows();
        int total = (shown.size() + cols - 1) / cols;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - rows)));
        if (shown.isEmpty()) {
            String msg = FaytePilgrims.buddies(gui).isEmpty()
                    ? "No kin yet. Share your secret, or enter someone else's above."
                    : (UNSORTED.equals(tab)
                            ? "Everyone is sorted into tabs."
                            : (ALL.equals(tab) || ONLINE.equals(tab)
                                    ? "Nobody here."
                                    : "This tab is empty. Right-click a kin \u2192 Tabs to add them."));
            g.image(text(msg, dim, false).tex(), new Coord(GX + 4, GRIDY + 6));
        }
        Coord cd = card();
        for (int r = 0; r < rows; r++) {
            for (int col = 0; col < cols; col++) {
                int i = (scroll + r) * cols + col;
                if (i >= shown.size()) {
                    break;
                }
                card(g, shown.get(i), new Coord(GX + col * (cd.x + GAP), GRIDY + r * (cd.y + GAP)));
            }
        }
        super.draw(g);
    }

    private void card(GOut g, BuddyWnd.Buddy b, Coord c) {
        boolean on = b.online > 0;
        Coord cd = card();
        Coord pt = port();
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.6);
        FayteSkin.box(g, c, cd, FayteSkin.PANEL, FayteSkin.BORDER);
        String key = FaytePilgrims.key(b);
        portrait(g, key, c.add(3, 3), pt, !on, b.name);
        Color nc = FaytePilgrims.color(gui, b);
        if (!on) {
            nc = FayteSkin.mix(FayteSkin.PANEL, nc, 0.6);
        }
        int tx = c.x + pt.x + 9;
        g.image(text(b.name, nc, false).tex(), new Coord(tx, c.y + 4));
        g.chcolor(on ? READY : FayteSkin.mix(FayteSkin.PANEL, FayteSkin.TEXT, 0.35));
        g.frect(new Coord(tx, c.y + FayteSkin.s(27)), new Coord(6, 6));
        g.chcolor();
        FaytePilgrims.Kin k = FaytePilgrims.get(gui).kin.get(key);
        String st = b.online > 0 ? "Online" : (b.online == 0 ? "Offline" : "Not kin");
        if (compact && k != null && k.desc != null && !k.desc.isEmpty()) {
            st = k.desc;
        }
        fit(g, st, dim, tx + 10, c.y + FayteSkin.s(22), c.x + cd.x - tx - 24);
        if (!compact && k != null && k.desc != null && !k.desc.isEmpty()) {
            fit(
                    g,
                    k.desc,
                    FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.85),
                    tx,
                    c.y + FayteSkin.s(42),
                    c.x + cd.x - tx - 8);
        }
        g.chcolor(BuddyWnd.gc[Math.max(0, Math.min(BuddyWnd.gc.length - 1, b.group))]);
        g.frect(new Coord(c.x + cd.x - 10, c.y + cd.y - 10), new Coord(5, 5));
        g.chcolor();
    }

    private void fit(GOut g, String s, Color col, int x, int y, int w) {
        Text t = text(s, col, false);
        String d = s;
        while (t.sz().x > w && d.length() > 4) {
            d = d.substring(0, d.length() - 2);
            t = text(d + "\u2026", col, false);
        }
        g.image(t.tex(), new Coord(x, y));
    }

    private void tabsmenu(BuddyWnd.Buddy b, Coord at) {
        List<String> tabs = FaytePilgrims.get(gui).tabs;
        List<String> opts = new ArrayList<>();
        for (String t : tabs) {
            opts.add((FaytePilgrims.intab(gui, b, t) ? "\u2713 " : "    ") + t);
        }
        opts.add("New tab\u2026");
        new FaytePopup(at, this, opts.toArray(new String[0]), -1, (i) -> {
            if (i < tabs.size()) {
                String t = tabs.get(i);
                FaytePilgrims.settab(gui, b, t, !FaytePilgrims.intab(gui, b, t));
            } else {
                new FayteAsk(gui, "New kin tab", "Name the tab; " + b.name + " is added to it.", "", (t) -> {
                    if (!t.isEmpty()) {
                        FaytePilgrims.addtab(gui, t);
                        FaytePilgrims.settab(gui, b, t, true);
                    }
                });
            }
            lastserial = -1;
        });
    }

    private void menu(BuddyWnd.Buddy b, Coord at) {
        List<String> opts = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        opts.add("Tabs\u2026");
        acts.add(() -> tabsmenu(b, at));
        opts.add("Rename");
        acts.add(() -> new FayteAsk(gui, "Rename kin", "Your name for this kin. Enter to save.", b.name, (t) -> {
            if (!t.isEmpty()) {
                FaytePilgrims.renamed(gui, b.name, t);
                b.chname(t);
                lastserial = -1;
            }
        }));
        opts.add("Description");
        acts.add(() -> {
            FaytePilgrims.Kin k = FaytePilgrims.kin(gui, FaytePilgrims.key(b));
            new FayteAsk(gui, "Describe " + b.name, "A short note shown on the card. Empty clears it.", k.desc, (t) -> {
                k.desc = t.isEmpty() ? null : t;
                FaytePilgrims.changed();
                lastserial = -1;
            });
        });
        opts.add("Name color");
        acts.add(() -> {
            FaytePilgrims.Kin k = FaytePilgrims.kin(gui, FaytePilgrims.key(b));
            new FaytePopup(at, this, FaytePilgrims.COLORNAMES, k.color + 1, (i) -> {
                k.color = i - 1;
                FaytePilgrims.changed();
            });
        });
        opts.add("New portrait");
        acts.add(() -> {
            FaytePilgrims.refresh(gui, FaytePilgrims.key(b));
            FayteMsg.say("A new portrait of " + b.name + " is taken next time they're near you.");
        });
        if (b.online >= 0) {
            opts.add("Chat");
            acts.add(b::chat);
            if (b.online == 1) {
                opts.add("Invite to party");
                acts.add(b::invite);
            }
        }
        if (b.seen) {
            opts.add("Describe (in game)");
            acts.add(b::describe);
        }
        opts.add(b.online >= 0 ? "End kinship" : "Forget");
        acts.add(() -> {
            boolean kin = b.online >= 0;
            FayteConfirm.ask(
                    gui,
                    "kinremove",
                    kin ? "End kinship?" : "Forget this pilgrim?",
                    kin
                            ? "End kinship with " + b.name + "? They will no longer be your kin."
                            : "Forget " + b.name + "? They will be removed from your list.",
                    () -> {
                        if (kin) {
                            b.endkin();
                        } else {
                            b.forget();
                        }
                        FayteLog.log("Pilgrims: asked the game to " + (kin ? "end kinship with " : "forget ") + b.name
                                + " (id " + b.id + ")");
                        pendrm = b;
                        pendat = System.currentTimeMillis();
                    });
        });
        new FaytePopup(
                at, this, opts.toArray(new String[0]), -1, (i) -> acts.get(i).run());
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        int ti = tabat(c);
        if (ti >= 0) {
            String t = sidetabs().get(ti);
            if (t.equals(NEWTAB)) {
                newtab();
            } else if (button == 3 && FaytePilgrims.get(gui).tabs.contains(t)) {
                tabmenu(t, c);
            } else if (button == 1) {
                settab(t);
            }
            return true;
        }
        BuddyWnd.Buddy b = cardat(c);
        if (b != null && button == 3) {
            menu(b, c);
            return true;
        } else if (b != null && button == 1) {
            if (b.online >= 0) {
                b.chat();
            }
            return true;
        }
        return super.mousedown(c, button);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        int ti = tabat(c);
        if (ti >= 0) {
            String t = sidetabs().get(ti);
            if (FaytePilgrims.get(gui).tabs.contains(t)) {
                return "Right-click to rename or delete this tab";
            } else if (UNSORTED.equals(t)) {
                return "Kin who aren't in any of your tabs";
            }
            return null;
        }
        BuddyWnd.Buddy b = cardat(c);
        long now = System.currentTimeMillis();
        if (b != hovcard) {
            hovcard = b;
            hovsince = now;
        }
        if (b != null && now - hovsince < 1500L) {
            return null;
        }
        if (b != null) {
            FaytePilgrims.Kin k = FaytePilgrims.get(gui).kin.get(FaytePilgrims.key(b));
            return b.name + (k != null && k.desc != null ? " \u2014 " + k.desc : "")
                    + "  (left-click: chat, right-click: tabs and more; small square: land group)";
        }
        return super.tooltip(c, prev);
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        if (c.y > GRIDY && c.x >= GX) {
            scroll += amount > 0 ? 1 : -1;
            return true;
        }
        return super.mousewheel(c, amount);
    }

    public boolean armsearch() {
        if (search == null || !search.visible) {
            return false;
        }
        search.arm();
        return true;
    }

    private BuddyWnd.Buddy pendrm = null;
    private long pendat = 0L;

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if (pendrm != null && System.currentTimeMillis() - pendat > 3000L) {
            BuddyWnd.Buddy b = pendrm;
            pendrm = null;
            if (gui.buddies != null && gui.buddies.find(b.id) == b) {
                FayteMsg.say(
                        "The game didn't remove " + b.name
                                + ". Try the game's own kin window (Game window button) and tell the developer.",
                        GameUI.MsgType.BAD);
                FayteLog.log("Pilgrims: " + b.name + " is still listed 3 s after asking to remove them");
            } else {
                FayteMsg.say(b.name + " was removed.");
                lastserial = -1;
            }
        }
    }
}
