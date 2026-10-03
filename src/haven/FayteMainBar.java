package haven;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class FayteMainBar extends Widget {
    private static final String[][] BUTTONS = {
        {"cmd:almanac", "Almanac", "paginae/skills/education"},
        {"fayte:equipment", "Equipment", "gfx/hud/equup"},
        {"cmd:invpack", "Bag (inventory and backpack)", "gfx/hud/invup"},
        {"cmd:actions", "Actions, bars and macros", "paginae/skills/bullying"},
        {"cmd:options", "Options", "gfx/hud/optup"},
    };
    private final Tex[] icons = new Tex[BUTTONS.length];
    private int hover = -1;
    private int almbase = -1;
    private int almnew = 0;
    private long lastcount = 0L;
    private final Map<String, Tex> badges = new HashMap<>();

    public static void seenactions(GameUI gui) {
        synchronized (gui.ui.sess.glob.paginae) {
            for (Glob.Pagina p : gui.ui.sess.glob.paginae) {
                if (p.newp == 1) {
                    p.newp = 0;
                }
            }
        }
    }

    private void count() {
        long now = System.currentTimeMillis();
        if (now - lastcount < 1000L) {
            return;
        }
        lastcount = now;
        int fresh = FayteAlmanac.freshall();
        if (almbase < 0 || FayteAlmanacWnd.isopen() || fresh < almbase) {
            almbase = fresh;
        }
        almnew = Math.max(0, fresh - almbase) + FayteKinReq.count();
    }

    private Tex badge(int n) {
        String k = n > 99 ? "99+" : Integer.toString(n);
        Tex t = badges.get(k);
        if (t == null) {
            t = new TexI(Utils.outline2(FayteSkin.labelf.render(k, Color.WHITE).img, Color.BLACK, true));
            badges.put(k, t);
        }
        return t;
    }

    public static int cell() {
        return FayteSkin.s(52);
    }

    public static int width() {
        return BUTTONS.length * (cell() + 4) + 4;
    }

    public static int height() {
        return cell() + 8;
    }

    public FayteMainBar(Coord c, Widget parent) {
        super(c, new Coord(width(), height()), parent);
    }

    private Tex icon(int i) {
        if (icons[i] == null) {
            try {
                String r = BUTTONS[i][2];
                if (r.startsWith("gfx/hud/")) {
                    icons[i] = new TexI(Resource.loadimg(r));
                } else {
                    Resource.Image img = Resource.load(r).layer(Resource.imgc);
                    if (img != null) {
                        icons[i] = img.tex();
                    }
                }
            } catch (Loading e) {
            } catch (RuntimeException e) {
                FayteLog.once("FayteMainBar.icon", e);
            }
        }
        return icons[i];
    }

    private int at(Coord c) {
        if (c.y < 4 || c.y >= 4 + cell()) {
            return -1;
        }
        int i = (c.x - 4) / (cell() + 4);
        return i >= 0 && i < BUTTONS.length && (c.x - 4) % (cell() + 4) < cell() ? i : -1;
    }

    @Override
    public void draw(GOut g) {
        count();
        FayteSkin.box(g, Coord.z, sz, FayteSkin.PANEL, FayteSkin.BORDER);
        int cs = cell();
        for (int i = 0; i < BUTTONS.length; i++) {
            Coord bc = new Coord(4 + i * (cs + 4), 4);
            FayteSkin.box(
                    g,
                    bc,
                    new Coord(cs, cs),
                    i == hover ? FayteSkin.HOVER : FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.25),
                    FayteSkin.BORDER);
            Tex t = icon(i);
            if (t != null) {
                g.image(t, bc.add(3, 3), new Coord(cs - 6, cs - 6));
            }
            int nn = BUTTONS[i][0].equals("cmd:almanac") ? almnew : 0;
            if (nn > 0) {
                Color gold = new Color(0xF0, 0xC8, 0x40);
                double ph = (System.currentTimeMillis() % 1400L) / 1400.0;
                int al = 70 + (int) (170 * Math.abs(Math.sin(ph * Math.PI)));
                g.chcolor(gold.getRed(), gold.getGreen(), gold.getBlue(), al);
                g.rect(bc.sub(1, 1), new Coord(cs + 1, cs + 1));
                g.rect(bc.sub(2, 2), new Coord(cs + 3, cs + 3));
                g.chcolor();
                Tex bt = badge(nn);
                Coord bsz = new Coord(Math.max(bt.sz().x + 6, FayteSkin.s(16)), bt.sz().y + 2);
                Coord bpos = bc.add(cs - bsz.x + 3, -3);
                FayteSkin.box(g, bpos, bsz, new Color(0xC0, 0x40, 0x30), null);
                g.aimage(bt, bpos.add(bsz.div(2)), 0.5, 0.5);
            }
        }
    }

    @Override
    public void mousemove(Coord c) {
        hover = at(c);
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        int i = at(c);
        if (i >= 0 && button == 1 && !FayteHud.editing()) {
            FayteActs.run(getparent(GameUI.class), BUTTONS[i][0]);
            return true;
        }
        return i >= 0;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        int i = at(c);
        if (i < 0) {
            return null;
        }
        if (BUTTONS[i][0].equals("cmd:almanac") && almnew > 0) {
            return BUTTONS[i][1] + ": " + almnew + " new " + (almnew == 1 ? "recipe or skill" : "recipes and skills");
        }
        return BUTTONS[i][1];
    }
}
