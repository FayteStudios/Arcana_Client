package haven;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteText {
    public static final char LS = FayteMarkup.LS;
    public static final char LM = FayteMarkup.LM;
    public static final char LE = FayteMarkup.LE;
    public static final Color LINK = new Color(0x8F, 0xC1, 0xE3);
    public static final Color DIM = FayteSkin.mix(FayteSkin.TEXT, FayteSkin.BORDER, 0.45);
    public static final String FAMILY = FayteFont.family();
    private static final double SCALE = FayteTextSize.pct(FayteTextSize.ALMANAC) / 100.0;
    public static final FayteText.Style BODY = new FayteText.Style(FayteFont.font(Font.PLAIN, sz(14)), FayteSkin.TEXT);
    public static final FayteText.Style BODYLINK = new FayteText.Style(FayteFont.font(Font.PLAIN, sz(14)), LINK);
    public static final FayteText.Style LABEL = new FayteText.Style(FayteFont.font(Font.PLAIN, sz(13)), DIM);
    public static final FayteText.Style LIST = new FayteText.Style(FayteFont.font(Font.PLAIN, sz(13)), FayteSkin.TEXT);
    public static final FayteText.Style HEAD = new FayteText.Style(FayteFont.font(Font.BOLD, sz(14)), FayteSkin.TEXT);
    public static final FayteText.Style NAME = new FayteText.Style(FayteFont.font(Font.BOLD, sz(19)), FayteSkin.TEXT);
    public static final Color GOLD = new Color(0xE3, 0xA8, 0x4A);
    public static final FayteText.Style HEADCELL = new FayteText.Style(FayteFont.font(Font.BOLD, sz(13)), GOLD);
    public static final FayteText.Style CELL = new FayteText.Style(FayteFont.font(Font.PLAIN, sz(13)), FayteSkin.TEXT);
    public static final FayteText.Style CELLLINK = new FayteText.Style(FayteFont.font(Font.PLAIN, sz(13)), LINK);
    public static final FayteText.Style CHIP = new FayteText.Style(FayteFont.font(Font.PLAIN, sz(12)), FayteSkin.TEXT);
    public static final double SPACING = 1.35;

    private static int sz(int n) {
        return (int) Math.round(n * SCALE);
    }

    public static class Style {
        public final Text.Foundry fnd;
        public final FontMetrics m;
        public final Font font;
        public final Color col;
        private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

        Style(Font f, Color c) {
            fnd = new Text.Foundry(f, c).aa(true);
            font = f;
            col = c;
            Graphics g = TexI.mkbuf(new Coord(4, 4)).getGraphics();
            g.setFont(f);
            m = g.getFontMetrics();
            g.dispose();
        }

        private Font fallback = null;

        private Font fontfor(String s) {
            if (s == null || font.canDisplayUpTo(s) < 0) {
                return font;
            }
            if (fallback == null) {
                Map<TextAttribute, Object> attrs = new HashMap<TextAttribute, Object>(font.getAttributes());
                attrs.put(TextAttribute.FAMILY, "Dialog");
                fallback = new Font(attrs);
            }
            return fallback;
        }

        public int width(String s) {
            return (int) Math.ceil(fontfor(s).getStringBounds(s, FRC).getWidth());
        }

        public int height() {
            return m.getAscent() + m.getDescent();
        }

        public int line() {
            return (int) Math.round(height() * SPACING);
        }

        private void paint(Graphics2D g, String s) {
            Utils.AA(g);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(fontfor(s));
            g.setColor(col);
            g.drawString(s, 0.0F, (float) m.getAscent());
        }

        public Text render(String s) {
            final Coord lsz = new Coord(Math.max(1, width(s) + 2), height());
            BufferedImage img = TexI.mkbuf(lsz);
            Graphics2D g = img.createGraphics();
            paint(g, s);
            g.dispose();
            Text.Line line = new Text.Line(s, img, m);
            line.hires = sc -> Text.paintscaled(lsz, sc, g2 -> paint(g2, s));
            return line;
        }
    }

    public static class Run {
        public final Text t;
        public final BufferedImage img;
        public final Coord c;
        public final Coord sz;
        public final String link;
        public String iconname;

        Run(Text t, Coord c, String link) {
            this.t = t;
            img = null;
            this.c = c;
            sz = t.sz();
            this.link = link;
        }

        Run(BufferedImage img, Coord c, Coord sz) {
            t = null;
            this.img = img;
            this.c = c;
            this.sz = sz;
            link = null;
        }
    }

    public static class Block {
        public final List<FayteText.Run> runs = new ArrayList<>();
        public Coord sz = Coord.z;
    }

    private static class Word {
        final String s;
        final String link;
        final boolean spacebefore;
        final BufferedImage icon;
        int size = 0;
        String iconname;

        Word(String s, String link, boolean spacebefore) {
            this.s = s;
            this.link = link;
            this.spacebefore = spacebefore;
            icon = null;
        }

        Word(BufferedImage icon, boolean spacebefore) {
            s = "";
            link = null;
            this.spacebefore = spacebefore;
            this.icon = icon;
        }
    }

    public static String plain(String s) {
        return FayteMarkup.plain(s);
    }

    private static List<FayteText.Word> words(String s) {
        List<FayteText.Word> ret = new ArrayList<>();
        int i = 0;
        boolean space = false;

        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == LS) {
                int m = s.indexOf(LM, i);
                int e = s.indexOf(LE, i);
                if (m > i && e > m) {
                    String target = s.substring(i + 1, m);
                    boolean live = target.matches("^[a-z-]+:.*") || FayteWikiData.find(target) != null;
                    boolean first = true;

                    for (String w : s.substring(m + 1, e).split(" ")) {
                        if (!w.isEmpty()) {
                            ret.add(new FayteText.Word(w, live ? target : null, !first || space));
                            first = false;
                        }
                    }
                    space = false;
                    i = e + 1;
                    continue;
                }
                i++;
            } else if (c == FayteMarkup.IS) {
                int e = s.indexOf(FayteMarkup.IE, i);
                if (e > i) {
                    String[] ip = s.substring(i + 1, e).split(String.valueOf(FayteMarkup.BF));
                    BufferedImage ic = FayteEntries.iconbuf(ip[0]);
                    if (ic != null) {
                        FayteText.Word iw = new FayteText.Word(ic, space || ret.isEmpty());
                        iw.iconname = ip[0];
                        if (ip.length > 1) {
                            try {
                                iw.size = Math.max(8, Math.min(128, Integer.parseInt(ip[1].trim())));
                            } catch (NumberFormatException nfe) {
                            }
                        }
                        ret.add(iw);
                        space = true;
                    }
                    i = e + 1;
                } else {
                    i++;
                }
            } else if (c == FayteMarkup.BS) {
                int e = s.indexOf(FayteMarkup.BE, i);
                i = e > i ? e + 1 : i + 1;
            } else if (c == LM || c == LE || c == FayteMarkup.IE || c == FayteMarkup.BE || c == FayteMarkup.BF) {
                i++;
            } else if (Character.isWhitespace(c)) {
                space = true;
                i++;
            } else {
                int j = i;
                while (j < s.length()
                        && !Character.isWhitespace(s.charAt(j))
                        && s.charAt(j) >= ' '
                        && !FayteMarkup.ismark(s.charAt(j))) {
                    j++;
                }
                if (j == i) {
                    i++;
                    continue;
                }
                ret.add(new FayteText.Word(s.substring(i, j), null, space || ret.isEmpty()));
                space = false;
                i = j;
            }
        }
        return ret;
    }

    public static FayteText.Block layout(String s, int width, FayteText.Style plain, FayteText.Style linked) {
        FayteText.Block b = new FayteText.Block();
        if (s == null || s.isEmpty()) {
            return b;
        }
        int lh = plain.line();
        int sp = plain.width(" ");
        int x = 0;
        int y = 0;
        int extra = 0;
        int linestart = 0;
        int maxw = 0;
        StringBuilder run = new StringBuilder();
        String runlink = null;
        int runx = 0;
        boolean runopen = false;

        for (FayteText.Word w : words(s)) {
            if (w.icon != null) {
                int ih = w.size > 0 ? w.size : plain.height();
                int iw = Math.max(1, w.icon.getWidth() * ih / Math.max(1, w.icon.getHeight()));
                int igap = x > 0 && w.spacebefore ? sp : 0;
                if (runopen) {
                    flush(b, run, runlink, runx, y, plain, linked);
                    runopen = false;
                }
                if (x > 0 && x + igap + iw > width) {
                    maxw = Math.max(maxw, x);
                    x = 0;
                    center(b, linestart, extra);
                    linestart = b.runs.size();
                    y += lh + extra;
                    extra = 0;
                    igap = 0;
                }
                FayteText.Run ir = new FayteText.Run(
                        w.icon, new Coord(x + igap, y + Math.max(0, (plain.height() - ih) / 2)), new Coord(iw, ih));
                ir.iconname = w.iconname;
                b.runs.add(ir);
                extra = Math.max(extra, ih - plain.height());
                x += igap + iw;
                continue;
            }
            FayteText.Style st = w.link != null ? linked : plain;
            int ww = st.width(w.s);
            int gap = x > 0 && w.spacebefore ? sp : 0;
            if (x > 0 && x + gap + ww > width) {
                if (runopen) {
                    flush(b, run, runlink, runx, y, plain, linked);
                    runopen = false;
                }
                maxw = Math.max(maxw, x);
                x = 0;
                center(b, linestart, extra);
                linestart = b.runs.size();
                y += lh + extra;
                extra = 0;
                gap = 0;
            }
            boolean samerun =
                    runopen && ((runlink == null && w.link == null) || (runlink != null && runlink.equals(w.link)));
            if (!samerun) {
                if (runopen) {
                    flush(b, run, runlink, runx, y, plain, linked);
                }
                run.setLength(0);
                runlink = w.link;
                runx = x + gap;
                runopen = true;
            } else if (gap > 0) {
                run.append(' ');
            }
            run.append(w.s);
            x = runx + st.width(run.toString());
        }
        if (runopen) {
            flush(b, run, runlink, runx, y, plain, linked);
        }
        center(b, linestart, extra);
        maxw = Math.max(maxw, x);
        b.sz = new Coord(maxw, y + plain.height() + extra);
        return b;
    }

    private static void center(FayteText.Block b, int from, int extra) {
        if (extra > 1) {
            for (int i = from; i < b.runs.size(); i++) {
                FayteText.Run r = b.runs.get(i);
                if (r.t != null) {
                    b.runs.set(i, new FayteText.Run(r.t, r.c.add(0, extra / 2), r.link));
                }
            }
        }
    }

    private static void flush(
            FayteText.Block b,
            StringBuilder run,
            String link,
            int x,
            int y,
            FayteText.Style plain,
            FayteText.Style linked) {
        if (run.length() > 0) {
            FayteText.Style st = link != null ? linked : plain;
            b.runs.add(new FayteText.Run(st.render(run.toString()), new Coord(x, y), link));
        }
    }
}
