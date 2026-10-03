package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteBenchCard extends Window {
    private static final Color HEAD = new Color(0xE3, 0xA8, 0x4A);
    private static final Color READY = new Color(0x8C, 0xD0, 0x7C);
    private static final Color SHORT = new Color(0xE0, 0x70, 0x60);
    private static FayteBenchCard current = null;
    private final GameUI gui;
    private final long gobid;
    private final List<String> stations;
    private final List<FayteRecipes.Recipe> recs = new ArrayList<>();
    private final Map<String, Text> texts = new HashMap<>();
    private final Map<String, Tex> icons = new HashMap<>();
    private final Button stopb, minusb;
    private FayteRecipes.Recipe cur = null;
    private List<String[]> ingr = new ArrayList<>();
    private List<FayteRecipes.Have> haves = new ArrayList<>();
    private long lasthave = 0L;
    private int qty = 1;
    private int scroll = 0;
    private final int listw, rowh, icon;

    public static boolean isopen() {
        return current != null && current.attached() && current.visible;
    }

    public static void open(GameUI gui, long gobid, List<String> stations) {
        if (current != null && current.attached()) {
            if (current.gobid == gobid) {
                current.raise();
                return;
            }
            current.ui.destroy(current);
        }
        current = new FayteBenchCard(gui, gobid, stations);
    }

    private static Coord size() {
        return new Coord(FayteSkin.s(420), FayteSkin.s(260));
    }

    private FayteBenchCard(GameUI gui, long gobid, List<String> stations) {
        super(
                new Coord(Math.max(0, gui.sz.x - size().x - FayteSkin.s(40)), FayteSkin.s(140)),
                size(),
                gui,
                benchname(gobid, stations));
        this.gui = gui;
        this.gobid = gobid;
        this.stations = stations;
        justclose = true;
        addtwdg(new FayteTitleButton(
                this, "Game window", "Open the game's own crafting window for this recipe", this::gamewindow));
        addtwdg(new FayteTitleButton(this, "Almanac", "Open this station's recipes in the Almanac", () -> {
            FayteAlmanacWnd.stationrecipes(this.gui, this.stations);
            if (cur != null) {
                FayteAlmanacWnd.show(this.gui, FayteAlmanac.RECIPES, cur.name);
            }
        }));
        listw = FayteSkin.s(170);
        icon = FayteSkin.s(20);
        rowh = Math.max(icon, FayteSkin.labelf.height()) + 4;
        for (FayteRecipes.Recipe r : FayteRecipes.list(gui)) {
            if (FayteStations.uses(r.name, stations)) {
                recs.add(r);
            }
        }
        recs.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        int bx = listw + FayteSkin.s(10);
        int by = asz.y - Button.bh() - 2;
        minusb = new Button(new Coord(bx, by), FayteSkin.s(22), this, "\u2212") {
            public void click() {
                FayteBenchCard.this.qty = Math.max(1, FayteBenchCard.this.qty - 1);
            }
        };
        new Button(new Coord(bx + FayteSkin.s(62), by), FayteSkin.s(22), this, "+") {
            public void click() {
                FayteBenchCard.this.qty = Math.min(999, FayteBenchCard.this.qty + 1);
            }
        };
        int cx = bx + FayteSkin.s(90);
        new Button(new Coord(cx, by), FayteSkin.s(52), this, "Craft") {
            public void click() {
                FayteBenchCard.this.craft(1);
            }
        };
        new Button(new Coord(cx + FayteSkin.s(54), by), FayteSkin.s(30), this, "\u00d7N") {
            public void click() {
                FayteBenchCard.this.craft(FayteBenchCard.this.qty);
            }
        };
        new Button(new Coord(cx + FayteSkin.s(86), by), FayteSkin.s(36), this, "All") {
            public void click() {
                FayteBenchCard.this.craft(999);
            }
        };
        stopb = new Button(new Coord(bx, by - Button.bh() - 4), FayteSkin.s(50), this, "Stop") {
            public void click() {
                FayteRecipes.stop("Crafting stopped");
            }
        };
    }

    private static String benchname(long gobid, List<String> stations) {
        String n = gobid >= 0 ? FayteLabels.get("gob:" + gobid) : null;
        return n != null ? n : stations.get(0);
    }

    private void gamewindow() {
        if (cur == null || cur.pag == null) {
            FayteMsg.say("Pick something to make first.");
            return;
        }
        gui.menu.senduse(cur.pag);
    }

    private boolean isstation(String name) {
        for (String s : stations) {
            if (s.trim().equalsIgnoreCase(name.trim())) {
                return true;
            }
        }
        return false;
    }

    private void pick(FayteRecipes.Recipe r) {
        cur = r;
        ingr = new ArrayList<>();
        for (String[] in : FayteRecipes.ingredients(r.name)) {
            if (!isstation(in[0])) {
                ingr.add(in);
            }
        }
        lasthave = 0L;
        haves = new ArrayList<>();
        FayteRecipes.preview(gui, r);
    }

    private int ingtop() {
        return FayteSkin.labelf.height() + 8;
    }

    private void craft(int n) {
        if (cur == null) {
            FayteMsg.say("Pick something to make first.");
            return;
        }
        if (FayteRecipes.busy()) {
            FayteRecipes.stop(null);
        }
        FayteRecipes.craft(gui, cur, n);
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

    private Tex icon(String name) {
        if (icons.containsKey(name)) {
            return icons.get(name);
        }
        BufferedImage img = FayteEntries.iconbuf(name);
        Tex t = img == null ? null : new TexI(img);
        icons.put(name, t);
        return t;
    }

    private void updhave() {
        long now = System.currentTimeMillis();
        if (cur == null || now - lasthave < 500L) {
            return;
        }
        lasthave = now;
        List<FayteRecipes.Have> l = new ArrayList<>();
        for (String[] in : ingr) {
            l.add(FayteRecipes.have(gui, cur.name, in[0]));
        }
        haves = l;
    }

    private int visrows() {
        return Math.max(1, asz.y / rowh);
    }

    @Override
    public void cdraw(GOut g) {
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.7);
        FayteSkin.box(g, Coord.z, new Coord(listw, asz.y), FayteSkin.PANEL, FayteSkin.BORDER);
        scroll = Math.max(0, Math.min(scroll, recs.size() - visrows()));
        GOut lg = g.reclip(new Coord(2, 2), new Coord(listw - 4, asz.y - 4));
        int y = 0;
        for (int i = scroll; i < recs.size() && y < asz.y; i++) {
            FayteRecipes.Recipe r = recs.get(i);
            if (r == cur) {
                lg.chcolor(FayteSkin.mix(FayteSkin.PANEL, HEAD, 0.25));
                lg.frect(new Coord(0, y), new Coord(listw, rowh));
                lg.chcolor();
            }
            Tex ic = icon(r.name);
            if (ic != null) {
                lg.image(ic, new Coord(2, y + 2), new Coord(icon, icon));
            }
            lg.image(
                    text(r.name, FayteSkin.TEXT).tex(),
                    new Coord(icon + 6, y + (rowh - FayteSkin.labelf.height()) / 2));
            y += rowh;
        }
        int x = listw + FayteSkin.s(10);
        int w = asz.x - x;
        if (cur == null) {
            g.image(
                    text(recs.isEmpty() ? "Nothing you know is made here." : "Pick something to make.", dim)
                            .tex(),
                    new Coord(x, 4));
            g.image(
                    text(recs.size() + (recs.size() == 1 ? " recipe here" : " recipes here"), dim)
                            .tex(),
                    new Coord(x, 4 + FayteSkin.labelf.height() + 4));
        } else {
            g.image(text(cur.name, HEAD).tex(), new Coord(x, 2));
            int yy = ingtop();
            updhave();
            for (int i = 0; i < ingr.size(); i++) {
                String[] in = ingr.get(i);
                int need = 1;
                try {
                    need = Integer.parseInt(in[1].trim());
                } catch (NumberFormatException e) {
                }
                FayteRecipes.Have h = i < haves.size() ? haves.get(i) : null;
                int have = h == null ? 0 : h.count;
                Tex ic = icon(in[0]);
                if (ic != null) {
                    g.image(ic, new Coord(x, yy), new Coord(icon, icon));
                }
                g.image(text(in[0], FayteSkin.TEXT).tex(), new Coord(x + icon + 4, yy + 2));
                g.aimage(
                        text(have + " / " + need * qty, have >= need * qty ? READY : SHORT)
                                .tex(),
                        new Coord(x + w - 4, yy + 2),
                        1.0,
                        0.0);
                yy += icon + 4;
            }
            String st = FayteRecipes.status();
            if (st != null) {
                g.image(text(st, dim).tex(), new Coord(x + FayteSkin.s(56), stopb.c.y + 3));
            }
        }
        g.aimage(
                text(Integer.toString(qty), FayteSkin.TEXT).tex(),
                new Coord(minusb.c.x + FayteSkin.s(42), minusb.c.y + Button.bh() / 2),
                0.5,
                0.5);
        stopb.visible = FayteRecipes.busy();
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        Coord p = c.sub(atl);
        if (button == 1 && p.x >= 0 && p.x < listw && p.y >= 0 && p.y < asz.y) {
            int i = scroll + (p.y - 2) / rowh;
            if (i >= 0 && i < recs.size()) {
                pick(recs.get(i));
            }
            return true;
        }
        String link = button == 1 ? linkat(p) : null;
        if (link != null) {
            FayteAlmanacWnd.showlink(gui, link);
            return true;
        }
        return super.mousedown(c, button);
    }

    private String linkat(Coord p) {
        int x = listw + FayteSkin.s(10);
        if (cur == null || p.x < x || p.x >= asz.x) {
            return null;
        }
        if (p.y >= 0 && p.y < ingtop() - 4) {
            return cur.name;
        }
        int i = (p.y - ingtop()) / (icon + 4);
        if (p.y >= ingtop() && i >= 0 && i < ingr.size()) {
            return ingr.get(i)[0];
        }
        return null;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        String l = linkat(c.sub(atl));
        if (l != null) {
            return "Open " + l + " in the Almanac";
        }
        return super.tooltip(c, prev);
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        Coord p = c.sub(atl);
        if (p.x >= 0 && p.x < listw) {
            scroll += amount;
            return true;
        }
        return super.mousewheel(c, amount);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if (gobid < 0 || gui.map == null) {
            return;
        }
        Gob b = gui.ui.sess.glob.oc.getgob(gobid);
        Gob pl = gui.map.player();
        if (b == null || (pl != null && pl.rc != null && b.rc != null && pl.rc.dist(b.rc) > 3 * 11)) {
            ui.destroy(this);
        }
    }

    @Override
    public void destroy() {
        if (current == this) {
            current = null;
        }
        FayteRecipes.endpreview(gui);
        super.destroy();
    }

    @Override
    protected boolean compactstyle() {
        return true;
    }
}
