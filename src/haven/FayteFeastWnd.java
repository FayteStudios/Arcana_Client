package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FayteFeastWnd extends Window {
    private String eatenkey = null;
    private Text eatentext = null;
    public static final int CAP = 10;
    private static final Color GOOD = new Color(0x7A, 0xC0, 0x6A);
    private static final Color BAD = new Color(0xC8, 0x5A, 0x50);
    private static FayteFeastWnd instance = null;
    private static Gobble gob = null;
    private static long pending = 0L;
    private static final Map<WItem, String> snap = new IdentityHashMap<>();
    private static final Map<String, Integer> eaten = new LinkedHashMap<>();
    private static int selhum = -1;
    private final GameUI gui;
    private final Map<String, Text> texts = new HashMap<>();
    private final Map<Indir<Resource>, Tex> icons = new HashMap<>();
    private long lastcalc = 0L;
    private int[][] plan = new int[4][3];
    private int foods = 0;

    public static boolean on() {
        return FayteSkin.on();
    }

    public static boolean active() {
        return gob != null && gob.linked();
    }

    public static void start(GameUI gui, Gobble g) {
        gob = g;
        g.faytehide();
        snap.clear();
        eaten.clear();
        pending = 0L;
        selhum = -1;
        if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
        }
        instance = new FayteFeastWnd(gui);
    }

    public static void stop() {
        gob = null;
        selhum = -1;
        if (instance != null && instance.attached()) {
            instance.savepos();
            instance.ui.destroy(instance);
        }
        instance = null;
    }

    public static void gtm(int[] old, int[] now) {
        if (!active() || old == null) {
            return;
        }
        boolean up = false;
        for (int i = 0; i < 4 && i < now.length && i < old.length; i++) {
            up |= now[i] > old[i];
        }
        if (up) {
            pending = System.currentTimeMillis();
        }
    }

    private static Coord startpos(GameUI gui, Coord sz) {
        String[] p = Utils.getpref("fayte_feast_pos", "").split(",");
        try {
            if (p.length == 2) {
                return new Coord(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
            }
        } catch (NumberFormatException e) {
        }
        Window iw = gui.maininv == null ? null : gui.maininv.getparent(Window.class);
        if (iw != null && iw.visible) {
            int x = iw.c.x - sz.x - FayteSkin.s(10);
            if (x < 0) {
                x = iw.c.x + iw.sz.x + FayteSkin.s(10);
            }
            return new Coord(x, iw.c.y);
        }
        return new Coord((gui.sz.x - sz.x) / 2, FayteSkin.s(90));
    }

    private FayteFeastWnd(GameUI gui) {
        super(Coord.z, new Coord(FayteSkin.s(380), FayteSkin.s(300)), gui, "Feast");
        this.gui = gui;
        cbtn.hide();
        c = startpos(gui, sz);
    }

    private void savepos() {
        Utils.setpref("fayte_feast_pos", c.x + "," + c.y);
    }

    @Override
    public boolean mouseup(Coord c, int button) {
        boolean r = super.mouseup(c, button);
        savepos();
        return r;
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

    private static String fmt(int v) {
        return String.format("%.1f", v / 1000.0);
    }

    private static int used(Gobble.TypeMod m) {
        return (int) Math.round(m.a * 100);
    }

    private static List<WItem> fooditems(GameUI gui) {
        List<WItem> l = new ArrayList<>();
        for (Inventory inv : FayteBagSel.inventories(gui)) {
            for (Widget w = inv.child; w != null; w = w.next) {
                if (w instanceof WItem && food(((WItem) w).item) != null) {
                    l.add((WItem) w);
                }
            }
        }
        return l;
    }

    private static GobbleInfo food(GItem it) {
        try {
            return ItemInfo.find(GobbleInfo.class, it.info());
        } catch (Loading e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean capped(GobbleInfo f) {
        if (gob == null) {
            return false;
        }
        for (int t : f.types) {
            if (t >= 0 && t < gob.mods.size() && used(gob.mods.get(t)) >= CAP) {
                return true;
            }
        }
        return false;
    }

    public static void drawitem(GOut g, WItem w) {
        if (!active()) {
            return;
        }
        GobbleInfo f = food(w.item);
        if (f == null) {
            return;
        }
        boolean cap = capped(f);
        boolean dim = cap || (selhum >= 0 && f.h[selhum] <= 0);
        if (dim) {
            g.chcolor(0, 0, 0, 120);
            g.frect(Coord.z, w.sz);
        }
        Color c = cap ? BAD : (selhum >= 0 ? Tempers.colors[selhum] : GOOD);
        if (!dim || cap) {
            g.chcolor(c.getRed(), c.getGreen(), c.getBlue(), 220);
            g.rect(Coord.z, w.sz.sub(1, 1));
        }
        g.chcolor();
    }

    private void calc() {
        List<WItem> items = fooditems(gui);
        foods = 0;
        List<GobbleInfo> fs = new ArrayList<>();
        for (WItem w : items) {
            GobbleInfo f = food(w.item);
            if (f != null && !capped(f)) {
                fs.add(f);
                foods++;
            }
        }
        for (int t = 0; t < 4; t++) {
            final int ft = t;
            List<GobbleInfo> order = new ArrayList<>(fs);
            order.sort((a, b) -> Integer.compare(b.h[ft], a.h[ft]));
            int[] rem = new int[gob.mods.size()];
            for (int i = 0; i < rem.length; i++) {
                rem[i] = Math.max(0, CAP - used(gob.mods.get(i)));
            }
            int lo = 0;
            int hi = 0;
            int n = 0;
            for (GobbleInfo f : order) {
                if (f.h[t] <= 0) {
                    continue;
                }
                boolean ok = true;
                for (int ty : f.types) {
                    if (ty >= 0 && ty < rem.length && rem[ty] <= 0) {
                        ok = false;
                    }
                }
                if (!ok) {
                    continue;
                }
                for (int ty : f.types) {
                    if (ty >= 0 && ty < rem.length) {
                        rem[ty]--;
                    }
                }
                lo += f.l[t];
                hi += f.h[t];
                n++;
            }
            plan[t] = new int[] {lo, hi, n};
        }
    }

    private void track() {
        long now = System.currentTimeMillis();
        List<WItem> items = fooditems(gui);
        if (pending != 0L && now - pending > 400L) {
            Set<WItem> cur = Collections.newSetFromMap(new IdentityHashMap<>());
            cur.addAll(items);
            for (Map.Entry<WItem, String> e : snap.entrySet()) {
                if (!cur.contains(e.getKey()) && e.getValue() != null) {
                    eaten.merge(e.getValue(), 1, Integer::sum);
                    FayteGains.ate(e.getValue());
                    FayteAlmanac.tally("eat", 1);
                }
            }
            pending = 0L;
        }
        if (pending == 0L) {
            snap.clear();
            for (WItem w : items) {
                snap.put(w, FayteAlmanac.itemname(w.item));
            }
        }
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if (!active()) {
            return;
        }
        if (gob.visible) {
            gob.faytehide();
        }
        track();
        long now = System.currentTimeMillis();
        if (now - lastcalc > 1000L) {
            lastcalc = now;
            calc();
        }
    }

    private GobbleInfo hovered() {
        if (ui.lasttip instanceof WItem.ItemTip) {
            try {
                return ItemInfo.find(
                        GobbleInfo.class, ((WItem.ItemTip) ui.lasttip).item().info());
            } catch (Loading e) {
            } catch (RuntimeException e) {
                FayteLog.once("FayteFeastWnd.hovered", e);
            }
        }
        return null;
    }

    private Tex icon(Indir<Resource> r) {
        Tex t = icons.get(r);
        if (t == null) {
            try {
                t = r.get().layer(Resource.imgc).tex();
                icons.put(r, t);
            } catch (Loading e) {
                return null;
            }
        }
        return t;
    }

    private static String typename(Indir<Resource> r) {
        try {
            Resource.Tooltip t = r.get().layer(Resource.tooltip);
            return t != null ? t.t : "...";
        } catch (Loading e) {
            return "...";
        }
    }

    private int humh() {
        return FayteSkin.s(46);
    }

    private int top() {
        return FayteSkin.s(26);
    }

    @Override
    public void cdraw(GOut g) {
        if (!active()) {
            return;
        }
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.65);
        int w = asz.x;
        GobbleInfo hov = hovered();
        g.image(text("Gobble points: " + gob.points, FayteSkin.TEXT, true).tex(), Coord.z);
        g.aimage(text(foods + " usable foods in open bags", dim, false).tex(), new Coord(w, FayteSkin.s(2)), 1.0, 0.0);
        int y = top();
        for (int t = 0; t < 4; t++) {
            Color hc = Tempers.colors[t];
            int lev = gob.lev[t];
            int max = Math.max(1, gob.lmax[t]);
            boolean sel = selhum == t;
            FayteSkin.box(
                    g,
                    new Coord(0, y),
                    new Coord(w, humh() - 4),
                    sel ? FayteSkin.mix(FayteSkin.PANEL, hc, 0.15) : FayteSkin.PANEL,
                    sel ? hc : FayteSkin.BORDER);
            g.image(text(Tempers.rnm[t], hc, true).tex(), new Coord(6, y + 3));
            String cur = fmt(lev) + " / " + fmt(gob.lmax[t]);
            g.aimage(text(cur, FayteSkin.TEXT, false).tex(), new Coord(w - 6, y + 4), 1.0, 0.0);
            Coord bc = new Coord(FayteSkin.s(96), y + 5);
            Coord bs = new Coord(w - FayteSkin.s(96) - FayteSkin.s(90), FayteSkin.s(10));
            FayteSkin.box(g, bc, bs, FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.4), FayteSkin.BORDER);
            int fw = (int) ((long) (bs.x - 2) * Math.min(lev, max) / max);
            if (hov != null && t < hov.h.length) {
                int hw = (int) ((long) (bs.x - 2) * Math.min(lev + hov.h[t], max) / max);
                g.chcolor(hc.getRed(), hc.getGreen(), hc.getBlue(), 90);
                g.frect(bc.add(1, 1), new Coord(hw, bs.y - 2));
            }
            g.chcolor(hc.getRed(), hc.getGreen(), hc.getBlue(), 230);
            g.frect(bc.add(1, 1), new Coord(fw, bs.y - 2));
            g.chcolor();
            String line;
            if (hov != null && t < hov.h.length) {
                line = "This food: +" + fmt(hov.l[t]) + " to +" + fmt(hov.h[t]);
            } else {
                int[] p = plan[t];
                line = p[2] == 0
                        ? "Nothing in your open bags adds " + Tempers.rnm[t]
                        : "From your open bags: +" + fmt(p[0]) + " to +" + fmt(p[1]) + " (" + p[2] + " foods)";
            }
            g.image(text(line, hov != null ? FayteSkin.TEXT : dim, false).tex(), new Coord(6, y + FayteSkin.s(22)));
            y += humh();
        }
        y += FayteSkin.s(4);
        g.image(
                text("Food groups this feast (each allows " + CAP + ")", dim, false)
                        .tex(),
                new Coord(0, y));
        y += FayteSkin.s(18);
        boolean[] hl = new boolean[gob.mods.size()];
        if (hov != null) {
            for (int ty : hov.types) {
                if (ty >= 0 && ty < hl.length) {
                    hl[ty] = true;
                }
            }
        }
        int rh = FayteSkin.s(22);
        if (gob.mods.isEmpty()) {
            g.image(text("None yet. Groups appear as you eat.", dim, false).tex(), new Coord(6, y));
            y += rh;
        }
        for (int i = 0; i < gob.mods.size(); i++) {
            Gobble.TypeMod m = gob.mods.get(i);
            int u = used(m);
            boolean full = u >= CAP;
            FayteSkin.box(
                    g,
                    new Coord(0, y),
                    new Coord(w, rh - 2),
                    hl[i] ? FayteSkin.mix(FayteSkin.PANEL, GOOD, 0.2) : FayteSkin.PANEL,
                    full ? BAD : (hl[i] ? GOOD : FayteSkin.BORDER));
            Tex ic = icon(m.t);
            if (ic != null) {
                g.image(ic, new Coord(3, y + 1), new Coord(rh - 4, rh - 4));
            }
            g.image(text(typename(m.t), full ? BAD : FayteSkin.TEXT, false).tex(), new Coord(rh + 4, y + 3));
            Coord bc = new Coord(w - FayteSkin.s(130), y + 6);
            Coord bs = new Coord(FayteSkin.s(80), FayteSkin.s(8));
            FayteSkin.box(g, bc, bs, FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.4), FayteSkin.BORDER);
            g.chcolor(full ? BAD : GOOD);
            g.frect(bc.add(1, 1), new Coord((bs.x - 2) * Math.min(u, CAP) / CAP, bs.y - 2));
            g.chcolor();
            g.aimage(
                    text(u + " / " + CAP, full ? BAD : FayteSkin.TEXT, false).tex(), new Coord(w - 6, y + 3), 1.0, 0.0);
            y += rh;
        }
        if (!eaten.isEmpty()) {
            y += FayteSkin.s(4);
            g.image(text("Eaten this feast", dim, false).tex(), new Coord(0, y));
            y += FayteSkin.s(18);
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : eaten.entrySet()) {
                sb.append(sb.length() > 0 ? ", " : "")
                        .append(e.getKey())
                        .append(e.getValue() > 1 ? " \u00d7" + e.getValue() : "");
            }
            String key = sb + "|" + (w - 6);
            if (!key.equals(eatenkey)) {
                eatenkey = key;
                eatentext = FayteSkin.labelf.renderwrap(sb.toString(), FayteSkin.TEXT, w - 6);
            }
            Text t = eatentext;
            g.image(t.tex(), new Coord(6, y));
            y += t.sz().y;
        }
        y += FayteSkin.s(4);
        if (y != asz.y) {
            resize(new Coord(asz.x, y));
        }
    }

    @Override
    public void mousemove(Coord c) {
        Coord p = c.sub(atl);
        int s = -1;
        if (p.x >= 0 && p.x < asz.x && p.y >= top() && p.y < top() + 4 * humh()) {
            s = (p.y - top()) / humh();
        }
        if (active()) {
            selhum = s;
        }
        super.mousemove(c);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        Coord p = c.sub(atl);
        if (p.y >= top() && p.y < top() + 4 * humh()) {
            return "Hover a humour to light up the foods in your bags that raise it. Hover a food to preview it here.";
        }
        return super.tooltip(c, prev);
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
