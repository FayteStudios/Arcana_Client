package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteEntryLayout {
    public static final int PAD = 10;
    public static final Color NOTEFILL = FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.45);
    public static final Color NOTEEDGE = new Color(0xD9, 0xB2, 0x6F);
    public static final Color CHIPEDGE = FayteSkin.BORDER;
    public static final Color HEADFILL = FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.55);
    public static final Color CELLFILL = FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.2);
    public static final Color CELLEDGE = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.PANEL, 0.2);
    public final List<FayteEntryLayout.Item> items = new ArrayList<>();
    public int height = 0;
    private final Map<String, Integer> refs = new HashMap<>();
    private String title;

    private void refer(FayteEntryLayout.Item it, String ref) {
        String k = ref.toLowerCase().replace(' ', '_');
        int n = refs.getOrDefault(k, 0);
        refs.put(k, n + 1);
        it.ref = ref;
        it.refn = n;
    }

    public enum Kind {
        TEXT,
        IMAGE,
        BAR,
        CHIP,
        BOX
    }

    public static class Item {
        public final FayteEntryLayout.Kind kind;
        public final Text t;
        public final BufferedImage img;
        public final Coord c;
        public final Coord sz;
        public final String link;
        public final Color fill;
        public final Color edge;
        public String ref;
        public int refn;

        Item moved(int dx, int dy) {
            FayteEntryLayout.Item n = new FayteEntryLayout.Item(kind, t, img, c.add(dx, dy), sz, link, fill, edge);
            n.ref = ref;
            n.refn = refn;
            return n;
        }

        Item(
                FayteEntryLayout.Kind kind,
                Text t,
                BufferedImage img,
                Coord c,
                Coord sz,
                String link,
                Color fill,
                Color edge) {
            this.kind = kind;
            this.t = t;
            this.img = img;
            this.c = c;
            this.sz = sz;
            this.link = link;
            this.fill = fill;
            this.edge = edge;
        }
    }

    private void text(Text t, Coord c, String link) {
        items.add(new FayteEntryLayout.Item(FayteEntryLayout.Kind.TEXT, t, null, c, t.sz(), link, null, null));
    }

    private void image(BufferedImage img, Coord c, Coord sz) {
        items.add(new FayteEntryLayout.Item(FayteEntryLayout.Kind.IMAGE, null, img, c, sz, null, null, null));
    }

    private void box(FayteEntryLayout.Kind kind, Coord c, Coord sz, Color fill, Color edge) {
        items.add(new FayteEntryLayout.Item(kind, null, null, c, sz, null, fill, edge));
    }

    private int runs(String s, int x, int y, int w, FayteText.Style plain, FayteText.Style linked) {
        FayteText.Block b = FayteText.layout(s, w, plain, linked);

        for (FayteText.Run r : b.runs) {
            Coord c = new Coord(x + r.c.x, y + r.c.y);
            if (r.img != null) {
                image(r.img, c.add(0, 1), r.sz);
                if (r.iconname != null) {
                    refer(items.get(items.size() - 1), "icon:" + r.iconname);
                }
            } else {
                text(r.t, c, r.link);
            }
        }
        return b.sz.y;
    }

    private int picture(FayteMarkup.Block b, int x, int y, int w, boolean showmissing) {
        BufferedImage img = FayteEntries.imagebuf(b.field(0));
        if (img == null) {
            if (showmissing) {
                Text t = FayteText.LABEL.render("[picture not found: " + b.field(0) + "]");
                text(t, new Coord(x, y), null);
                return t.sz().y + 8;
            } else {
                return 0;
            }
        }
        int want = 0;
        try {
            want = Integer.parseInt(b.field(2).trim());
        } catch (NumberFormatException nfe) {
        }
        double f = want > 0
                ? Math.min((double) Math.min(want, w) / img.getWidth(), 600.0 / img.getHeight())
                : Math.min(1.0, Math.min((double) w / img.getWidth(), 260.0 / img.getHeight()));
        Coord sz = new Coord((int) (img.getWidth() * f), (int) (img.getHeight() * f));
        int px = x + (w - sz.x) / 2;
        image(img, new Coord(px, y), sz);
        refer(items.get(items.size() - 1), "pic:" + b.field(0));
        int h = sz.y + 4;
        String cap = b.field(1);
        if (!cap.isEmpty()) {
            FayteText.Block cb = FayteText.layout(cap, w, FayteText.LABEL, FayteText.LABEL);
            int cx = x + (w - cb.sz.x) / 2;
            h += runs(cap, cx, y + h, w, FayteText.LABEL, FayteText.LABEL) + 2;
        }
        return h + 8;
    }

    private int note(FayteMarkup.Block b, int x, int y, int w) {
        int ip = 10;
        int start = items.size();
        int h = ip;
        String title = FayteMarkup.plain(b.field(0)).trim();
        if (!title.isEmpty()) {
            Text t = FayteText.HEAD.render(title);
            text(t, new Coord(x + ip + 4, y + h), null);
            h += t.sz().y + 4;
        }
        h += runs(b.field(1), x + ip + 4, y + h, w - ip * 2 - 4, FayteText.BODY, FayteText.BODYLINK);
        h += ip;
        items.add(
                start,
                new FayteEntryLayout.Item(
                        FayteEntryLayout.Kind.BOX,
                        null,
                        null,
                        new Coord(x, y),
                        new Coord(w, h),
                        null,
                        NOTEFILL,
                        NOTEEDGE));
        items.add(
                start + 1,
                new FayteEntryLayout.Item(
                        FayteEntryLayout.Kind.BOX, null, null, new Coord(x, y), new Coord(4, h), null, NOTEEDGE, null));
        return h + 10;
    }

    private int grid(FayteMarkup.Block b, int x, int y, int w, boolean showmissing) {
        int n = b.fields.size() - 1;
        if (n <= 0) {
            return 0;
        }
        int cols = 0;
        try {
            cols = Integer.parseInt(b.field(0).trim());
        } catch (NumberFormatException e) {
        }
        if (cols <= 0 || cols > n) {
            cols = n;
        }
        int gap = 10;
        int cw = Math.max(20, (w - gap * (cols - 1)) / cols);
        int h = 0;

        for (int r = 0; r * cols < n; r++) {
            int rowh = 0;

            for (int c = 0; c < cols && r * cols + c < n; c++) {
                String cell = FayteWikiText.fragment(FayteMarkup.dec(b.field(1 + r * cols + c)), title);
                int cy = y + h;
                int ch = 0;

                for (String line : cell.split("\n")) {
                    ch += para(line, x + c * (cw + gap), cy + ch, cw, showmissing);
                }
                rowh = Math.max(rowh, ch);
            }
            h += rowh + gap;
        }
        return h;
    }

    private int table(FayteMarkup.Block b, int x, int y, int w, boolean showmissing) {
        FayteTable.Table t = FayteTable.parse(FayteMarkup.dec(b.field(0)));
        int ncol = t.columns();
        if (ncol == 0) {
            return 0;
        }
        int pad = 6;
        int[] cw = new int[ncol];
        List<List<String>> frags = new ArrayList<>();

        for (List<FayteTable.Cell> r : t.rows) {
            List<String> fr = new ArrayList<>();
            int col = 0;

            for (FayteTable.Cell c : r) {
                String f = FayteWikiText.fragment(c.raw, title);
                fr.add(f);
                if (c.span == 1 && col < ncol) {
                    int nat = 0;

                    for (String line : f.split("\n")) {
                        nat = Math.max(
                                nat,
                                FayteText.layout(
                                                line,
                                                10000,
                                                c.head ? FayteText.HEADCELL : FayteText.CELL,
                                                c.head ? FayteText.HEADCELL : FayteText.CELLLINK)
                                        .sz
                                        .x);
                    }
                    cw[col] = Math.max(cw[col], Math.min(nat + 3, w / 2) + pad * 2);
                }
                col += c.span;
            }
            frags.add(fr);
        }
        int total = 0;

        for (int i = 0; i < ncol; i++) {
            cw[i] = Math.max(cw[i], 36);
            total += cw[i];
        }
        if (total > w) {
            for (int i = 0; i < ncol; i++) {
                cw[i] = Math.max(24, cw[i] * w / total);
            }
        }
        int h = 0;
        if (t.caption != null && !t.caption.isEmpty()) {
            h += runs(FayteWikiText.fragment(t.caption, title), x, y, w, FayteText.HEAD, FayteText.HEAD) + 6;
        }
        for (int ri = 0; ri < t.rows.size(); ri++) {
            List<FayteTable.Cell> r = t.rows.get(ri);
            int start = items.size();
            int rowh = FayteText.CELL.line() + pad;
            int col = 0;
            int cx = x;
            List<int[]> boxes = new ArrayList<>();

            for (int ci = 0; ci < r.size() && col < ncol; ci++) {
                FayteTable.Cell c = r.get(ci);
                int width = 0;

                for (int k = col; k < Math.min(ncol, col + c.span); k++) {
                    width += cw[k];
                }
                FayteText.Style st = c.head ? FayteText.HEADCELL : FayteText.CELL;
                FayteText.Style lk = c.head ? FayteText.HEADCELL : FayteText.CELLLINK;
                int ch = 0;
                int before = items.size();

                for (String line : frags.get(ri).get(ci).split("\n")) {
                    List<Object> parts = FayteMarkup.split(line);
                    if (parts.size() == 1 && parts.get(0) instanceof FayteMarkup.Block) {
                        ch += para(line, cx + pad, y + h + pad / 2 + ch, width - pad * 2, showmissing);
                    } else {
                        FayteText.Block tb = FayteText.layout(line, width - pad * 2, st, lk);
                        int off = Math.max(0, (width - pad * 2 - tb.sz.x) / 2);
                        ch += runs(line, cx + pad + off, y + h + pad / 2 + ch, width - pad * 2, st, lk);
                    }
                }
                rowh = Math.max(rowh, ch + pad);
                boxes.add(new int[] {cx, width, before, c.head ? 1 : 0, c.bg == null ? -1 : c.bg.getRGB()});
                cx += width;
                col += c.span;
            }
            for (int bi = boxes.size() - 1; bi >= 0; bi--) {
                int[] bx = boxes.get(bi);
                Color fill = bx[3] == 1
                        ? HEADFILL
                        : (bx[4] == -1 ? CELLFILL : FayteSkin.mix(new Color(bx[4]), FayteSkin.PANEL, 0.55));
                items.add(
                        start,
                        new FayteEntryLayout.Item(
                                FayteEntryLayout.Kind.BOX,
                                null,
                                null,
                                new Coord(bx[0], y + h),
                                new Coord(bx[1] + 1, rowh + 1),
                                null,
                                fill,
                                CELLEDGE));
            }
            for (int bi = 0; bi < boxes.size(); bi++) {
                int[] bx = boxes.get(bi);
                int nextstart = bi + 1 < boxes.size() ? boxes.get(bi + 1)[2] : items.size();
                int celltop = y + h;
                int contenth = 0;

                for (int k = bx[2] + boxes.size(); k < nextstart + boxes.size() && k < items.size(); k++) {
                    FayteEntryLayout.Item it = items.get(k);
                    contenth = Math.max(contenth, it.c.y + it.sz.y - celltop);
                }
                int shift = Math.max(0, (rowh - contenth - 2) / 2) - 2;
                if (shift > 0) {
                    for (int k = bx[2] + boxes.size(); k < nextstart + boxes.size() && k < items.size(); k++) {
                        FayteEntryLayout.Item it = items.get(k);
                        items.set(k, it.moved(0, shift));
                    }
                }
            }
            h += rowh;
        }
        return h + 10;
    }

    private int para(String s, int x, int y, int w, boolean showmissing) {
        int h = 0;

        for (Object o : FayteMarkup.split(s)) {
            if (o instanceof FayteMarkup.Block) {
                FayteMarkup.Block b = (FayteMarkup.Block) o;
                if (b.kind.equals(FayteMarkup.GRID)) {
                    h += grid(b, x, y + h, w, showmissing);
                } else if (b.kind.equals(FayteMarkup.TABLE)) {
                    h += table(b, x, y + h, w, showmissing);
                } else if (b.kind.equals(FayteMarkup.PICTURE)) {
                    h += picture(b, x, y + h, w, showmissing);
                } else if (b.kind.equals(FayteMarkup.NOTE)) {
                    h += note(b, x, y + h, w);
                }
            } else if (!FayteMarkup.plain((String) o).trim().isEmpty() || ((String) o).indexOf(FayteMarkup.IS) >= 0) {
                h += runs(((String) o).trim(), x, y + h, w, FayteText.BODY, FayteText.BODYLINK);
            }
        }
        return h;
    }

    private int heading(String title, int x, int y, int w) {
        Text h = FayteText.HEAD.render(FayteMarkup.plain(title));
        int bh = h.sz().y + 8;
        box(FayteEntryLayout.Kind.BAR, new Coord(x, y), new Coord(w, bh), FayteSkin.BORDER, null);
        text(h, new Coord(x + 8, y + 4), null);
        return bh + 8;
    }

    public static BufferedImage iconof(String icon) {
        if (icon == null) {
            return null;
        }
        return FayteEntries.iconbuf(icon);
    }

    public static FayteEntryLayout build(FayteWikiEntry e, int width, boolean showmissing) {
        FayteEntryLayout l = new FayteEntryLayout();
        l.title = e == null ? null : FayteMarkup.plain(e.name);
        int w = width - PAD * 2;
        int x = PAD;
        int y = PAD;
        if (e == null) {
            l.height = y;
            return l;
        }
        int tx = x;
        int hh = 0;
        List<BufferedImage> hicons = new ArrayList<>();
        List<String> hnames = new ArrayList<>();

        for (String n : e.icons) {
            BufferedImage ic = iconof(n);
            if (ic != null) {
                hicons.add(ic);
                hnames.add(n);
            }
        }
        BufferedImage icon = hicons.isEmpty() ? iconof(e.icon) : null;
        if (!hicons.isEmpty()) {
            int isz = hicons.size() == 1 ? 48 : 32;
            int maxw = w / 2;
            int ix = x;
            int iy = y;

            for (int i = 0; i < hicons.size(); i++) {
                BufferedImage ic = hicons.get(i);
                double f = (double) isz / Math.max(ic.getWidth(), ic.getHeight());
                Coord s = new Coord(Math.max(1, (int) (ic.getWidth() * f)), Math.max(1, (int) (ic.getHeight() * f)));
                if (ix > x && ix + s.x > x + maxw) {
                    ix = x;
                    iy += isz + 4;
                }
                l.image(ic, new Coord(ix, iy), s);
                l.refer(l.items.get(l.items.size() - 1), "hicon:" + hnames.get(i));
                ix += s.x + 4;
                tx = Math.max(tx, ix + 6);
            }
            hh = iy + isz - y;
        } else if (icon != null) {
            double f = Math.min(1.0, 48.0 / Math.max(icon.getWidth(), icon.getHeight()));
            Coord isz = new Coord(Math.max(1, (int) (icon.getWidth() * f)), Math.max(1, (int) (icon.getHeight() * f)));
            l.image(icon, new Coord(x, y), isz);
            tx += isz.x + 10;
            hh = isz.y;
        }
        int ty = y;
        if (e.name != null) {
            Text t = FayteText.NAME.render(FayteMarkup.plain(e.name));
            l.text(t, new Coord(tx, ty), null);
            ty += t.sz().y + 4;
        }
        if (e.category != null) {
            Text t = FayteText.CHIP.render(FayteMarkup.plain(e.category));
            l.box(FayteEntryLayout.Kind.CHIP, new Coord(tx, ty + 3), t.sz().add(10, 6), FayteSkin.PANEL, CHIPEDGE);
            l.text(t, new Coord(tx + 5, ty + 6), null);
            ty += t.sz().y + 12;
        }
        y += Math.max(hh, ty - y) + 10;
        if (e.image != null) {
            y += l.picture(
                    new FayteMarkup.Block(
                            FayteMarkup.PICTURE, Arrays.asList(e.image, "", e.imagesize == null ? "" : e.imagesize)),
                    x,
                    y,
                    w,
                    showmissing);
        }
        if (e.summary != null) {
            y += l.para(e.summary, x, y, w, showmissing) + 14;
        }
        if (e.sections != null) {
            for (FayteWikiEntry.Section s : e.sections) {
                if (s.title != null) {
                    y += l.heading(s.title, x, y, w);
                }
                if (s.lead != null) {
                    y += l.para(s.lead, x + 8, y, w - 8, showmissing);
                }
                if (s.rows != null && !s.rows.isEmpty()) {
                    int lw = 0;

                    for (String[] r : s.rows) {
                        lw = Math.max(lw, FayteText.LABEL.width(r.length > 0 ? FayteMarkup.plain(r[0]) : ""));
                    }
                    lw = Math.min(lw, w / 2);

                    for (String[] r : s.rows) {
                        Text lt = FayteText.LABEL.render(r.length > 0 ? FayteMarkup.plain(r[0]) : "");
                        l.text(lt, new Coord(x + 8, y + 1), null);
                        int vh = l.runs(
                                r.length > 1 ? r[1] : "",
                                x + lw + 20,
                                y,
                                Math.max(60, w - lw - 28),
                                FayteText.BODY,
                                FayteText.BODYLINK);
                        y += Math.max(FayteText.BODY.line(), vh) + 2;
                    }
                }
                if (s.text != null) {
                    y += l.para(s.text, x + 8, y, w - 8, showmissing);
                }
                y += s.title == null ? 8 : 14;
            }
        }
        l.height = y + PAD;
        return l;
    }
}
