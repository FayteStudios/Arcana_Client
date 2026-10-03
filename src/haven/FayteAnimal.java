package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

public class FayteAnimal extends Window {
    public static final String[] STATS = {"Tranquility", "Immunity", "Metabolism", "Size", "Productivity", "Longevity"};
    private static final Color[] COLS = {
        new Color(0xEE, 0x22, 0x22),
        new Color(0x22, 0x88, 0x22),
        new Color(0xEE, 0xEE, 0x44),
        new Color(0xEE, 0x66, 0x22),
        new Color(0x22, 0x44, 0xEE),
        new Color(0x66, 0x22, 0xAA)
    };
    private static final Color TEMPC = new Color(0xB0, 0x40, 0x40);
    private static final Color GOLD = new Color(0xF0, 0xD0, 0x48);
    private static final Color SILVER = new Color(0xC8, 0xCE, 0xD8);
    private static final String[] DESCS = {
        "How easily its Temperament goes down.",
        "Resistance to sickness.",
        "How much it eats (at least 2 a day).",
        "How much meat it gives when butchered.",
        "How fast it produces (milk, wool, ...).",
        "How long it lives (up to about 105 days).",
    };
    private static final String[][] SPECIES = {
        {"Pig", "Walnut", "piglet", "sow", "boar", "pig,hog,swine"},
        {"Cow", "Clover", "calf,calve", "heifer", "bull,ox", "cow,cattle"},
        {"Sheep", "Carrot", "lamb", "ewe", "ram", "sheep"},
        {"Goat", "Baby Corn", "kid", "doe,nanny", "buck,billy", "goat"},
        {"Horse", "Coarse Salt", "foal", "mare", "stallion", "horse"},
    };
    private static final String[][] DEFSTARS = {
        {"Pig", "Size", "Immunity"},
        {"Cow", "Productivity", "Immunity,Longevity"},
        {"Sheep", "Productivity", "Immunity,Longevity"},
        {"Goat", "Productivity", "Immunity,Longevity"},
        {"Horse", "Longevity", "Size,Immunity"},
    };
    private static final String RAISE =
            "hunger, sickness and not mating (slowly); being near a stillborn; branding (a lot) and castrating; seeing"
                    + " an unnatural death";
    private static final int PAD = 8;
    private static volatile Gob lastgob = null;
    private static volatile long lastat = 0L;
    private static final Map<Window, FayteAnimal> cards = new WeakHashMap<>();
    private final Window src;
    private final String species;
    private final String sex;
    private final String pre;
    private final String treat;
    private final Map<String, Text> texts = new HashMap<>();
    private final FayteWikiEntry.View view;
    private int toph = -1;

    public static void clicked(Gob g) {
        FayteLabels.opened("gob:" + g.id);
        lastgob = g;
        lastat = System.currentTimeMillis();
    }

    public static void meter(VMeter m) {
        if (!FayteModules.ALMANAC.on() || m.stat == null) {
            return;
        }
        Window w = m.getparent(Window.class);
        GameUI gui = m.getparent(GameUI.class);
        if (w == null || gui == null || cards.containsKey(w)) {
            return;
        }
        int n = 0;
        for (VMeter v : meters(w)) {
            if (idx(v.stat) >= 0) {
                n++;
            }
        }
        if (n >= 4) {
            cards.put(w, new FayteAnimal(gui, w));
        }
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
            if (c instanceof Label && ((Label) c).texts != null) {
                l.add(((Label) c).texts);
            }
            l.addAll(labels(c));
        }
        return l;
    }

    private static int idx(String stat) {
        if (stat == null) {
            return -1;
        }
        String s = stat.toLowerCase();
        for (int i = 0; i < STATS.length; i++) {
            String k = STATS[i].toLowerCase();
            if (s.startsWith(k.substring(0, Math.min(4, k.length())))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean word(String hay, String words) {
        for (String w : words.split(",")) {
            if (Pattern.compile("\\b" + w + "s?\\b").matcher(hay).find()) {
                return true;
            }
        }
        return false;
    }

    private FayteAnimal(GameUI gui, Window src) {
        super(src.c, new Coord(FayteSkin.s(330), FayteSkin.s(420)), gui, "Animal");
        this.src = src;
        justclose = true;
        String rn = null;
        Gob g = lastgob;
        if (g != null && System.currentTimeMillis() - lastat < 30000L) {
            rn = FayteMsg.resname(g);
        }
        String hay = ((src.cap == null ? "" : src.cap.text) + " "
                        + (rn == null ? "" : rn.replace('/', ' ').replace('_', ' ')))
                .toLowerCase();
        String sp = null, sx = null, pr = "F", tr = null;
        for (String[] s : SPECIES) {
            if (word(hay, s[2])) {
                sx = "Young";
                pr = "Y";
            } else if (word(hay, s[3])) {
                sx = "Female";
                pr = "F";
            } else if (word(hay, s[4])) {
                sx = "Male";
                pr = "M";
            } else if (!word(hay, s[5])) {
                continue;
            }
            sp = s[0];
            tr = s[1];
            break;
        }
        species = sp;
        sex = sx;
        pre = pr;
        treat = tr;
        addtwdg(new FayteTitleButton(this, "Almanac", "Open this animal in the Almanac", () -> {
            if (species != null) {
                FayteAlmanacWnd.showlink(gui, species);
            }
        }));
        view = new FayteWikiEntry.View(new Coord(PAD, 0), new Coord(10, 10), this, card());
        view.onlink = (t) -> FayteAlmanacWnd.showlink(gui, t);
        src.hide();
    }

    private static String links(String list) {
        List<String> out = new ArrayList<>();
        for (String p : FayteMarkup.plain(list).split(",")) {
            String n = p.trim();
            if (!n.isEmpty()) {
                out.add(FayteMarkup.link(n, n));
            }
        }
        return String.join(", ", out);
    }

    private FayteWikiEntry card() {
        FayteWikiEntry en = new FayteWikiEntry();
        FayteWikiData.Entry e = species == null ? null : FayteWikiData.find(species);
        if (e != null) {
            FayteWikiEntry.Section s = new FayteWikiEntry.Section();
            s.title = "What it gives";
            String[][] keys = {{"Products", "Products"}, {"Items gained", "Butchered"}, {"Rare items", "Rare"}};
            for (Map.Entry<String, Map<String, String>> t : e.tpl.entrySet()) {
                if (!t.getKey().split("#")[0].trim().equalsIgnoreCase("Domesticated")) {
                    continue;
                }
                Map<String, String> a = t.getValue();
                for (String[] k : keys) {
                    String v = a.get(pre + k[0]);
                    if (v == null) {
                        v = a.get(pre + k[0] + " ");
                    }
                    if (v != null
                            && !FayteMarkup.plain(v).replaceAll("[,\\s]", "").isEmpty()) {
                        s.rows.add(new String[] {k[1], links(v)});
                    }
                }
            }
            if (!s.rows.isEmpty()) {
                en.sections.add(s);
            }
        }
        FayteWikiEntry.Section t = new FayteWikiEntry.Section();
        t.title = "Temperament";
        if (treat != null) {
            t.rows.add(new String[] {"Treat", FayteMarkup.link(treat, treat) + " lowers it"});
        }
        t.rows.add(new String[] {"Raised by", RAISE});
        en.sections.add(t);
        return en;
    }

    private static String starkey(String species) {
        return "fayte_animal_stars2_" + (species == null ? "any" : species.toLowerCase());
    }

    private int star(String stat) {
        String def = "|";
        for (String[] d : DEFSTARS) {
            if (d[0].equals(species)) {
                def = d[1] + "|" + d[2];
            }
        }
        String[] p = Utils.getpref(starkey(species), def).split("\\|", -1);
        if (p.length > 0 && Arrays.asList(p[0].split(",")).contains(stat)) {
            return 2;
        } else if (p.length > 1 && Arrays.asList(p[1].split(",")).contains(stat)) {
            return 1;
        }
        return 0;
    }

    private void cyclestar(String stat) {
        List<String> gold = new ArrayList<>(), silver = new ArrayList<>();
        for (String st : STATS) {
            int v = star(st);
            if (st.equals(stat)) {
                v = v == 0 ? 2 : (v == 2 ? 1 : 0);
            }
            if (v == 2) {
                gold.add(st);
            } else if (v == 1) {
                silver.add(st);
            }
        }
        Utils.setpref(starkey(species), String.join(",", gold) + "|" + String.join(",", silver));
    }

    private Text text(String s, Color c, boolean big) {
        String k = (big ? "b" : "") + c.getRGB() + "|" + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 200) {
                texts.clear();
            }
            t = (big ? Window.bigtf : FayteSkin.labelf).render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private Map<String, Integer> values() {
        Map<String, Integer> v = new LinkedHashMap<>();
        for (VMeter m : meters(src)) {
            if (m.stat != null) {
                v.put(m.stat, m.val >= 0 ? m.val : m.amount);
            }
        }
        return v;
    }

    private int rowh() {
        return FayteSkin.labelf.height() + 12;
    }

    private int statstop() {
        return Window.bigtf.height() + FayteSkin.labelf.height() + 14;
    }

    private void bar(GOut g, int x, int y, int w, double f, Color c) {
        g.chcolor(FayteSkin.mix(FayteSkin.PANEL, c, 0.25));
        g.frect(new Coord(x, y), new Coord(w, 5));
        g.chcolor(c);
        g.frect(new Coord(x, y), new Coord((int) (w * Math.max(0.0, Math.min(1.0, f))), 5));
        g.chcolor();
    }

    @Override
    public void cdraw(GOut g) {
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.7);
        int x0 = PAD, w = asz.x - PAD * 2, x1 = x0 + w;
        String name = src.cap == null ? "Animal" : src.cap.text;
        g.image(text(name, FayteSkin.TEXT, true).tex(), new Coord(x0, 4));
        String sub = species == null ? "Unknown kind" : (sex != null ? sex + " " : "") + species.toLowerCase();
        g.image(text(sub, dim, false).tex(), new Coord(x0, Window.bigtf.height() + 6));
        Map<String, Integer> vals = values();
        int y = statstop();
        int sx = x0 + FayteSkin.s(16);
        int total = 0;
        for (int i = 0; i < STATS.length; i++) {
            Integer v = null;
            for (Map.Entry<String, Integer> e : vals.entrySet()) {
                if (idx(e.getKey()) == i) {
                    v = e.getValue();
                }
            }
            int star = this.star(STATS[i]);
            g.image(
                    text(
                                    star > 0 ? "\u2605" : "\u2606",
                                    star == 2
                                            ? GOLD
                                            : (star == 1
                                                    ? SILVER
                                                    : FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.4)),
                                    false)
                            .tex(),
                    new Coord(x0, y));
            g.image(text(STATS[i], FayteSkin.TEXT, false).tex(), new Coord(sx, y));
            g.aimage(
                    text(v == null ? "?" : Integer.toString(v), FayteSkin.TEXT, false)
                            .tex(),
                    new Coord(x1, y),
                    1.0,
                    0.0);
            bar(g, sx, y + FayteSkin.labelf.height() + 2, x1 - sx, v == null ? 0 : v / 100.0, COLS[i]);
            if (v != null) {
                total += v;
            }
            y += rowh();
        }
        for (String l : labels(src)) {
            int ci = l.lastIndexOf(':');
            String k = ci > 0 ? l.substring(0, ci).trim() : l.trim();
            String v = ci > 0 ? l.substring(ci + 1).trim() : "";
            if (k.isEmpty()) {
                continue;
            }
            g.image(text(k, FayteSkin.TEXT, false).tex(), new Coord(sx, y));
            g.aimage(text(v, FayteSkin.TEXT, false).tex(), new Coord(x1, y), 1.0, 0.0);
            String num = v.replaceAll("[^0-9]", "");
            if (k.toLowerCase().startsWith("temper") && !num.isEmpty() && num.length() < 5) {
                bar(g, sx, y + FayteSkin.labelf.height() + 2, x1 - sx, Integer.parseInt(num) / 100.0, TEMPC);
                y += rowh();
            } else {
                y += FayteSkin.labelf.height() + 6;
            }
        }
        String tl = "Total " + total;
        g.image(text(tl, FayteSkin.TEXT, false).tex(), new Coord(x0, y + 2));
        y += FayteSkin.labelf.height() + 12;
        if (toph != y) {
            toph = y;
            int vh = FayteSkin.s(200);
            view.c = new Coord(x0, y);
            view.resize(new Coord(w, vh));
            resize(new Coord(asz.x, y + vh + PAD));
        }
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        Coord p = c.sub(atl);
        int y0 = statstop();
        if (button == 3 && p.y >= y0 && p.y < y0 + STATS.length * rowh()) {
            cyclestar(STATS[(p.y - y0) / rowh()]);
            return true;
        }
        return super.mousedown(c, button);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        Coord p = c.sub(atl);
        int y0 = statstop();
        if (p.y >= y0 && p.y < y0 + STATS.length * rowh() && p.x >= 0) {
            int i = (p.y - y0) / rowh();
            return STATS[i] + ": " + DESCS[i] + " Right-click: gold star, silver star, none (for all "
                    + (species == null ? "animals of this kind" : species.toLowerCase() + "s") + ").";
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
