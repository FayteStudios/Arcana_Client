package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class FayteField extends Window {
    private static final Color[] BARC = {
        new Color(0xEE, 0x22, 0x22),
        new Color(0x32, 0xCD, 0x32),
        new Color(0x22, 0x44, 0xEE),
        new Color(0xFF, 0x69, 0xB4)
    };
    private static final String[] BARWORD = {"cabbage", "cereal", "cotton", "pumpkin"};
    private static final Object[][] CROPS = {
        {"Cereal", "Ear of Cereal", new int[] {4, 1}, 2, new String[] {"cereal", "wheat", "barley", "rye", "oat"}},
        {"Cabbage", "Seeds of Cabbage", new int[] {3, 4}, 1, new String[] {"cabbage", "colewort"}},
        {"Cotton", "Cotton", new int[] {1, 2}, 3, new String[] {"cotton"}},
        {"Pumpkin", "Pumpkin", new int[] {2, 3}, 4, new String[] {"pumpkin"}},
        {"Corn", "Corn", new int[] {1, 3}, 2, new String[] {"corn", "maize"}},
        {"Potato", "Any Potato", new int[] {3, 4}, 1, new String[] {"potato"}},
        {"Tobacco", "Tobacco", new int[] {1, 2}, 3, new String[] {"tobacco"}},
    };
    private static final String[][] FERT = {
        {"Bloom", "0", "0", "700", "0"},
        {"Bone Ash", "-30", "0", "0", "0"},
        {"Bonemeal", "0", "100", "0", "0"},
        {"Carver's Crappy Downtime Droppings", "0", "0", "1500", "0"},
        {"Clay", "0", "0", "-50", "60"},
        {"Charcoal", "0", "-50", "50", "0"},
        {"Cow Manure", "0", "150", "150", "180"},
        {"Crown Manure", "0", "250", "125", "150"},
        {"Dross", "0", "0", "125", "0"},
        {"Egg Shell", "0", "0", "90", "48"},
        {"Fertile Turkey Droppings", "0", "90", "180", "108"},
        {"Goat Manure", "0", "100", "100", "180"},
        {"Guano", "0", "475", "625", "270"},
        {"Hay", "0", "0", "0", "110"},
        {"Humus", "0", "25", "50", "29"},
        {"Lime", "0", "50", "-50", "0"},
        {"Little Limestone", "0", "500", "0", "0"},
        {"Mysterious Lillypad", "0", "0", "0", "1100"},
        {"Pig Manure", "0", "150", "100", "120"},
        {"Rotten Fruit", "0", "0", "180", "108"},
        {"Sheep Manure", "0", "100", "250", "120"},
        {"Turkey Droppings", "0", "40", "90", "48"},
        {"Whale Excrement", "-40", "0", "0", "0"},
        {"Wood Choppings", "-20", "0", "0", "0"},
    };
    private static final String[] FERTCOLS = {"Upkeep", "Plenty", "Speed", "Influence"};
    private static final Color GOLD = new Color(0xF0, 0xD0, 0x48);
    private static final int PAD = 8;
    private static final Map<Window, FayteField> cards = new WeakHashMap<>();
    private final Window src;
    private final GameUI gui;
    private final long gobid;
    private final Map<String, Text> texts = new HashMap<>();
    private int croptop = -1;
    private int bartop = -1;
    private int fertop = -1;
    private List<String> fertnames = new ArrayList<>();
    private long lastscan = 0L;
    private int planted = -1;
    private String plantedname = null;
    private int stage = -1;
    private int cropcount = 0;

    public static void meter(VMeter m, String tip) {
        if (!FayteModules.ALMANAC.on() || tip == null || bar(tip) < 0) {
            return;
        }
        Window w = m.getparent(Window.class);
        GameUI gui = m.getparent(GameUI.class);
        if (w == null || gui == null || w.cap == null || cards.containsKey(w)) {
            return;
        }
        int n = 0;
        for (VMeter v : meters(w)) {
            if (v.tipt != null && bar(v.tipt) >= 0) {
                n++;
            }
        }
        if (n >= 4) {
            for (FayteField old : new ArrayList<>(cards.values())) {
                if (old != null && old.attached()) {
                    old.ui.destroy(old);
                }
            }
            cards.put(w, new FayteField(gui, w));
        }
    }

    private static int bar(String tip) {
        String t = tip.toLowerCase();
        for (int i = 0; i < BARWORD.length; i++) {
            if (t.contains(BARWORD[i])) {
                return i;
            }
        }
        return -1;
    }

    private static List<VMeter> meters(Widget w) {
        List<VMeter> l = new ArrayList<>();
        for (Widget c = w.child; c != null; c = c.next) {
            if (c instanceof VMeter) {
                l.add((VMeter) c);
            }
            l.addAll(meters(c));
        }
        return l;
    }

    private static List<String> labels(Widget w) {
        List<String> l = new ArrayList<>();
        for (Widget c = w.child; c != null; c = c.next) {
            if (c instanceof Label
                    && ((Label) c).texts != null
                    && !((Label) c).texts.trim().isEmpty()) {
                l.add(((Label) c).texts);
            }
            l.addAll(labels(c));
        }
        return l;
    }

    private FayteField(GameUI gui, Window src) {
        super(src.c, new Coord(FayteSkin.s(360), FayteSkin.s(420)), gui, "Field");
        this.src = src;
        this.gui = gui;
        justclose = true;
        Gob g = FayteTools.lastgob(gui);
        gobid = g != null && FayteMsg.resname(g) != null && FayteMsg.resname(g).contains("field") ? g.id : -1L;
        addtwdg(new FayteTitleButton(this, "Name", "Give this field a name", () -> {
            if (gobid >= 0) {
                FayteLabels.prompt(this, "gob:" + gobid, "field");
            }
        }));
        addtwdg(new FayteTitleButton(
                this,
                "Fertilizers",
                "Every fertilizer and what it does (Almanac)",
                () -> FayteAlmanacWnd.showlink(gui, "Fertilizer")));
        addtwdg(new FayteTitleButton(
                this,
                "Tiers",
                "Crop tiers, influence and rotations (Almanac)",
                () -> FayteAlmanacWnd.showlink(gui, "Tilled Field")));
        src.hide();
    }

    private String starkey() {
        return "fayte_field_stars_" + gobid;
    }

    private boolean starred(int bar) {
        return Arrays.asList(Utils.getpref(starkey(), "").split(",")).contains(Integer.toString(bar));
    }

    private void togglestar(int bar) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            boolean on = starred(i);
            if (i == bar) {
                on = !on;
            }
            if (on) {
                l.add(Integer.toString(i));
            }
        }
        Utils.setpref(starkey(), String.join(",", l));
    }

    private int[] bars() {
        int[] v = {-1, -1, -1, -1};
        for (VMeter m : meters(src)) {
            if (m.tipt != null) {
                int b = bar(m.tipt);
                if (b >= 0) {
                    v[b] = m.amount;
                }
            }
        }
        return v;
    }

    private String raisedby(int b) {
        for (VMeter m : meters(src)) {
            if (m.tipt != null && bar(m.tipt) == b) {
                return m.tipt;
            }
        }
        return BARWORD[b];
    }

    private static int cropof(String rn) {
        String t = rn.toLowerCase();
        for (int i = 0; i < CROPS.length; i++) {
            for (String w : (String[]) CROPS[i][4]) {
                if (t.contains(w)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void scan() {
        long now = System.currentTimeMillis();
        if (now - lastscan < 1000L) {
            return;
        }
        lastscan = now;
        Gob f = gobid >= 0 ? gui.ui.sess.glob.oc.getgob(gobid) : null;
        planted = -2;
        plantedname = null;
        stage = -1;
        cropcount = 0;
        ResDrawable rd = f == null ? null : f.getattr(ResDrawable.class);
        byte[] b = rd == null || rd.sdt == null ? null : rd.sdt.blob;
        if (b != null && b.length >= 3) {
            int resid = (b[0] & 0xff) | ((b[1] & 0xff) << 8);
            stage = b[2] & 0xff;
            cropcount = 1;
            planted = -1;
            try {
                Resource r = gui.ui.sess.getres(resid).get();
                if (r != null) {
                    planted = cropof(r.name);
                    String d = null;
                    try {
                        d = FayteWorldNames.display(r.name);
                    } catch (RuntimeException e) {
                        FayteLog.once("FayteField.scan", e);
                    }
                    plantedname = d != null ? d : r.name.substring(r.name.lastIndexOf('/') + 1);
                    if (planted >= 0 && (d == null || d.equals(r.name.substring(r.name.lastIndexOf('/') + 1)))) {
                        plantedname = (String) CROPS[planted][0];
                    }
                }
            } catch (Loading e) {
                lastscan = 0L;
            } catch (RuntimeException e) {
                FayteLog.once("FayteField.scan", e);
            }
        }
        fertnames = carried();
    }

    private static String fertof(String item) {
        String n = item.toLowerCase();
        for (String[] f : FERT) {
            if (n.equals(f[0].toLowerCase())) {
                return f[0];
            }
        }
        if (n.startsWith("rotten") || n.startsWith("rotting")) {
            return "Rotten Fruit";
        }
        for (String[] f : FERT) {
            String fl = f[0].toLowerCase();
            if (fl.length() > 4 && (n.contains(fl) || n.endsWith(fl + "s"))) {
                return f[0];
            }
        }
        return null;
    }

    private List<String> carried() {
        Set<String> have = new LinkedHashSet<>();
        List<Inventory> invs = new ArrayList<>();
        if (gui.maininv != null) {
            invs.add(gui.maininv);
        }
        for (Widget w = gui.child; w != null; w = w.next) {
            if (w instanceof Window
                    && w.visible
                    && ((Window) w).cap != null
                    && ((Window) w).hasinv()
                    && w != gui.invwnd) {
                String t = ((Window) w).cap.text.toLowerCase();
                if (t.contains("pack")
                        || t.contains("belt")
                        || t.contains("sack")
                        || t.contains("bag")
                        || t.contains("pouch")) {
                    for (Widget c = w.child; c != null; c = c.next) {
                        if (c instanceof Inventory) {
                            invs.add((Inventory) c);
                        }
                    }
                }
            }
        }
        for (Inventory inv : invs) {
            for (Widget c = inv.child; c != null; c = c.next) {
                if (c instanceof WItem) {
                    String n = FayteAlmanac.itemname(((WItem) c).item);
                    String f = n == null ? null : fertof(n);
                    if (f != null) {
                        have.add(f);
                    }
                    for (String sub : FayteRecipes.contents((WItem) c)) {
                        String fs = fertof(FayteContents.what(sub));
                        if (fs != null) {
                            have.add(fs);
                        }
                    }
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (String[] f : FERT) {
            if (have.contains(f[0])) {
                out.add(f[0]);
            }
        }
        return out;
    }

    private static String fertdesc(String name) {
        for (String[] f : FERT) {
            if (f[0].equals(name)) {
                List<String> p = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    int v = Integer.parseInt(f[i + 1]);
                    if (v != 0) {
                        p.add(FERTCOLS[i] + " " + (v > 0 ? "+" : "") + v);
                    }
                }
                return String.join(", ", p);
            }
        }
        return "";
    }

    private static String tier(int sum) {
        if (sum < 75) {
            return "Tier 1";
        } else if (sum < 100) {
            return "Tier 1 & 2";
        } else if (sum < 150) {
            return "Tier 2";
        }
        return "Tier 2 & 3";
    }

    private static Color tiercol(int sum) {
        if (sum < 75) {
            return new Color(0xB8, 0xB0, 0xA0);
        } else if (sum < 100) {
            return new Color(0x9C, 0xC8, 0x8C);
        } else if (sum < 150) {
            return new Color(0x6C, 0xB0, 0xF0);
        }
        return GOLD;
    }

    private Text text(String s, Color c, boolean big) {
        String k = (big ? "b" : "") + c.getRGB() + "|" + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 300) {
                texts.clear();
            }
            t = (big ? Window.bigtf : FayteSkin.labelf).render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private int lh() {
        return FayteSkin.labelf.height();
    }

    private int head(GOut g, String s, int x, int y, int w) {
        g.chcolor(FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.6));
        g.frect(new Coord(x, y), new Coord(w, lh() + 6));
        g.chcolor();
        g.aimage(text(s, new Color(0xE3, 0xA8, 0x4A), false).tex(), new Coord(x + w / 2, y + 3), 0.5, 0.0);
        return y + lh() + 12;
    }

    @Override
    public void cdraw(GOut g) {
        scan();
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.7);
        int x0 = PAD, w = asz.x - PAD * 2, x1 = x0 + w;
        String nm = gobid >= 0 ? FayteLabels.get("gob:" + gobid) : null;
        g.image(text(nm != null ? nm : "Tilled Field", FayteSkin.TEXT, true).tex(), new Coord(x0, 4));
        String sub;
        if (planted == -2 || cropcount == 0) {
            sub = "Nothing planted";
        } else {
            sub = "Planted: " + (plantedname != null ? plantedname : "?")
                    + (stage >= 0 ? "  \u00b7  stage " + stage : "");
        }
        g.image(text(sub, dim, false).tex(), new Coord(x0, Window.bigtf.height() + 6));
        int y = Window.bigtf.height() + lh() + 14;
        List<String> ls = labels(src);
        int col = 0;
        for (String l : ls) {
            int cx = col == 0 ? x0 : x0 + w / 2 + 6;
            int cw = w / 2 - 6;
            int ci = l.lastIndexOf(':');
            String k = ci > 0 ? l.substring(0, ci).trim() : l.trim();
            String v = ci > 0 ? l.substring(ci + 1).trim() : "";
            g.image(text(k, dim, false).tex(), new Coord(cx, y));
            g.aimage(text(v, FayteSkin.TEXT, false).tex(), new Coord(cx + cw, y), 1.0, 0.0);
            if (col == 1) {
                y += lh() + 4;
            }
            col = 1 - col;
        }
        if (col == 1) {
            y += lh() + 4;
        }
        y += 6;
        y = head(g, "Influence", x0, y, w);
        bartop = y;
        int[] bv = bars();
        int[] reads = planted >= 0 ? (int[]) CROPS[planted][2] : null;
        int raises = planted >= 0 ? (Integer) CROPS[planted][3] : -1;
        int sx = x0 + FayteSkin.s(16);
        for (int i = 0; i < 4; i++) {
            boolean st = starred(i);
            g.image(
                    text(
                                    st ? "\u2605" : "\u2606",
                                    st ? GOLD : FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.4),
                                    false)
                            .tex(),
                    new Coord(x0, y));
            String mark = "";
            if (reads != null && (reads[0] == i + 1 || reads[1] == i + 1)) {
                mark = "  \u25c6 read by " + CROPS[planted][0];
            } else if (raises == i + 1) {
                mark = "  \u25b2 raised at harvest";
            }
            g.image(
                    text((i + 1) + "  " + raisedby(i) + mark, FayteSkin.TEXT, false)
                            .tex(),
                    new Coord(sx, y));
            g.aimage(
                    text(bv[i] < 0 ? "?" : Integer.toString(bv[i]), FayteSkin.TEXT, false)
                            .tex(),
                    new Coord(x1, y),
                    1.0,
                    0.0);
            int by = y + lh() + 2;
            g.chcolor(FayteSkin.mix(FayteSkin.PANEL, BARC[i], 0.25));
            g.frect(new Coord(sx, by), new Coord(x1 - sx, 6));
            if (bv[i] > 0) {
                g.chcolor(BARC[i]);
                g.frect(new Coord(sx, by), new Coord((int) ((x1 - sx) * Math.min(1.0, bv[i] / 100.0)), 6));
            }
            g.chcolor();
            y += rowh();
        }
        y += 4;
        y = head(g, "What each crop would give", x0, y, w);
        croptop = y;
        for (int i = 0; i < CROPS.length; i++) {
            int[] r = (int[]) CROPS[i][2];
            int sum = (bv[r[0] - 1] < 0 || bv[r[1] - 1] < 0) ? -1 : bv[r[0] - 1] + bv[r[1] - 1];
            boolean here = i == planted;
            if (here) {
                g.chcolor(FayteSkin.mix(FayteSkin.PANEL, GOLD, 0.15));
                g.frect(new Coord(x0, y - 1), new Coord(w, lh() + 2));
                g.chcolor();
            }
            g.image(text((String) CROPS[i][0], FayteText.LINK, false).tex(), new Coord(sx, y));
            g.image(
                    text("bars " + r[0] + "+" + r[1] + " = " + (sum < 0 ? "?" : Integer.toString(sum)), dim, false)
                            .tex(),
                    new Coord(x0 + w * 2 / 5, y));
            if (sum >= 0) {
                g.aimage(text(tier(sum), tiercol(sum), false).tex(), new Coord(x1, y), 1.0, 0.0);
            }
            y += lh() + 4;
        }
        y += 6;
        y = head(g, "Fertilizers you carry", x0, y, w);
        fertop = y;
        if (fertnames.isEmpty()) {
            g.image(
                    text("None in your bag. \"Fertilizers\" above lists them all.", dim, false)
                            .tex(),
                    new Coord(sx, y));
            y += lh() + 4;
        } else {
            for (String f : fertnames) {
                g.image(text(f, FayteText.LINK, false).tex(), new Coord(sx, y));
                g.aimage(text(fertdesc(f), dim, false).tex(), new Coord(x1, y), 1.0, 0.0);
                y += lh() + 4;
            }
        }
        y += 4;
        String note =
                "Harvest raises the crop's bar by 3\u00d7 the field's Influence % (5\u00d7 with Three-field System);"
                        + " the other bars drop by 5.";
        Text nt = texts.get("note|" + w);
        if (nt == null) {
            nt = FayteSkin.labelf.renderwrap(note, dim, w);
            texts.put("note|" + w, nt);
        }
        g.image(nt.tex(), new Coord(x0, y));
        y += nt.sz().y + PAD;
        if (asz.y != y) {
            resize(new Coord(asz.x, y));
        }
    }

    private int rowh() {
        return lh() + 12;
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        Coord p = c.sub(atl);
        if (bartop >= 0 && p.y >= bartop && p.y < bartop + 4 * rowh()) {
            int b = (p.y - bartop) / rowh();
            if (button == 3 || p.x < PAD + FayteSkin.s(16)) {
                togglestar(b);
            } else if (button == 1) {
                FayteAlmanacWnd.showlink(gui, "Tilled Field");
            }
            return true;
        }
        if (button == 1 && croptop >= 0 && p.y >= croptop && p.y < croptop + CROPS.length * (lh() + 4)) {
            FayteAlmanacWnd.showlink(gui, (String) CROPS[(p.y - croptop) / (lh() + 4)][1]);
            return true;
        }
        if (button == 1 && fertop >= 0 && p.y >= fertop && p.y < fertop + fertnames.size() * (lh() + 4)) {
            FayteAlmanacWnd.showlink(gui, fertnames.get((p.y - fertop) / (lh() + 4)));
            return true;
        }
        return super.mousedown(c, button);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        Coord p = c.sub(atl);
        if (bartop >= 0 && p.y >= bartop && p.y < bartop + 4 * rowh()) {
            return "Right-click (or click the star) to star this bar for this field. Click for the tier charts.";
        }
        if (croptop >= 0 && p.y >= croptop && p.y < croptop + CROPS.length * (lh() + 4)) {
            return "Tier from the sum of the two bars this crop reads: under 75 tier 1, 75-100 mixed, 100-150 tier 2,"
                    + " 150+ tier 2 and 3. Click to open the crop.";
        }
        return super.tooltip(c, prev);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if (!src.linked()) {
            ui.destroy(this);
            return;
        }
        if (src.visible) {
            src.hide();
        }
        Gob f = gobid >= 0 ? gui.ui.sess.glob.oc.getgob(gobid) : null;
        Gob pl = gui.map == null ? null : gui.map.player();
        if (gobid >= 0 && (f == null || (pl != null && pl.rc != null && f.rc != null && pl.rc.dist(f.rc) > 6 * 11))) {
            ui.destroy(this);
        }
    }

    @Override
    public void destroy() {
        cards.remove(src);
        if (src.linked()) {
            src.wdgmsg("close");
        }
        super.destroy();
    }

    @Override
    protected boolean compactstyle() {
        return true;
    }
}
