package haven;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteAchievePanel extends Widget {
    private static final Color GOLD = new Color(0xC8, 0xAA, 0x62);
    private static final Color FILL = FayteSkin.mix(FayteSkin.PANEL, GOLD, 0.55);
    private static final String[] STATUS = {"Open", "Done", "Everything"};
    private int status = 0;
    private static final Color BAR = new Color(0x6A, 0x9A, 0x5A);
    private final Map<String, Text> texts = new HashMap<>();
    private final Map<String, Tex> icons = new HashMap<>();
    private final Map<String, Tex> tips = new HashMap<>();
    private int cat = 0;
    private int scroll = 0;
    private List<FayteAchieve.Line> shown = new ArrayList<>();

    public FayteAchievePanel(Coord c, Coord sz, Widget parent) {
        super(c, sz, parent);
    }

    private Text text(String s, Color c, boolean bold) {
        String k = c.getRGB() + (bold ? "|b|" : "|") + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 400) {
                texts.clear();
            }
            t = (bold ? FayteSkin.titlef : FayteSkin.labelf).render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private Tex icon(String name) {
        if (name == null) {
            return null;
        }
        if (icons.containsKey(name)) {
            return icons.get(name);
        }
        try {
            Tex t = Resource.load(name).layer(Resource.imgc).tex();
            icons.put(name, t);
            return t;
        } catch (Loading e) {
            return null;
        } catch (RuntimeException e) {
            icons.put(name, null);
            return null;
        }
    }

    private static int hdr() {
        return FayteSkin.s(58);
    }

    private static int row2() {
        return FayteSkin.s(28);
    }

    private static Coord pinc(Coord sz) {
        return new Coord(sz.x - FayteSkin.s(60), FayteSkin.s(64));
    }

    private static Coord pinsz() {
        return new Coord(FayteSkin.s(52), FayteSkin.s(16));
    }

    private static int gap() {
        return FayteSkin.s(8);
    }

    private static int cardh() {
        return FayteSkin.s(86);
    }

    private int cols() {
        return Math.max(1, (sz.x + gap()) / (FayteSkin.s(270) + gap()));
    }

    private int cardw() {
        int n = cols();
        return (sz.x - (n - 1) * gap()) / n;
    }

    private int chipw() {
        return FayteSkin.s(96);
    }

    private List<FayteAchieve.Line> filtered() {
        List<FayteAchieve.Line> l = new ArrayList<>();
        for (FayteAchieve.Line a : FayteAchieve.lines()) {
            boolean done = a.next() < 0;
            if ((cat == 0 || FayteAchieve.CATS[cat].equals(a.cat)) && (status == 2 || (status == 1) == done)) {
                l.add(a);
            }
        }
        return l;
    }

    private int visrows() {
        return Math.max(1, (sz.y - hdr()) / (cardh() + gap()));
    }

    @Override
    public void draw(GOut g) {
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.65);
        for (int i = 0; i < FayteAchieve.CATS.length; i++) {
            boolean sel = i == cat;
            Coord cc = new Coord(i * (chipw() + 4), 0);
            Coord cs = new Coord(chipw(), FayteSkin.s(22));
            if (cc.x + cs.x > sz.x) {
                break;
            }
            FayteSkin.box(
                    g,
                    cc,
                    cs,
                    sel ? FayteSkin.mix(FayteSkin.PANEL, GOLD, 0.2) : FayteSkin.PANEL,
                    sel ? GOLD : FayteSkin.BORDER);
            g.aimage(
                    text(FayteAchieve.CATS[i], sel ? GOLD : FayteSkin.TEXT, false)
                            .tex(),
                    cc.add(cs.div(2)),
                    0.5,
                    0.5);
        }
        for (int i = 0; i < STATUS.length; i++) {
            boolean sel = i == status;
            Coord cc = new Coord(i * (chipw() + 4), row2());
            Coord cs = new Coord(chipw(), FayteSkin.s(22));
            FayteSkin.box(
                    g,
                    cc,
                    cs,
                    sel ? FayteSkin.mix(FayteSkin.PANEL, GOLD, 0.2) : FayteSkin.PANEL,
                    sel ? GOLD : FayteSkin.BORDER);
            g.aimage(text(STATUS[i], sel ? GOLD : FayteSkin.TEXT, false).tex(), cc.add(cs.div(2)), 0.5, 0.5);
        }
        g.aimage(
                text("Earned " + FayteAchieve.earned() + " of " + FayteAchieve.total(), dim, false)
                        .tex(),
                new Coord(sz.x, row2() + FayteSkin.s(11)),
                1.0,
                0.5);

        if (shown.isEmpty()) {
            g.image(
                    text(status == 1 ? "Nothing finished here yet." : "Everything here is done!", dim, false)
                            .tex(),
                    new Coord(0, hdr()));
        }
        shown = filtered();
        int cols = this.cols();
        int rows = (shown.size() + cols - 1) / cols;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows - visrows())));
        int cw = cardw();
        for (int i = scroll * cols; i < shown.size(); i++) {
            int r = i / cols - scroll;
            if (r >= visrows()) {
                break;
            }
            Coord o = new Coord((i % cols) * (cw + gap()), hdr() + r * (cardh() + gap()));
            card(g.reclip(o, new Coord(cw, cardh())), shown.get(i), new Coord(cw, cardh()), dim);
        }
        if (rows > visrows()) {
            g.aimage(
                    text("Scroll for more (" + (scroll + 1) + "/" + (rows - visrows() + 1) + ")", dim, false)
                            .tex(),
                    new Coord(sz.x, sz.y),
                    1.0,
                    1.0);
        }
    }

    private void card(GOut g, FayteAchieve.Line l, Coord sz, Color dim) {
        int earned = l.earned();
        int next = l.next();
        boolean done = next < 0;
        FayteSkin.box(
                g,
                Coord.z,
                sz,
                done ? FayteSkin.mix(FayteSkin.PANEL, GOLD, 0.07) : FayteSkin.PANEL,
                done ? FayteSkin.mix(FayteSkin.BORDER, GOLD, 0.6) : FayteSkin.BORDER);
        int is = FayteSkin.s(34);
        Coord ic = new Coord(FayteSkin.s(8), FayteSkin.s(8));
        Tex it = icon(l.icon);
        if (it != null) {
            g.image(it, ic, new Coord(is, is));
        } else {
            FayteSkin.box(
                    g,
                    ic,
                    new Coord(is, is),
                    FayteSkin.mix(FayteSkin.PANEL, earned > 0 ? GOLD : FayteSkin.BORDER, 0.25),
                    earned > 0 ? FILL : FayteSkin.BORDER);
            g.aimage(
                    text(l.name.substring(0, 1), earned > 0 ? GOLD : FayteSkin.TEXT, true)
                            .tex(),
                    ic.add(is / 2, is / 2),
                    0.5,
                    0.5);
        }
        int x = FayteSkin.s(50);
        int w = sz.x - x - FayteSkin.s(8);
        g.image(text(l.name, done ? GOLD : FayteSkin.TEXT, true).tex(), new Coord(x, FayteSkin.s(6)));
        g.aimage(
                text(earned + " / " + l.tiers.length, earned > 0 ? GOLD : dim, false)
                        .tex(),
                new Coord(sz.x - FayteSkin.s(8), FayteSkin.s(8)),
                1.0,
                0.0);
        g.image(text(done ? "Every tier earned" : l.goal(next), dim, false).tex(), new Coord(x, FayteSkin.s(26)));
        int goal = done ? l.tiers[l.tiers.length - 1] : l.tiers[next];
        int v = Math.min(l.value, goal);
        Coord bc = new Coord(x, FayteSkin.s(46));
        Coord bs = new Coord(w, FayteSkin.s(14));
        FayteSkin.box(g, bc, bs, FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.4), FayteSkin.BORDER);
        int fw = goal <= 0 ? 0 : (int) ((long) (bs.x - 2) * v / goal);
        if (fw > 0) {
            g.chcolor(done ? FILL : BAR);
            g.frect(bc.add(1, 1), new Coord(fw, bs.y - 2));
            g.chcolor();
        }
        g.aimage(text(v + " / " + goal, FayteSkin.TEXT, false).tex(), bc.add(bs.div(2)), 0.5, 0.5);
        int ps = FayteSkin.s(8);
        for (int i = 0; i < l.tiers.length; i++) {
            Coord pc = new Coord(x + i * (ps + 3), FayteSkin.s(68));
            if (pc.x + ps > sz.x - FayteSkin.s(66)) {
                break;
            }
            boolean got = i < earned;
            FayteSkin.box(g, pc, new Coord(ps, ps), got ? FILL : FayteSkin.PANEL, got ? FILL : FayteSkin.BORDER);
        }
        GameUI gui = getparent(GameUI.class);
        boolean pin = gui != null && FayteAchievePins.pinned(gui, l.id);
        Coord pc = pinc(sz);
        FayteSkin.box(
                g,
                pc,
                pinsz(),
                pin ? FayteSkin.mix(FayteSkin.PANEL, GOLD, 0.2) : FayteSkin.PANEL,
                pin ? GOLD : FayteSkin.BORDER);
        g.aimage(text(pin ? "Pinned" : "Pin", pin ? GOLD : dim, false).tex(), pc.add(pinsz().div(2)), 0.5, 0.5);
    }

    private FayteAchieve.Line at(Coord c) {
        if (c.y < hdr()) {
            return null;
        }
        int cols = this.cols();
        int cw = cardw();
        int col = c.x / (cw + gap());
        int row = (c.y - hdr()) / (cardh() + gap());
        if (col >= cols || c.x % (cw + gap()) >= cw || (c.y - hdr()) % (cardh() + gap()) >= cardh()) {
            return null;
        }
        int i = (row + scroll) * cols + col;
        return i >= 0 && i < shown.size() ? shown.get(i) : null;
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (button == 1 && c.y < FayteSkin.s(22)) {
            int i = c.x / (chipw() + 4);
            if (i >= 0 && i < FayteAchieve.CATS.length && c.x % (chipw() + 4) < chipw()) {
                cat = i;
                scroll = 0;
            }
            return true;
        }
        if (button == 1 && c.y >= row2() && c.y < row2() + FayteSkin.s(22)) {
            int i = c.x / (chipw() + 4);
            if (i >= 0 && i < STATUS.length && c.x % (chipw() + 4) < chipw()) {
                status = i;
                scroll = 0;
            }
            return true;
        }
        FayteAchieve.Line hit = at(c);
        if (button == 1 && hit != null) {
            int cw = cardw();
            Coord local = new Coord(c.x % (cw + gap()), (c.y - hdr()) % (cardh() + gap()));
            if (local.isect(pinc(new Coord(cw, cardh())), pinsz())) {
                GameUI gui = getparent(GameUI.class);
                if (gui != null) {
                    FayteAchievePins.toggle(gui, hit.id);
                }
            }
            return true;
        }
        return super.mousedown(c, button);
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        scroll += amount > 0 ? 1 : -1;
        return true;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        FayteAchieve.Line l = at(c);
        if (l == null) {
            return super.tooltip(c, prev);
        }
        int earned = l.earned();
        String k = l.id + "|" + earned;
        Tex t = tips.get(k);
        if (t == null) {
            if (tips.size() > 100) {
                tips.clear();
            }
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
            StringBuilder sb = new StringBuilder();
            sb.append("$b{")
                    .append(RichText.Parser.quote(l.name))
                    .append("}  (")
                    .append(l.cat)
                    .append(")\n");
            for (int i = 0; i < l.tiers.length; i++) {
                Long when = FayteAlmanac.achieved(l.tierid(i));
                sb.append(when != null ? "$col[200,170,98]{\u2713 " : "\u25cb ")
                        .append(RichText.Parser.quote(l.goal(i)));
                if (when != null) {
                    sb.append("  ").append(df.format(new Date(when))).append("}");
                }
                sb.append("\n");
            }
            t = RichText.render(sb.toString(), FayteSkin.s(300)).tex();
            tips.put(k, t);
        }
        return t;
    }
}
