package haven;

import java.awt.Color;
import java.util.List;

public class FayteFoodSlots extends Widget {
    public static final int GAP = 3;
    public static final Coord SLOT = new Coord(FayteSkin.s(48), FayteSkin.s(48));
    private static Tex feasttex = null;
    private final Text crl;
    private final Text fel;
    private Indir<Resource> crres = null;
    private Tex crtex = null;
    private Tex crtip = null;

    public FayteFoodSlots(Coord c, Widget parent) {
        super(c, new Coord(SLOT.x * 2 + GAP, SLOT.y), parent);
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.35);
        crl = FayteSkin.labelf.render("Crave", dim);
        fel = FayteSkin.labelf.render("Feast", dim);
    }

    private Tempers tm() {
        GameUI gui = getparent(GameUI.class);
        return gui == null ? null : gui.tm;
    }

    private static Tex feast() {
        if (feasttex == null) {
            feasttex = new TexI(Tempers.gbtni[0]);
        }
        return feasttex;
    }

    public void update(Tempers tm) {
        boolean on = FayteSkin.on();
        if (on != visible) {
            show(on);
        }
        if (on && tm != null) {
            if (tm.crimg != null && tm.crimg.visible) {
                tm.crimg.hide();
            }
            if (tm.gbtn != null && tm.gbtn.visible) {
                tm.gbtn.hide();
            }
        } else if (tm != null) {
            if (tm.crimg != null && !tm.crimg.visible) {
                tm.crimg.show();
            }
            if (tm.gbtn != null && !tm.gbtn.visible && tm.visible) {
                tm.gbtn.show();
            }
        }
        Indir<Resource> r = tm == null ? null : tm.cravail;
        if (r != crres) {
            crres = r;
            crtex = null;
            crtip = null;
        }
    }

    private boolean feastable(Tempers tm) {
        return tm != null && tm.gbtn != null && tm.visible;
    }

    private static void fit(GOut g, Tex t, Coord o) {
        Coord ts = t.sz();
        int m = SLOT.x - 6;
        double f = Math.min(1.0, (double) m / Math.max(ts.x, ts.y));
        Coord s = new Coord(Math.max(1, (int) (ts.x * f)), Math.max(1, (int) (ts.y * f)));
        g.image(t, o.add(SLOT.sub(s).div(2)), s);
    }

    @Override
    public void draw(GOut g) {
        Tempers tm = this.tm();
        for (int i = 0; i < 2; i++) {
            Coord o = new Coord(i * (SLOT.x + GAP), 0);
            boolean ready = i == 0 ? crres != null : feastable(tm);
            Color gold = new Color(0xF0, 0xC8, 0x40);
            FayteSkin.box(
                    g,
                    o,
                    SLOT,
                    ready ? FayteSkin.mix(FayteSkin.PANEL, gold, 0.18) : FayteSkin.PANEL,
                    ready ? gold : FayteSkin.BORDER);
            if (ready) {
                double ph = (System.currentTimeMillis() % 1400L) / 1400.0;
                int a = 60 + (int) (160 * Math.abs(Math.sin(ph * Math.PI)));
                g.chcolor(gold.getRed(), gold.getGreen(), gold.getBlue(), a);
                g.rect(o.sub(1, 1), SLOT.add(1, 1));
                g.rect(o.sub(2, 2), SLOT.add(3, 3));
                g.chcolor();
            }
            boolean drawn = false;
            if (i == 0 && crres != null) {
                try {
                    if (crtex == null) {
                        crtex = crres.get().layer(Resource.imgc).tex();
                    }
                    fit(g, crtex, o);
                    drawn = true;
                } catch (Loading e) {
                }
            } else if (i == 1 && feastable(tm)) {
                fit(g, feast(), o);
                drawn = true;
            }
            if (!drawn) {
                g.aimage((i == 0 ? crl : fel).tex(), o.add(SLOT.div(2)), 0.5, 0.5);
            }
        }
    }

    private String crname() {
        try {
            return crres.get().layer(Resource.tooltip).t;
        } catch (Loading e) {
            return "...";
        }
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        Tempers tm = this.tm();
        if (button != 1 || tm == null) {
            return true;
        }
        if (c.x < SLOT.x) {
            GameUI gui = getparent(GameUI.class);
            if (crres != null && gui != null && FayteModules.ALMANAC.on()) {
                FayteAlmanacWnd.showlink(gui, crname());
            }
        } else if (feastable(tm)) {
            GameUI gui = getparent(GameUI.class);
            if (gui != null) {
                gui.act("gobble");
            }
        }
        return true;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        if (c.x < SLOT.x) {
            if (crres == null) {
                return "Craving: none right now (on cooldown)";
            }
            if (crtip == null) {
                String grp = "";
                try {
                    List<String> g = FayteCraving.cravgroups(crres.get());
                    grp = g.isEmpty()
                            ? "\nFood group: not known yet (Arcana learns it once one is in your bags)"
                            : "\nFood group: " + String.join(", ", g);
                } catch (Loading e) {
                }
                crtip = RichText.render("Craving: " + crname() + grp + "\n\nClick to look it up in the Almanac.", 260)
                        .tex();
            }
            return crtip;
        } else {
            return feastable(tm()) ? "Feast: click to start feasting" : "Feast: not available right now";
        }
    }
}
