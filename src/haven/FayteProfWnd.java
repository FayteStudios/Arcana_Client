package haven;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class FayteProfWnd extends Window {
    private static final Color READY = new Color(0x58, 0xC8, 0x60);
    private static final Color EXPC = new Color(0x50, 0x8C, 0xF0);
    private static final Color GAIN = new Color(0xE8, 0xC8, 0x40);
    private static FayteProfWnd instance = null;
    private final GameUI gui;
    private final Map<String, Tex> icons = new HashMap<>();
    private final Map<String, Text> texts = new HashMap<>();
    private final int rowh = FayteSkin.s(26);

    public static void toggle(GameUI gui) {
        if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
            instance = null;
        } else {
            instance = new FayteProfWnd(gui);
        }
    }

    private FayteProfWnd(GameUI gui) {
        super(
                new Coord(FayteSkin.s(40), FayteSkin.s(120)),
                new Coord(FayteSkin.s(300), CharWnd.attrorder.size() * FayteSkin.s(26) + FayteSkin.s(34)),
                gui,
                "Proficiencies");
        this.gui = gui;
        justclose = true;
    }

    private Text text(String s, Color c) {
        String k = c.getRGB() + "|" + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 200) {
                texts.clear();
            }
            t = FayteSkin.labelf.render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private Tex icon(String nm) {
        Tex t = icons.get(nm);
        if (t == null) {
            try {
                Resource.Image img = Resource.load("gfx/hud/skills/" + nm).layer(Resource.imgc);
                if (img != null) {
                    t = img.tex();
                    icons.put(nm, t);
                }
            } catch (Loading e) {
            }
        }
        return t;
    }

    private int gain(String nm) {
        return inspgain(ui, nm);
    }

    public static int inspgain(UI ui, String nm) {
        if (ui == null || !(ui.lasttip instanceof WItem.ItemTip)) {
            return 0;
        }
        try {
            Inspiration insp = ItemInfo.find(
                    Inspiration.class, ((WItem.ItemTip) ui.lasttip).item().info());
            if (insp != null) {
                for (int i = 0; i < insp.attrs.length; i++) {
                    if (insp.attrs[i].equals(nm)) {
                        return insp.exp[i];
                    }
                }
            }
        } catch (Loading e) {
        }
        return 0;
    }

    @Override
    public void cdraw(GOut g) {
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.7);
        int w = asz.x;
        double ph = (System.currentTimeMillis() % 1000L) / 1000.0;
        for (int i = 0; i < CharWnd.attrorder.size(); i++) {
            String nm = CharWnd.attrorder.get(i);
            CharWnd.Attr a = FayteSkills.attr(gui, nm);
            int y = i * rowh;
            Tex ic = icon(nm);
            if (ic != null) {
                g.image(ic, new Coord(0, y + 4), new Coord(16, 16));
            }
            g.image(text(CharWnd.attrnm.get(nm), FayteSkin.TEXT).tex(), new Coord(20, y + 1));
            if (a == null) {
                continue;
            }
            int gn = gain(nm);
            g.aimage(text(Integer.toString(a.attr.comp), FayteSkin.TEXT).tex(), new Coord(w - 36, y + 1), 1.0, 0.0);
            g.aimage(
                    text(
                                    a.exp + (gn > 0 ? " +" + gn : "") + " / " + a.cap,
                                    gn > 0 ? (a.exp + gn >= a.cap ? READY : GAIN) : dim)
                            .tex(),
                    new Coord(w - 62, y + 1),
                    1.0,
                    0.0);
            Coord bc = new Coord(20, y + rowh - 8);
            Coord bs = new Coord(w - 20 - 34, 5);
            g.chcolor(FayteSkin.mix(FayteSkin.PANEL, EXPC, 0.25));
            g.frect(bc, bs);
            g.chcolor(a.av ? READY : EXPC);
            g.frect(bc, new Coord((int) (bs.x * Utils.clip((double) a.exp / Math.max(1, a.cap), 0, 1)), bs.y));
            if (gn > 0) {
                int x0 = bc.x + (int) (Utils.clip((double) a.exp / Math.max(1, a.cap), 0, 1) * bs.x);
                int x1 = bc.x + (int) (Utils.clip((double) (a.exp + gn) / Math.max(1, a.cap), 0, 1) * bs.x);
                g.chcolor(a.exp + gn >= a.cap ? READY : GAIN);
                g.frect(new Coord(x0, bc.y), new Coord(Math.max(1, x1 - x0), bs.y));
            }
            g.chcolor();
            Coord pc = new Coord(w - 24, y + 2);
            Coord psz = new Coord(22, rowh - 6);
            if (a.av) {
                int al = 140 + (int) (115 * Math.abs(Math.sin(ph * Math.PI)));
                FayteSkin.box(
                        g, pc, psz, new Color(READY.getRed(), READY.getGreen(), READY.getBlue(), al), FayteSkin.BORDER);
            } else {
                FayteSkin.box(g, pc, psz, FayteSkin.PANEL, FayteSkin.BORDER);
            }
            g.aimage(text("+", a.av ? FayteSkin.TEXT : dim).tex(), pc.add(psz.div(2)), 0.5, 0.5);
        }
        int y = CharWnd.attrorder.size() * rowh + 4;
        Glob.CAttr ac = ui.sess.glob.cattr.get("scap");
        int icap = ac != null ? ac.comp : 0;
        int ins = FayteSkills.inspiration(gui);
        g.image(
                text(String.format("Inspiration  %,d / %,d", ins, icap), FayteSkin.TEXT)
                        .tex(),
                new Coord(20, y));
        g.chcolor(new Color(0xA8, 0x80, 0xC8));
        g.frect(
                new Coord(20, y + FayteSkin.labelf.height() + 2),
                new Coord((int) ((w - 20) * (icap > 0 ? Math.min(1.0, (double) ins / icap) : 0)), 5));
        g.chcolor();
    }

    private int rowat(Coord c) {
        Coord p = c.sub(atl);
        int i = p.y / rowh;
        return p.y >= 0 && i >= 0 && i < CharWnd.attrorder.size() ? i : -1;
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        int i = rowat(c);
        if (i >= 0) {
            String nm = CharWnd.attrorder.get(i);
            CharWnd.Attr a = FayteSkills.attr(gui, nm);
            Coord p = c.sub(atl);
            if (button == 1 && a != null && p.x >= asz.x - 24) {
                if (a.av) {
                    final CharWnd.Attr fa = a;
                    FayteConfirm.ask(
                            gui,
                            "profbuy",
                            "Raise " + CharWnd.attrnm.get(nm) + "?",
                            "The proficiency you choose gives 2 points, any other full proficiencies will give 1"
                                    + " point. Are you sure you want this option?",
                            fa::buy);
                } else {
                    FayteMsg.say("Not enough points to raise " + CharWnd.attrnm.get(nm) + " yet.");
                }
                return true;
            } else if (button == 3) {
                FayteProfPins.toggle(gui, nm);
                return true;
            }
        }
        return super.mousedown(c, button);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        int i = rowat(c);
        if (i >= 0) {
            return "Hover an inspirational item to preview what it adds (yellow; green if it fills the level)."
                    + " Right-click to pin this one to the screen.";
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
