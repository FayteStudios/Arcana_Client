package haven;

import java.awt.Color;

public class FayteTitleButton extends Widget {
    public static final int H = FayteSkin.s(20);
    public static final int BIGW = FayteSkin.s(64);
    public static final int BIGH = FayteSkin.s(48);
    private Text label;
    private String text;
    private final Runnable action;
    private boolean hov = false;
    private boolean down = false;
    public boolean sel = false;
    public Runnable rclick = null;
    private String icon = null;
    private Tex itex = null;
    private boolean ifailed = false;
    private int mode = 0;
    private boolean iconly = false;
    public Color swatch = null;
    public Color selcol = null;

    public FayteTitleButton(Widget parent, String text, Object tooltip, Runnable action) {
        super(Coord.z, new Coord(1, H), parent);
        this.action = action;
        this.tooltip = tooltip;
        setlabel(text);
    }

    public static int iconmode() {
        return (int) Math.round(FayteConfig.almanacIcons.get());
    }

    public static int rowh() {
        return iconmode() == 2 ? BIGH : H;
    }

    public FayteTitleButton icon(String res, int mode) {
        icon = res;
        itex = null;
        ifailed = false;
        this.mode = res == null ? 0 : mode;
        setlabel(text);
        return this;
    }

    public FayteTitleButton iconly(String res) {
        iconly = true;
        icon = res;
        itex = null;
        ifailed = false;
        setlabel(text);
        return this;
    }

    public void setlabel(String text) {
        this.text = text;
        label = FayteSkin.labelf.render(text);
        if (iconly) {
            sz = new Coord(icon == null && swatch == null ? label.sz().x + 16 : H + 4, H);
        } else if (mode == 2) {
            sz = new Coord(Math.max(BIGW, label.sz().x + 16), BIGH);
        } else if (mode == 1) {
            sz = new Coord(label.sz().x + H + 14, H);
        } else {
            sz = new Coord(label.sz().x + 16, H);
        }
    }

    private Tex itex() {
        if (itex == null && icon != null && !ifailed) {
            try {
                if (icon.startsWith("gfx/hud/")) {
                    itex = new TexI(Resource.loadimg(icon));
                } else {
                    Resource.Image img = Resource.load(icon).layer(Resource.imgc);
                    if (img != null) {
                        itex = img.tex();
                    } else {
                        ifailed = true;
                    }
                }
            } catch (Loading e) {
            } catch (RuntimeException e) {
                ifailed = true;
            }
        }
        return itex;
    }

    private void drawicon(GOut g, Coord c, int s) {
        Tex t = itex();
        if (t != null) {
            g.image(t, c, new Coord(s, s));
        }
    }

    @Override
    public void draw(GOut g) {
        Color bg = down || sel
                ? (sel && selcol != null ? selcol : FayteSkin.BORDER)
                : (hov ? FayteSkin.HOVER : FayteSkin.PANEL);
        FayteSkin.box(g, Coord.z, sz, bg, sel ? FayteSkin.TEXT : FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.25));
        int d = down ? 1 : 0;
        if (iconly) {
            if (swatch != null) {
                g.chcolor(swatch);
                g.frect(new Coord(5 + d, 4 + d), sz.sub(10, 8));
                g.chcolor();
            } else if (icon != null && itex() != null) {
                drawicon(g, new Coord((sz.x - (H - 4)) / 2 + d, 2 + d), H - 4);
            } else {
                g.aimage(label.tex(), sz.div(2).add(d, d), 0.5, 0.5);
            }
        } else if (mode == 2) {
            int s = BIGH - label.sz().y - 8;
            drawicon(g, new Coord((sz.x - s) / 2 + d, 3 + d), s);
            g.aimage(label.tex(), new Coord(sz.x / 2 + d, sz.y - 3 + d), 0.5, 1.0);
        } else if (mode == 1) {
            drawicon(g, new Coord(4 + d, 2 + d), H - 4);
            g.aimage(label.tex(), new Coord(H + 4 + d, sz.y / 2 + d), 0.0, 0.5);
        } else {
            g.aimage(label.tex(), sz.div(2).add(d, d), 0.5, 0.5);
        }
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        if (iconly) {
            return tooltip instanceof String ? text + ": " + tooltip : text;
        }
        return super.tooltip(c, prev);
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (button == 3 && rclick != null) {
            rclick.run();
            return true;
        } else if (button != 1) {
            return false;
        } else {
            down = true;
            ui.grabmouse(this);
            return true;
        }
    }

    @Override
    public boolean mouseup(Coord c, int button) {
        if (down && button == 1) {
            down = false;
            ui.grabmouse(null);
            if (c.isect(Coord.z, sz)) {
                action.run();
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void mousemove(Coord c) {
        hov = c.isect(Coord.z, sz);
    }
}
