package haven;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class FayteSkillGoal extends Widget {
    private static final int W = FayteSkin.s(230);
    private static final int ROWH = FayteSkin.labelf.height() + FayteSkin.s(10);
    private static final Color READY = new Color(0x58, 0xC8, 0x60);
    private static final Color LOCKED = new Color(0xE0, 0x50, 0x48);
    private static final Color EXPC = new Color(0x50, 0x8C, 0xF0);
    private static FayteSkillGoal instance = null;
    private static String announced = null;
    private final Map<String, Text> texts = new HashMap<>();
    private Tex icon = null;
    private String iconof = null;

    private FayteSkillGoal(Coord c, Widget parent) {
        super(c, new Coord(W, 40), parent);
    }

    public static void reset() {
        announced = null;
        if (instance != null) {
            instance.icon = null;
            instance.iconof = null;
        }
    }

    public static void sync(GameUI gui) {
        String goal = FayteSkills.goal(gui);
        CharWnd.Skill sk = goal == null ? null : FayteSkills.find(gui, goal, false);
        if (goal != null && sk == null && FayteSkills.find(gui, goal, true) != null) {
            CharWnd.Skill l = FayteSkills.find(gui, goal, true);
            String n = FayteSkills.name(l);
            FayteMsg.say("Goal reached: you learned " + (n == null ? goal : n) + ".", GameUI.MsgType.GOOD);
            FayteSkills.setgoal(gui, null);
            goal = null;
        }
        boolean want = goal != null && sk != null;
        if (want && (instance == null || instance.parent != gui)) {
            instance =
                    FayteLanding.add(gui, FayteHud.reg(new FayteSkillGoal(FayteLanding.anchor(gui), gui), "skillgoal"));
        } else if (!want && instance != null) {
            if (instance.attached()) {
                instance.ui.destroy(instance);
            }
            instance = null;
        }
        if (want && sk.afforded() == 0 && !goal.equals(announced)) {
            announced = goal;
            String n = FayteSkills.name(sk);
            FayteMsg.say("You can now learn " + (n == null ? goal : n) + ".", GameUI.MsgType.GOOD);
        }
    }

    private Text text(String s, Color c) {
        String k = c.getRGB() + "|" + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 100) {
                texts.clear();
            }
            t = FayteSkin.labelf.render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    @Override
    public void draw(GOut g) {
        GameUI gui = getparent(GameUI.class);
        CharWnd.Skill sk = FayteSkills.find(gui, FayteSkills.goal(gui), false);
        if (sk == null) {
            return;
        }
        int h = 30 + sk.costa.length * ROWH + 4;
        if (sz.y != h) {
            sz = new Coord(W, h);
        }
        boolean ready = sk.afforded() == 0;
        Color edge = FayteSkin.BORDER;
        if (ready) {
            int a = 120 + (int) (135 * Math.abs(Math.sin((System.currentTimeMillis() % 2000L) / 2000.0 * Math.PI)));
            edge = new Color(READY.getRed(), READY.getGreen(), READY.getBlue(), a);
        }
        FayteSkin.box(g, Coord.z, sz, FayteSkin.PANEL, edge);
        if (icon == null || !sk.nm.equals(iconof)) {
            try {
                Resource.Image img = sk.res.get().layer(Resource.imgc);
                icon = img == null ? null : img.tex();
                iconof = sk.nm;
            } catch (Loading e) {
            }
        }
        if (icon != null) {
            g.image(icon, new Coord(5, 5), new Coord(20, 20));
        }
        String n = FayteSkills.name(sk);
        g.aimage(text(n == null ? "..." : n, FayteSkin.TEXT).tex(), new Coord(30, 15), 0.0, 0.5);
        g.aimage(
                text(ready ? "Ready" : "Goal", ready ? READY : FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.6))
                        .tex(),
                new Coord(W - 6, 15),
                1.0,
                0.5);
        int[] o = CharWnd.sortattrs(sk.costa);
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.75);
        for (int k = 0; k < o.length; k++) {
            int u = o[k];
            CharWnd.Attr a = FayteSkills.attr(gui, sk.costa[u]);
            int y = 30 + k * ROWH;
            int have = a == null ? 0 : a.exp;
            boolean lock = a != null && a.attr.base * 100 < sk.costv[u];
            g.image(
                    text(CharWnd.attrnm.get(sk.costa[u]), lock ? LOCKED : FayteSkin.TEXT)
                            .tex(),
                    new Coord(6, y));
            int gn = FayteProfWnd.inspgain(ui, sk.costa[u]);
            Color gc = have + gn >= sk.costv[u] ? READY : new Color(0xE8, 0xC8, 0x40);
            g.aimage(
                    text(
                                    Math.min(have, sk.costv[u]) + (gn > 0 ? " +" + gn : "") + " / " + sk.costv[u],
                                    gn > 0 ? gc : dim)
                            .tex(),
                    new Coord(W - 6, y),
                    1.0,
                    0.0);
            Coord bc = new Coord(6, y + FayteSkin.labelf.height() + 2);
            Coord bs = new Coord(W - 12, 3);
            Color c = lock ? LOCKED : (have >= sk.costv[u] ? READY : EXPC);
            g.chcolor(FayteSkin.mix(FayteSkin.PANEL, c, 0.25));
            g.frect(bc, bs);
            g.chcolor(c);
            g.frect(bc, new Coord((int) (Utils.clip((double) have / Math.max(1, sk.costv[u]), 0, 1) * bs.x), bs.y));
            if (gn > 0 && have < sk.costv[u]) {
                int x0 = bc.x + (int) (Utils.clip((double) have / Math.max(1, sk.costv[u]), 0, 1) * bs.x);
                int x1 = bc.x + (int) (Utils.clip((double) (have + gn) / Math.max(1, sk.costv[u]), 0, 1) * bs.x);
                g.chcolor(gc);
                g.frect(new Coord(x0, bc.y), new Coord(Math.max(1, x1 - x0), bs.y));
            }
            g.chcolor();
        }
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (button == 1 && !FayteHud.editing()) {
            FayteAlmanacWnd.skills(getparent(GameUI.class), true);
            return true;
        }
        return false;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        return "Your skill goal. Click to open Skills.";
    }
}
