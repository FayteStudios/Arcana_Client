package haven;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class FayteSkin {
    public static final Color BORDER = new Color(0x3A, 0x41, 0x4C);
    public static final Color PANEL = new Color(0x15, 0x18, 0x1D);
    public static final Color TEXT = new Color(0xF0, 0xEB, 0xDD);
    public static final Color HOVER = mix(PANEL, BORDER, 0.5);
    public static final Color SHADE = new Color(0, 0, 0, 96);
    public static final int BW = 2;
    public static final int TITLEH = 20;
    public static final double SCALE = FayteTextSize.pct(FayteTextSize.UI, 115) / 100.0;
    public static final Text.Foundry titlef = new Text.Foundry(FayteFont.font(Font.BOLD, s(12)), TEXT).aa(true);
    public static final Text.Foundry labelf = new Text.Foundry(FayteFont.font(Font.PLAIN, s(11)), TEXT).aa(true);

    public static int s(int px) {
        return (int) Math.round(px * SCALE);
    }

    public static final String[] PIECES = {"tl", "tr", "bl", "br", "t", "b", "l", "r"};
    private static final Map<String, FayteSkin.Overlay> overlays = new HashMap<>();
    private static long lastscan = 0L;

    public static boolean on() {
        return FayteModules.STYLE.on();
    }

    public static Color mix(Color a, Color b, double f) {
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * f),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * f),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * f));
    }

    public static void border(GOut g, Coord tl, Coord sz, int w) {
        g.frect(tl, new Coord(sz.x, w));
        g.frect(tl.add(0, sz.y - w), new Coord(sz.x, w));
        g.frect(tl.add(0, w), new Coord(w, sz.y - w * 2));
        g.frect(tl.add(sz.x - w, w), new Coord(w, sz.y - w * 2));
    }

    public static void box(GOut g, Coord tl, Coord sz, Color fill, Color edge) {
        if (fill != null) {
            g.chcolor(fill);
            g.frect(tl, sz);
        }
        if (edge != null) {
            g.chcolor(edge);
            border(g, tl, sz, BW);
        }
        g.chcolor();
    }

    public static void panel(GOut g, Coord tl, Coord sz, String part) {
        box(g, tl, sz, PANEL, BORDER);
        overlay(g, part, tl, sz);
    }

    public static void frame(GOut g, Coord tl, Coord sz, String part) {
        box(g, tl, sz, null, BORDER);
        overlay(g, part, tl, sz);
    }

    public static void titlebar(GOut g, Coord tl, Coord sz, Tex label) {
        box(g, tl, sz, BORDER, null);
        if (label != null) {
            g.image(label, tl.add(8, (sz.y - label.sz().y) / 2));
        }
        overlay(g, "title", tl, sz);
    }

    public static void button(GOut g, Coord sz, boolean hover, boolean down) {
        box(g, Coord.z, sz, down ? BORDER : (hover ? HOVER : PANEL), BORDER);
        overlay(g, "button", Coord.z, sz);
    }

    public static void overlay(GOut g, String part, Coord tl, Coord sz) {
        FayteSkin.Overlay o = get(part);
        if (o != null) {
            o.draw(g, tl, sz);
        }
    }

    public static synchronized FayteSkin.Overlay get(String part) {
        long now = System.currentTimeMillis();
        if (now - lastscan > 3000L) {
            lastscan = now;

            for (FayteSkin.Overlay o : overlays.values()) {
                o.check();
            }
        }
        FayteSkin.Overlay o = overlays.get(part);
        if (o == null) {
            o = new FayteSkin.Overlay(part);
            overlays.put(part, o);
        }
        return o.empty ? null : o;
    }

    public static File dir() {
        return new File(Config.userhome, "overlays");
    }

    public static class Overlay {
        public final String part;
        private final Tex[] tex = new Tex[PIECES.length];
        private final long[] mtime = new long[PIECES.length];
        boolean empty = true;

        Overlay(String part) {
            this.part = part;
            check();
        }

        void check() {
            boolean any = false;

            for (int i = 0; i < PIECES.length; i++) {
                File f = new File(dir(), part + "_" + PIECES[i] + ".png");
                long m = f.exists() ? f.lastModified() : 0L;
                if (m != mtime[i]) {
                    mtime[i] = m;
                    if (tex[i] != null) {
                        tex[i].dispose();
                        tex[i] = null;
                    }
                    if (m != 0L) {
                        try {
                            BufferedImage img = ImageIO.read(f);
                            if (img != null) {
                                tex[i] = new TexI(img);
                            }
                        } catch (Exception e) {
                            System.out.println("Could not read overlay " + f + ": " + e);
                        }
                    }
                }
                any |= tex[i] != null;
            }
            empty = !any;
        }

        private void tile(GOut g, Tex t, Coord ul, Coord sz, boolean horiz) {
            if (t != null && sz.x > 0 && sz.y > 0) {
                Coord ts = t.sz();
                if (horiz) {
                    for (int x = 0; x < sz.x; x += ts.x) {
                        g.image(t, ul.add(x, 0), ul, sz);
                    }
                } else {
                    for (int y = 0; y < sz.y; y += ts.y) {
                        g.image(t, ul.add(0, y), ul, sz);
                    }
                }
            }
        }

        void draw(GOut g, Coord tl, Coord sz) {
            Tex ctl = tex[0];
            Tex ctr = tex[1];
            Tex cbl = tex[2];
            Tex cbr = tex[3];
            Coord ltl = ctl == null ? Coord.z : ctl.sz();
            Coord ltr = ctr == null ? Coord.z : ctr.sz();
            Coord lbl = cbl == null ? Coord.z : cbl.sz();
            Coord lbr = cbr == null ? Coord.z : cbr.sz();
            Tex t = tex[4];
            Tex b = tex[5];
            Tex l = tex[6];
            Tex r = tex[7];
            if (t != null) {
                tile(g, t, tl.add(ltl.x, 0), new Coord(sz.x - ltl.x - ltr.x, t.sz().y), true);
            }
            if (b != null) {
                tile(g, b, tl.add(lbl.x, sz.y - b.sz().y), new Coord(sz.x - lbl.x - lbr.x, b.sz().y), true);
            }
            if (l != null) {
                tile(g, l, tl.add(0, ltl.y), new Coord(l.sz().x, sz.y - ltl.y - lbl.y), false);
            }
            if (r != null) {
                tile(g, r, tl.add(sz.x - r.sz().x, ltr.y), new Coord(r.sz().x, sz.y - ltr.y - lbr.y), false);
            }
            if (ctl != null) {
                g.image(ctl, tl);
            }
            if (ctr != null) {
                g.image(ctr, tl.add(sz.x - ltr.x, 0));
            }
            if (cbl != null) {
                g.image(cbl, tl.add(0, sz.y - lbl.y));
            }
            if (cbr != null) {
                g.image(cbr, tl.add(sz.x - lbr.x, sz.y - lbr.y));
            }
        }
    }
}
