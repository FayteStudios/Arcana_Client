package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class FayteAbacus extends Widget {
    private static final int W = FayteSkin.s(200);
    private static final Map<Window, FayteAbacus> attached = new HashMap<>();

    static void reset() {
        attached.keySet().removeIf(w -> !w.attached());
    }

    private final Window win;
    private final Inventory inv;
    private final boolean main;
    private boolean detached;
    private Coord dragoff = null;
    private long lastcount = 0L;
    private List<String[]> rows = new ArrayList<>();
    private String hovname = null;
    private String pinname = null;

    private String lit() {
        return pinname != null ? pinname : hovname;
    }

    public static void drawitem(GOut g, WItem w) {
        if (attached.isEmpty()) {
            return;
        }
        for (FayteAbacus a : attached.values()) {
            String n = a.lit();
            if (n == null || w.parent != a.inv || !a.visible) {
                continue;
            }
            boolean match = n.equalsIgnoreCase(FayteSort.name(w));
            if (!match) {
                try {
                    String ct = ItemInfo.getContent(w.item.info());
                    match = ct != null && ct.toLowerCase().endsWith(n.toLowerCase());
                } catch (Loading e) {
                } catch (RuntimeException e) {
                    FayteLog.once("FayteAbacus.drawitem", e);
                }
            }
            if (match) {
                g.chcolor(0xF0, 0xD0, 0x48, 255);
                g.rect(Coord.z, w.sz.sub(1, 1));
                g.rect(new Coord(1, 1), w.sz.sub(3, 3));
                g.chcolor();
            }
        }
    }

    private String rowat(Coord c) {
        int i = (c.y - head()) / rowh() + scroll;
        return c.y >= head() && i >= 0 && i < rows.size() ? rows.get(i)[0] : null;
    }

    private int total = 0;
    private int scroll = 0;
    private final Map<String, Text> texts = new HashMap<>();

    public static int extra(Window w) {
        FayteAbacus a = attached.get(w);
        return a != null && a.linked() && !a.detached
                ? W + 2
                : (a == null && FayteSkin.on() && w.hasinv() && wanted(w) ? W + 2 : 0);
    }

    private static String key(Window w) {
        return "fayte_abacus_" + (w.cap == null ? "" : w.cap.text.toLowerCase().trim());
    }

    public static boolean wanted(Window w) {
        return Utils.getprefb(key(w), false);
    }

    public static void attach(Window w, Inventory inv) {
        if (!FayteSkin.on() || !w.linked()) {
            return;
        }
        GameUI gui = w.getparent(GameUI.class);
        FayteAbacus a = attached.get(w);
        if (gui != null && (a == null || !a.linked())) {
            attached.put(w, new FayteAbacus(gui, w, inv));
        }
    }

    public static void toggle(Window w, Inventory inv) {
        FayteAbacus a = attached.get(w);
        boolean on = a == null || !a.linked();
        Utils.setprefb(key(w), on);
        if (on) {
            attach(w, inv);
        } else {
            attached.remove(w);
            a.ui.destroy(a);
        }
    }

    private FayteAbacus(GameUI gui, Window win, Inventory inv) {
        super(Coord.z, new Coord(W, win.sz.y), gui);
        this.win = win;
        this.inv = inv;
        main = "inv".equals(inv.fkey);
        detached = main && Utils.getprefb("fayte_abacus_detached", false);
        if (detached) {
            String[] p = Utils.getpref("fayte_abacus_pos", "").split(",");
            try {
                c = new Coord(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
            } catch (RuntimeException e) {
                c = win.c.add(win.sz.x + 2, 0);
            }
        }
        follow();
    }

    private void follow() {
        if (detached) {
            return;
        }
        Coord want = win.c.add(win.sz.x + 2, 0);
        if (!want.equals(c)) {
            c = want;
        }
        if (sz.y != win.sz.y) {
            sz = new Coord(sz.x, Math.max(FayteSkin.s(80), win.sz.y));
        }
    }

    private void count() {
        Map<String, double[]> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, String> units = new HashMap<>();
        int n = 0;
        for (WItem w : FayteXfer.items(inv)) {
            n++;
            String name = FayteSort.name(w);
            double amt = 1;
            String unit = null;
            try {
                String ct = ItemInfo.getContent(w.item.info());
                if (ct != null) {
                    String[] parts = ct.split(" ", 4);
                    if (parts.length == 4) {
                        amt = Double.parseDouble(parts[0]);
                        unit = parts[1];
                        name = parts[3];
                    }
                }
            } catch (Loading e) {
            } catch (RuntimeException e) {
                FayteLog.once("FayteAbacus.count", e);
            }
            if (name == null) {
                continue;
            }
            double[] v = m.computeIfAbsent(name, k -> new double[2]);
            v[0] += amt;
            v[1]++;
            if (unit != null) {
                units.put(name, unit);
            }
        }
        List<String[]> r = new ArrayList<>();
        List<Map.Entry<String, double[]>> es = new ArrayList<>(m.entrySet());
        es.sort((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]));
        for (Map.Entry<String, double[]> e : es) {
            String u = units.get(e.getKey());
            String amt =
                    u != null ? String.format("%.2f %s", e.getValue()[0], u) : Integer.toString((int) e.getValue()[0]);
            r.add(new String[] {e.getKey(), amt});
        }
        rows = r;
        total = n;
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

    private int head() {
        return FayteSkin.s(24);
    }

    private int rowh() {
        return FayteSkin.labelf.height() + 3;
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if (!win.linked() || !inv.linked()) {
            attached.remove(win);
            ui.destroy(this);
            return;
        }
        boolean vis = win.visible;
        if (vis != visible) {
            show(vis);
        }
        follow();
        long now = System.currentTimeMillis();
        if (now - lastcount > 500L) {
            lastcount = now;
            count();
        }
    }

    @Override
    public void draw(GOut g) {
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.65);
        FayteSkin.box(g, Coord.z, sz, FayteSkin.PANEL, win.edgecolor());
        g.image(
                text("Abacus  \u00b7  " + total + (total == 1 ? " item" : " items"), FayteSkin.TEXT)
                        .tex(),
                new Coord(6, 4));
        if (main) {
            String b = detached ? "Attach" : "Detach";
            Text bt = text(b, dim);
            g.image(bt.tex(), new Coord(sz.x - bt.sz().x - 6, 4));
        }
        int fw = W;
        for (String[] r : rows) {
            fw = Math.max(
                    fw,
                    text(label(r[0]), dim).sz().x + text(r[1], FayteSkin.TEXT).sz().x + 24);
        }
        fw = Math.min(fw, FayteSkin.s(340));
        if (fw != sz.x) {
            sz = new Coord(fw, sz.y);
        }
        int rh = rowh();
        int vis = Math.max(1, (sz.y - head() - 4) / rh);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - vis)));
        if (rows.isEmpty()) {
            g.image(text("Empty", dim).tex(), new Coord(6, head()));
        }
        for (int i = 0; i < vis && i + scroll < rows.size(); i++) {
            String[] r = rows.get(i + scroll);
            int y = head() + i * rh;
            Text amt = text(r[1], FayteSkin.TEXT);
            String nm = label(r[0]);
            Text nt = text(nm, dim);
            while (nt.sz().x > sz.x - amt.sz().x - 18 && nm.length() > 3) {
                nm = nm.substring(0, nm.length() - 1);
                nt = text(nm.trim() + "\u2026", dim);
            }
            if (r[0].equals(lit())) {
                g.chcolor(0xF0, 0xD0, 0x48, 60);
                g.frect(new Coord(2, y - 1), new Coord(sz.x - 4, rh));
                g.chcolor();
            }
            g.image(nt.tex(), new Coord(6, y));
            g.aimage(amt.tex(), new Coord(sz.x - 6, y), 1.0, 0.0);
        }
    }

    private String label(String name) {
        String wg = FayteCraving.winegroup(name);
        return wg == null ? name : wg + " (" + name.replaceAll("(?i)\\s*wine$", "") + ")";
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (button == 1 && main && c.y < head() && c.x > sz.x - FayteSkin.s(60)) {
            detached = !detached;
            Utils.setprefb("fayte_abacus_detached", detached);
            if (!detached) {
                follow();
            }
            return true;
        }
        String rn = button == 1 ? rowat(c) : null;
        if (rn != null) {
            pinname = rn.equals(pinname) ? null : rn;
            return true;
        }
        if (button == 1 && detached && c.y < head()) {
            dragoff = c;
            ui.grabmouse(this);
            return true;
        }
        return true;
    }

    @Override
    public void mousemove(Coord c) {
        hovname = c.isect(Coord.z, sz) ? rowat(c) : null;
        if (dragoff != null) {
            this.c = this.c.add(c.sub(dragoff));
            return;
        }
        super.mousemove(c);
    }

    @Override
    public boolean mouseup(Coord c, int button) {
        if (dragoff != null && button == 1) {
            dragoff = null;
            ui.grabmouse(null);
            Utils.setpref("fayte_abacus_pos", this.c.x + "," + this.c.y);
            return true;
        }
        return super.mouseup(c, button);
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        scroll += amount > 0 ? 1 : -1;
        return true;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        if (main && c.y < head() && c.x > sz.x - FayteSkin.s(60)) {
            return detached ? "Dock it back beside the bag" : "Let it float on its own (drag it by the top)";
        }
        return null;
    }
}
