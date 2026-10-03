package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteShopList extends Widget {
    private static final int W = FayteSkin.s(260);
    private static final Color READY = new Color(0x7A, 0xC0, 0x6A);
    private static final Color SHORT = new Color(0xE0, 0x80, 0x60);
    private static final Color STORED = new Color(0xE0, 0xC0, 0x60);
    private static final Color GOLD = new Color(0xC8, 0xAA, 0x62);
    private static FayteShopList instance = null;
    private static List<Object[]> entries = null;
    private static String entriesof = null;
    private final Map<String, Text> texts = new HashMap<>();
    private final List<Object[]> hits = new ArrayList<>();
    private List<Object[]> lines = new ArrayList<>();
    private long lastcalc = 0L;

    private FayteShopList(Coord c, Widget parent) {
        super(c, new Coord(W, FayteSkin.s(40)), parent);
    }

    private static String key() {
        String c = Config.currentCharName;
        return "fayte_shop_" + (c == null ? "" : c);
    }

    private static synchronized List<Object[]> entries() {
        String k = key();
        if (entries == null || !k.equals(entriesof)) {
            entriesof = k;
            entries = new ArrayList<>();
            for (String l : Utils.getpref(k, "").split("\n")) {
                String[] p = l.split("\t");
                if (p.length == 2) {
                    try {
                        entries.add(new Object[] {p[0], Integer.parseInt(p[1])});
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
        return entries;
    }

    private static synchronized void save() {
        StringBuilder sb = new StringBuilder();
        for (Object[] e : entries()) {
            sb.append(e[0]).append('\t').append(e[1]).append('\n');
        }
        Utils.setpref(key(), sb.toString());
    }

    public static synchronized void add(GameUI gui, String recipe, int qty) {
        for (Object[] e : entries()) {
            if (e[0].equals(recipe)) {
                e[1] = (Integer) e[1] + qty;
                save();
                sync(gui);
                FayteMsg.say("Shopping list: " + recipe + " is now \u00d7" + e[1] + ".");
                return;
            }
        }
        entries().add(new Object[] {recipe, qty});
        save();
        sync(gui);
        FayteMsg.say("Added " + recipe + " \u00d7" + qty + " to your shopping list.");
    }

    public static void sync(GameUI gui) {
        boolean want = !entries().isEmpty();
        if (want && (instance == null || instance.parent != gui)) {
            instance =
                    FayteLanding.add(gui, FayteHud.reg(new FayteShopList(FayteLanding.anchor(gui), gui), "shoplist"));
        } else if (!want && instance != null) {
            if (instance.attached()) {
                instance.ui.destroy(instance);
            }
            instance = null;
        }
    }

    private Text text(String s, Color c, boolean bold) {
        String k = c.getRGB() + (bold ? "|b|" : "|") + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 300) {
                texts.clear();
            }
            t = (bold ? FayteSkin.titlef : FayteSkin.labelf).render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private void calc(GameUI gui) {
        List<Object[]> out = new ArrayList<>();
        for (Object[] e : new ArrayList<>(entries())) {
            String r = (String) e[0];
            int q = (Integer) e[1];
            List<Object[]> rows = new ArrayList<>();
            boolean ok = true;
            boolean movable = true;
            for (String[] in : FayteRecipes.ingredients(r)) {
                if (FayteRecipes.isstation(in[0])) {
                    continue;
                }
                int per = 1;
                try {
                    per = Integer.parseInt(in[1].trim());
                } catch (NumberFormatException ex) {
                }
                int need = per * q;
                List<String> names = FayteStash.names(in[0]);
                int have = FayteRecipes.have(gui, r, in[0]).count + FayteStash.inbag(gui, names);
                int stored = FayteStash.stored(names);
                String ch = FayteRecipes.choice(r, in[0]);
                String label = ch != null ? ch : in[0];
                ok &= have >= need;
                movable &= have + stored >= need;
                rows.add(new Object[] {label, have, need, stored});
            }
            out.add(new Object[] {r, q, ok, rows, movable});
        }
        lines = out;
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        long now = System.currentTimeMillis();
        GameUI gui = getparent(GameUI.class);
        if (gui != null && now - lastcalc > 1000L) {
            lastcalc = now;
            calc(gui);
        }
    }

    private int rowh() {
        return FayteSkin.labelf.height() + 4;
    }

    @Override
    public void draw(GOut g) {
        hits.clear();
        int rh = rowh();
        int h = FayteSkin.s(26);
        for (Object[] l : lines) {
            h += rh + 2 + ((List<?>) l[3]).size() * rh + 4;
        }
        if (sz.y != h) {
            sz = new Coord(W, h);
        }
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.65);
        FayteSkin.box(g, Coord.z, sz, FayteSkin.PANEL, FayteSkin.BORDER);
        g.image(text("Shopping list", GOLD, true).tex(), new Coord(6, 4));
        Text clr = text("Clear", dim, false);
        Coord cc = new Coord(sz.x - clr.sz().x - 8, 5);
        g.image(clr.tex(), cc);
        hits.add(new Object[] {cc, clr.sz(), "clear", null});
        int y = FayteSkin.s(26);
        for (Object[] l : lines) {
            boolean ok = (Boolean) l[2];
            String head = l[0] + " \u00d7" + l[1];
            g.image(text(head, ok ? READY : FayteSkin.TEXT, true).tex(), new Coord(6, y));
            hits.add(new Object[] {new Coord(6, y), new Coord(sz.x - 40, rh), "open", l[0]});
            Text x = text("\u2715", dim, false);
            Coord xc = new Coord(sz.x - x.sz().x - 8, y + 1);
            g.image(x.tex(), xc);
            hits.add(new Object[] {xc.sub(4, 2), x.sz().add(8, 4), "remove", l[0]});
            if (ok || (Boolean) l[4]) {
                Text rd = ok ? text("ready", READY, false) : text("move stored items to your bag", STORED, false);
                g.image(rd.tex(), new Coord(xc.x - rd.sz().x - 8, y + 1));
            }
            y += rh + 2;
            @SuppressWarnings("unchecked")
            List<Object[]> rows = (List<Object[]>) l[3];
            for (Object[] r : rows) {
                int have = (Integer) r[1];
                int need = (Integer) r[2];
                int stored = (Integer) r[3];
                Color c = have >= need ? READY : (have + stored >= need ? STORED : SHORT);
                g.image(text((String) r[0], dim, false).tex(), new Coord(16, y));
                Text ht = text(Math.min(have, need) + " / " + need, c, false);
                g.aimage(ht.tex(), new Coord(sz.x - 8, y), 1.0, 0.0);
                if (stored > 0 && have < need) {
                    g.aimage(
                            text("+" + stored + " stored", STORED, false).tex(),
                            new Coord(sz.x - 14 - ht.sz().x, y),
                            1.0,
                            0.0);
                }
                hits.add(new Object[] {new Coord(16, y), new Coord(sz.x - 80, rh), "open", r[0]});
                y += rh;
            }
            y += 4;
        }
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (button != 1 || FayteHud.editing()) {
            return false;
        }
        GameUI gui = getparent(GameUI.class);
        for (Object[] h : hits) {
            if (c.isect((Coord) h[0], (Coord) h[1])) {
                String what = (String) h[2];
                if (what.equals("clear")) {
                    synchronized (FayteShopList.class) {
                        entries().clear();
                    }
                    save();
                    sync(gui);
                } else if (what.equals("remove")) {
                    synchronized (FayteShopList.class) {
                        entries().removeIf(e -> e[0].equals(h[3]));
                    }
                    save();
                    sync(gui);
                    lastcalc = 0L;
                } else if (gui != null) {
                    FayteAlmanacWnd.showlink(gui, (String) h[3]);
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        for (Object[] h : hits) {
            if (c.isect((Coord) h[0], (Coord) h[1])) {
                String what = (String) h[2];
                return what.equals("clear")
                        ? "Empty the shopping list"
                        : what.equals("remove") ? "Take this off the list" : "Open " + h[3] + " in the Almanac";
            }
        }
        return "Counts what's in your bag against what these recipes need. \"Stored\" is in your backpack and carried"
                + " sacks (as of the last time you opened them); move it to your bag to craft. Add more from Almanac"
                + " \u2192 Recipes \u2192 Shopping list.";
    }
}
