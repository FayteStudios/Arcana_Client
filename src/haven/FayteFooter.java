package haven;

import java.awt.Color;

public class FayteFooter extends Widget {
    private final Window win;
    private String last = null;
    private Tex tex = null;
    private long lastcalc = 0L;

    public FayteFooter(Window win) {
        super(Coord.z, new Coord(10, FayteSkin.labelf.height() + 4), win);
        this.win = win;
        place();
    }

    private Inventory inv() {
        for (Widget c = win.child; c != null; c = c.next) {
            if (c instanceof Inventory) {
                return (Inventory) c;
            }
        }
        return null;
    }

    private void place() {
        Inventory inv = this.inv();
        if (inv == null) {
            return;
        }
        Coord nc = new Coord(inv.c.x, inv.c.y + inv.sz.y + 2);
        Coord ns = new Coord(inv.sz.x, FayteSkin.labelf.height() + 4);
        if (!nc.equals(c) || !ns.equals(sz)) {
            c = nc;
            sz = ns;
            win.pack();
            win.placetwdgs();
        }
    }

    private String line() {
        Inventory inv = this.inv();
        if (inv == null) {
            return "";
        }
        int n = 0;
        double sum = 0;
        for (Widget c = inv.child; c != null; c = c.next) {
            if (c instanceof WItem) {
                String nm = FayteAlmanac.itemname(((WItem) c).item);
                String p = FayteXfer.purity((WItem) c);
                if (nm != null
                        && p != null
                        && (nm.toLowerCase().contains("worm")
                                || nm.toLowerCase().contains("python"))) {
                    try {
                        sum += Double.parseDouble(p);
                        n++;
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
        return n == 0
                ? "No worms"
                : n + (n == 1 ? " worm" : " worms") + "  \u00b7  avg purity " + String.format("%.2f", sum / n) + "%";
    }

    @Override
    public void draw(GOut g) {
        long now = System.currentTimeMillis();
        if (now - lastcalc > 1000L) {
            lastcalc = now;
            place();
            String l = line();
            if (!l.equals(last)) {
                last = l;
                tex = new TexI(Utils.outline2(
                        FayteSkin.labelf.render(l, FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.85)).img,
                        Color.BLACK));
            }
        }
        if (tex != null) {
            g.aimage(tex, new Coord(sz.x, 2), 1.0, 0.0);
        }
    }
}
