package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteSkillsPanel extends Widget {
    private int PROFW = FayteAlmanacWnd.prefint("fayte_alm_split_profw", 300);
    private int SKW = FayteAlmanacWnd.prefint("fayte_alm_split_skw", 230);
    private static final int SHORTH = FayteSkin.s(28);
    private static final int TALLH = FayteSkin.labelf.height() * 2 + FayteSkin.s(18);
    private int PROWH = SHORTH;
    private boolean tall = false;
    private int profscroll = 0;
    private int HEADH = FayteAlmanacWnd.prefint("fayte_alm_split_skhead", 150);
    private int userh = HEADH;
    private int lastwant = -1;
    private final FayteSplit split1;
    private final FayteSplit split2;
    private final FayteSplit split3;
    private final FayteTitleButton classic;
    private static final int ICON = 32;
    private static final Color READY = new Color(0x58, 0xC8, 0x60);
    private static final Color SHORT = new Color(0xE0, 0xA0, 0x48);
    private static final Color LOCKED = new Color(0xE0, 0x50, 0x48);
    private static final Color EXPC = new Color(0x50, 0x8C, 0xF0);
    private static final Color GAIN = new Color(0xE8, 0xC8, 0x40);
    private static final Color LEVEL = new Color(0xB4, 0xC8, 0xEC);

    private int inspgain(String nm) {
        if (!(ui.lasttip instanceof WItem.ItemTip)) {
            return 0;
        }
        try {
            GItem item = ((WItem.ItemTip) ui.lasttip).item();
            Inspiration insp = ItemInfo.find(Inspiration.class, item.info());
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

    private final GameUI gui;
    private final TextEntry search;
    private final FayteTitleButton availbtn;
    private final FayteTitleButton learnedbtn;
    private final FayteSkillsPanel.SkList list;
    private final FayteWikiEntry.View view;
    private final Button buybtn;
    private final Button popbtn;
    private final Button goalbtn;
    private final Map<String, Text> texts = new HashMap<>();
    private final Map<Object, Tex> icons = new HashMap<>();
    private boolean learned = false;
    private String cursk = null;
    private boolean curlearned = false;
    private String curprof = null;
    private long lastrefresh = 0L;
    private String goallbl = "Set as goal";
    private int lastsig = 0;

    public FayteSkillsPanel(Coord c, Coord sz, Widget parent, GameUI gui) {
        super(c, sz, parent);
        this.gui = gui;
        int sx = PROFW + 8;
        availbtn = new FayteTitleButton(this, "Available", "Skills you can work towards", () -> settab(false));
        availbtn.c = new Coord(sx, 0);
        learnedbtn = new FayteTitleButton(this, "Learned", "Skills you already know", () -> settab(true));
        learnedbtn.c = new Coord(sx + availbtn.sz.x + 6, 0);
        classic = new FayteTitleButton(
                this, "Game window", "Open the game's own character window", () -> gui.classiccw());
        classic.c = new Coord(sz.x - classic.sz.x, 0);
        search = new TextEntry(new Coord(sx, 22), new Coord(SKW, 20), this, "") {
            @Override
            protected void changed() {
                FayteSkillsPanel.this.refilter();
            }
        };
        search.clicktotype = true;
        list = new FayteSkillsPanel.SkList(new Coord(sx, 48), new Coord(SKW, sz.y - 48));
        int rx = sx + SKW + 8;
        popbtn =
                new Button(
                        new Coord(22, CharWnd.attrorder.size() * PROWH + FayteSkin.s(32)),
                        FayteSkin.s(150),
                        this,
                        "Pop out list") {
                    @Override
                    public void click() {
                        FayteProfWnd.toggle(FayteSkillsPanel.this.gui);
                    }
                };
        popbtn.tooltip = Text.render(
                "Open all proficiencies in their own small window, with the inspiration preview and + buttons.");
        buybtn = new Button(new Coord(rx + 6, HEADH - Button.bh() - 8), FayteSkin.s(80), this, "Learn") {
            @Override
            public void click() {
                FayteSkillsPanel.this.buy();
            }
        };
        goalbtn =
                new Button(
                        new Coord(rx + FayteSkin.s(92), HEADH - Button.bh() - 8),
                        FayteSkin.s(110),
                        this,
                        "Set as goal") {
                    @Override
                    public void click() {
                        FayteSkillsPanel.this.togglegoal();
                    }
                };
        view = new FayteWikiEntry.View(new Coord(rx, HEADH), new Coord(sz.x - rx, sz.y - HEADH), this, null);
        view.onlink = (t) -> FayteAlmanacWnd.showlink(gui, t);
        split1 = new FayteSplit(this, true, (p) -> {
            PROFW = p - 1;
            layout();
            Utils.setpref("fayte_alm_split_profw", Integer.toString(PROFW));
        });
        split2 = new FayteSplit(this, true, (p) -> {
            SKW = p - 1 - (PROFW + 8);
            layout();
            Utils.setpref("fayte_alm_split_skw", Integer.toString(SKW));
        });
        split3 = new FayteSplit(this, false, (p) -> {
            HEADH = p + 6;
            userh = HEADH;
            layout();
            Utils.setpref("fayte_alm_split_skhead", Integer.toString(HEADH));
        });
        settab(false);
        layout();
    }

    public void layout() {
        PROFW = Math.max(240, Math.min(420, PROFW));
        SKW = Math.max(150, Math.min(Math.max(150, sz.x - PROFW - 8 - 8 - 260), SKW));
        HEADH = Math.max(FayteSkin.s(130), Math.min(Math.max(FayteSkin.s(130), sz.y - 120), HEADH));
        int sx = PROFW + 8;
        int rx = sx + SKW + 8;
        availbtn.c = new Coord(sx, 0);
        learnedbtn.c = new Coord(sx + availbtn.sz.x + 6, 0);
        classic.c = new Coord(sz.x - classic.sz.x, 0);
        int sy = FayteTitleButton.H + 6;
        search.c = new Coord(sx, sy);
        search.sz = new Coord(SKW, 20);
        list.c = new Coord(sx, sy + 26);
        list.sz = new Coord(SKW, sz.y - sy - 26);
        buybtn.c = new Coord(rx + 6, HEADH - Button.bh() - 8);
        goalbtn.c = new Coord(rx + FayteSkin.s(92), HEADH - Button.bh() - 8);
        view.c = new Coord(rx, HEADH);
        view.resize(new Coord(sz.x - rx, sz.y - HEADH));
        split1.place(PROFW + 1, 0, sz.y);
        split2.place(sx + SKW + 1, 0, sz.y);
        split3.place(HEADH - 6, rx, sz.x - rx);
    }

    @Override
    public void resize(Coord sz) {
        super.resize(sz);
        layout();
    }

    private void settab(boolean l) {
        learned = l;
        availbtn.sel = !l;
        learnedbtn.sel = l;
        list.scroll = 0;
        refilter();
    }

    private void refilter() {
        String q = search.text == null ? "" : search.text.trim().toLowerCase();
        List<CharWnd.Skill> src = learned ? FayteSkills.learned(gui) : FayteSkills.available(gui);
        List<CharWnd.Skill> ret = new ArrayList<>();
        for (CharWnd.Skill s : src) {
            String n = FayteSkills.name(s);
            if (q.isEmpty() || (n != null ? n : s.nm).toLowerCase().contains(q)) {
                ret.add(s);
            }
        }
        ret.sort((a, b) -> {
            String an = FayteSkills.name(a), bn = FayteSkills.name(b);
            return (an == null ? a.nm : an).compareToIgnoreCase(bn == null ? b.nm : bn);
        });
        list.skills = ret;
        availbtn.setlabel("Available (" + FayteSkills.available(gui).size() + ")");
        learnedbtn.setlabel("Learned (" + FayteSkills.learned(gui).size() + ")");
        learnedbtn.c = new Coord(availbtn.c.x + availbtn.sz.x + 6, 0);
        if (cursk == null && curprof == null && !ret.isEmpty()) {
            selectsk(ret.get(0), learned);
        }
    }

    public void selectgoal() {
        String g = FayteSkills.goal(gui);
        if (g != null) {
            settab(false);
            CharWnd.Skill s = FayteSkills.find(gui, g, false);
            if (s != null) {
                selectsk(s, false);
            }
        }
    }

    private CharWnd.Skill cur() {
        return FayteSkills.find(gui, cursk, curlearned);
    }

    private void selectsk(CharWnd.Skill s, boolean learned) {
        cursk = s.nm;
        curlearned = learned;
        curprof = null;
        fillview();
    }

    private void selectprof(String nm) {
        curprof = nm;
        cursk = null;
        fillview();
    }

    private void fillview() {
        FayteWikiEntry g = null;
        if (curprof != null) {
            String n = CharWnd.attrnm.get(curprof);
            FayteWikiData.Entry w = FayteWikiData.find(n);
            if (w != null) {
                g = FayteWikiGlance.full(w, "gfx/hud/skills/" + curprof);
                g.name = n;
            } else {
                g = new FayteWikiEntry();
                g.name = n;
            }
            try {
                Resource.Pagina p = Resource.load("gfx/hud/skills/" + curprof).layer(Resource.pagina);
                if (p != null && g.summary == null) {
                    g.summary = FayteSkills.plain(p.text);
                }
            } catch (Loading e) {
            }
        } else {
            CharWnd.Skill s = cur();
            if (s != null) {
                String n = FayteSkills.name(s);
                FayteWikiData.Entry w = n == null ? null : FayteWikiData.find(n);
                String resnm = null;
                try {
                    resnm = s.res.get().name;
                } catch (Loading e) {
                }
                if (w != null) {
                    g = FayteWikiGlance.full(w, resnm);
                    g.name = n;
                } else {
                    g = new FayteWikiEntry();
                    g.name = n == null ? s.nm : n;
                }
                String t = FayteSkills.text(s);
                if (t != null && !t.isEmpty()) {
                    FayteWikiEntry.Section sec = new FayteWikiEntry.Section();
                    sec.title = "In game";
                    sec.text = t;
                    g.sections.add(0, sec);
                }
            }
        }
        if (g == null) {
            g = new FayteWikiEntry();
            g.name = "Skills";
            g.summary = "Pick a skill or a proficiency.";
        }
        view.set(g);
    }

    private void buy() {
        CharWnd.Skill s = cur();
        if (s == null || curlearned) {
            return;
        }
        if (s.afforded() != 0) {
            FayteMsg.say("Not enough proficiency points for " + FayteSkills.name(s) + " yet.");
            return;
        }
        FayteSkills.buy(gui, s);
    }

    private void togglegoal() {
        CharWnd.Skill s = cur();
        if (s == null || curlearned) {
            return;
        }
        if (s.nm.equals(FayteSkills.goal(gui))) {
            FayteSkills.setgoal(gui, null);
        } else {
            FayteSkills.setgoal(gui, s.nm);
        }
    }

    private Text text(String s, Color c, boolean big) {
        String k = (big ? "b" : "") + c.getRGB() + "|" + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 400) {
                texts.clear();
            }
            t = (big ? Window.bigtf : FayteSkin.labelf).render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private Tex icon(Object key, Indir<Resource> res) {
        Tex t = icons.get(key);
        if (t == null) {
            try {
                Resource.Image img = res.get().layer(Resource.imgc);
                if (img != null) {
                    t = img.tex();
                    icons.put(key, t);
                }
            } catch (Loading e) {
            }
        }
        return t;
    }

    private Tex proficon(String nm) {
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

    private int profat(Coord c) {
        if (c.x < 0 || c.x >= PROFW || c.y < 0) {
            return -1;
        }
        int i = (c.y + profscroll) / PROWH;
        return i < CharWnd.attrorder.size() ? i : -1;
    }

    private static void bar(GOut g, Coord c, Coord sz, double v, Color col) {
        g.chcolor(FayteSkin.mix(FayteSkin.PANEL, col, 0.22));
        g.frect(c, sz);
        g.chcolor(col);
        g.frect(c, new Coord((int) (Utils.clip(v, 0, 1) * sz.x), sz.y));
        g.chcolor(FayteSkin.BORDER);
        g.rect(c, sz.add(1, 1));
        g.chcolor();
    }

    private int profsh() {
        return CharWnd.attrorder.size() * PROWH + FayteSkin.s(32) + Button.bh() + FayteSkin.s(6) + 8;
    }

    private void drawprofs(GOut g0) {
        boolean t = false;
        for (String an : CharWnd.attrorder) {
            CharWnd.Attr aa = FayteSkills.attr(gui, an);
            t |= aa != null && aa.attr.comp >= 100;
        }
        tall = t;
        PROWH = t ? TALLH : SHORTH;
        int maxs = Math.max(0, profsh() - sz.y);
        profscroll = Math.max(0, Math.min(profscroll, maxs));
        GOut g = g0.reclip(new Coord(0, -profscroll), new Coord(PROFW, sz.y + profscroll));
        popbtn.c = new Coord(22, CharWnd.attrorder.size() * PROWH + FayteSkin.s(32) - profscroll);
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.7);
        CharWnd.Skill sel = curlearned ? null : cur();
        double ph = (System.currentTimeMillis() % 1000L) / 1000.0;
        for (int i = 0; i < CharWnd.attrorder.size(); i++) {
            String nm = CharWnd.attrorder.get(i);
            CharWnd.Attr a = FayteSkills.attr(gui, nm);
            int y = i * PROWH;
            if (nm.equals(curprof)) {
                FayteSkin.box(
                        g,
                        new Coord(0, y),
                        new Coord(PROFW, PROWH - 2),
                        FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.5),
                        null);
            }
            Tex ic = proficon(nm);
            if (ic != null) {
                g.image(ic, new Coord(2, y + 5), new Coord(16, 16));
            }
            if (FayteProfPins.pinned(gui, nm)) {
                g.chcolor(READY);
                g.frect(new Coord(0, y + 2), new Coord(2, PROWH - 6));
                g.chcolor();
            }
            int lh = FayteSkin.labelf.height();
            if (a == null) {
                g.aimage(text(CharWnd.attrnm.get(nm), FayteSkin.TEXT, false).tex(), new Coord(22, y + 2), 0.0, 0.0);
                continue;
            }
            int gain = inspgain(nm);
            Text nt = text(CharWnd.attrnm.get(nm), FayteSkin.TEXT, false);
            Text et = text(
                    a.exp + (gain > 0 ? " +" + gain : "") + " / " + a.cap,
                    gain > 0 ? (a.exp + gain >= a.cap ? READY : GAIN) : dim,
                    false);
            Coord bc, bs;
            if (tall) {
                g.image(nt.tex(), new Coord(22, y + 2));
                g.image(
                        text("\u2013  Lv " + a.attr.comp, LEVEL, false).tex(),
                        new Coord(22 + nt.sz().x + FayteSkin.s(6), y + 2));
                g.image(et.tex(), new Coord(22, y + 2 + lh));
                bc = new Coord(22, y + 4 + lh * 2 + FayteSkin.s(3));
                bs = new Coord(PROFW - 22 - 34, 6);
            } else {
                g.image(nt.tex(), new Coord(22, y + 2));
                Tex lv = text("Lv " + a.attr.comp, LEVEL, false).tex();
                g.aimage(lv, new Coord(PROFW - 38, y + 2), 1.0, 0.0);
                g.aimage(et.tex(), new Coord(PROFW - 38 - lv.sz().x - FayteSkin.s(12), y + 2), 1.0, 0.0);
                bc = new Coord(22, y + PROWH - 11);
                bs = new Coord(PROFW - 22 - 36, 6);
            }
            bar(g, bc, bs, (double) a.exp / Math.max(1, a.cap), a.av ? READY : EXPC);
            if (gain > 0) {
                int x0 = bc.x + (int) (Utils.clip((double) a.exp / Math.max(1, a.cap), 0, 1) * bs.x);
                int x1 = bc.x + (int) (Utils.clip((double) (a.exp + gain) / Math.max(1, a.cap), 0, 1) * bs.x);
                g.chcolor(a.exp + gain >= a.cap ? READY : GAIN);
                g.frect(new Coord(x0, bc.y), new Coord(Math.max(1, x1 - x0), bs.y));
                g.chcolor();
            }
            if (sel != null) {
                for (int k = 0; k < sel.costa.length; k++) {
                    if (sel.costa[k].equals(nm)) {
                        int x = bc.x + (int) (Utils.clip((double) sel.costv[k] / Math.max(1, a.cap), 0, 1) * bs.x);
                        g.chcolor(a.attr.base * 100 < sel.costv[k] ? LOCKED : FayteSkin.TEXT);
                        g.frect(new Coord(x - 1, bc.y - 3), new Coord(2, bs.y + 6));
                        g.chcolor();
                    }
                }
            }
            Coord psz = tall ? new Coord(22, 22) : new Coord(22, PROWH - 8);
            Coord pc = tall ? new Coord(PROFW - 26, bc.y + bs.y / 2 - psz.y / 2) : new Coord(PROFW - 26, y + 3);
            if (a.av) {
                int al = 140 + (int) (115 * Math.abs(Math.sin(ph * Math.PI)));
                FayteSkin.box(
                        g, pc, psz, new Color(READY.getRed(), READY.getGreen(), READY.getBlue(), al), FayteSkin.BORDER);
            } else {
                FayteSkin.box(g, pc, psz, FayteSkin.PANEL, FayteSkin.BORDER);
            }
            g.aimage(text("+", a.av ? FayteSkin.TEXT : dim, false).tex(), pc.add(psz.div(2)), 0.5, 0.5);
        }
        int y = CharWnd.attrorder.size() * PROWH + 4;
        if (profscroll > 0 || profsh() > sz.y) {
            int vh = sz.y;
            int th = Math.max(20, vh * vh / profsh());
            int ty = (vh - th) * profscroll / Math.max(1, profsh() - vh);
            g0.chcolor(FayteSkin.mix(FayteSkin.PANEL, FayteSkin.TEXT, 0.35));
            g0.frect(new Coord(PROFW - 3, ty), new Coord(3, th));
            g0.chcolor();
        }
        Glob.CAttr ac = ui.sess.glob.cattr.get("scap"), ar = ui.sess.glob.cattr.get("srate");
        int icap = ac != null ? ac.comp : 0;
        double rate = ar != null ? 3 * ar.comp / 1000.0 : 0;
        g.image(
                text(
                                String.format(
                                        "Inspiration  %,d / %,d   +%.2f/s", FayteSkills.inspiration(gui), icap, rate),
                                FayteSkin.TEXT,
                                false)
                        .tex(),
                new Coord(22, y));
        bar(
                g,
                new Coord(22, y + 16),
                new Coord(PROFW - 58, 6),
                icap > 0 ? (double) FayteSkills.inspiration(gui) / icap : 0,
                new Color(0xA8, 0x80, 0xC8));
    }

    private void drawhead(GOut g) {
        int rx = PROFW + 8 + SKW + 8;
        FayteSkin.box(
                g, new Coord(rx, 0), new Coord(sz.x - rx, HEADH - Button.bh() - 12), FayteSkin.PANEL, FayteSkin.BORDER);
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.7);
        CharWnd.Skill s = cursk == null ? null : cur();
        if (curprof != null) {
            CharWnd.Attr a = FayteSkills.attr(gui, curprof);
            Tex ic = proficon(curprof);
            if (ic != null) {
                g.image(ic, new Coord(rx + 6, 6), new Coord(ICON, ICON));
            }
            g.image(text(CharWnd.attrnm.get(curprof), FayteSkin.TEXT, true).tex(), new Coord(rx + ICON + 12, 4));
            if (a != null) {
                g.image(
                        text("Level " + a.attr.comp + " (base " + a.attr.base + ")", dim, false)
                                .tex(),
                        new Coord(rx + ICON + 12, FayteSkin.s(24)));
                g.image(
                        text(
                                        "Points " + a.exp + " / " + a.cap
                                                + (a.av ? "  \u00b7  ready to raise with +" : ""),
                                        a.av ? READY : dim,
                                        false)
                                .tex(),
                        new Coord(rx + 6, FayteSkin.s(50)));
            }
            buybtn.visible = false;
            goalbtn.visible = false;
            return;
        }
        if (s == null) {
            buybtn.visible = false;
            goalbtn.visible = false;
            return;
        }
        Tex ic = icon(s.nm, s.res);
        if (ic != null) {
            g.image(ic, new Coord(rx + 6, 6), new Coord(ICON, ICON));
        }
        String n = FayteSkills.name(s);
        g.image(text(n == null ? "..." : n, FayteSkin.TEXT, true).tex(), new Coord(rx + ICON + 12, 4));
        boolean goal = s.nm.equals(FayteSkills.goal(gui));
        String st;
        Color sc;
        if (curlearned) {
            st = "Learned";
            sc = READY;
        } else {
            int af = s.afforded();
            st = af == 0 ? "Ready to learn" : (af == 3 ? "Proficiency level too low" : "Collecting points");
            sc = af == 0 ? READY : (af == 3 ? LOCKED : SHORT);
        }
        g.image(
                text(st + (goal ? "  \u00b7  your goal" : ""), sc, false).tex(),
                new Coord(rx + ICON + 12, FayteSkin.s(24)));
        int[] o = CharWnd.sortattrs(s.costa);
        int y = FayteSkin.s(44);
        int x = rx + 6;
        int w = sz.x - rx - 12;
        int rowh = FayteSkin.s(22);
        int need = y + o.length * rowh + Button.bh() + 20;
        int want = Math.max(userh, need);
        if (want != lastwant) {
            lastwant = want;
            HEADH = want;
            layout();
        }
        for (int k = 0; k < o.length; k++) {
            int u = o[k];
            CharWnd.Attr a = FayteSkills.attr(gui, s.costa[u]);
            Coord c = new Coord(x, y + k * rowh);
            int have = a == null ? 0 : a.exp;
            boolean lock = a != null && a.attr.base * 100 < s.costv[u];
            String lbl = CharWnd.attrnm.get(s.costa[u]);
            g.image(text(lbl, lock ? LOCKED : FayteSkin.TEXT, false).tex(), c);
            int gain = curlearned ? 0 : inspgain(s.costa[u]);
            String amt =
                    (curlearned ? "" : Math.min(have, s.costv[u]) + (gain > 0 ? " +" + gain : "") + " / ") + s.costv[u];
            g.aimage(
                    text(amt, gain > 0 ? (have + gain >= s.costv[u] ? READY : GAIN) : dim, false)
                            .tex(),
                    c.add(w, 0),
                    1.0,
                    0.0);
            if (!curlearned) {
                Coord bc = c.add(0, FayteSkin.labelf.height() + 1);
                double need1 = Math.max(1, s.costv[u]);
                bar(g, bc, new Coord(w, 4), have / need1, lock ? LOCKED : (have >= s.costv[u] ? READY : EXPC));
                if (gain > 0 && have < s.costv[u]) {
                    int x0 = bc.x + (int) (Utils.clip(have / need1, 0, 1) * w);
                    int x1 = bc.x + (int) (Utils.clip((have + gain) / need1, 0, 1) * w);
                    g.chcolor(have + gain >= s.costv[u] ? READY : GAIN);
                    g.frect(new Coord(x0, bc.y), new Coord(Math.max(1, x1 - x0), 4));
                    g.chcolor();
                }
            }
        }
        buybtn.visible = !curlearned;
        goalbtn.visible = !curlearned;
        String gl = goal ? "Clear goal" : "Set as goal";
        if (!gl.equals(goallbl)) {
            goallbl = gl;
            goalbtn.change(gl);
        }
    }

    @Override
    public void draw(GOut g) {
        if (FayteSkills.cw(gui) == null) {
            g.image(text("Skills are not loaded yet.", FayteSkin.TEXT, false).tex(), new Coord(4, 4));
            return;
        }
        drawprofs(g);
        drawhead(g);
        super.draw(g);
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        int i = profat(c);
        if (button == 3 && i >= 0) {
            FayteProfPins.toggle(gui, CharWnd.attrorder.get(i));
            return true;
        }
        if (button == 1 && i >= 0) {
            String nm = CharWnd.attrorder.get(i);
            CharWnd.Attr a = FayteSkills.attr(gui, nm);
            if (c.x >= PROFW - 26 && a != null) {
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
            } else {
                selectprof(nm);
            }
            return true;
        }
        return super.mousedown(c, button);
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        if (c.x >= 0 && c.x < PROFW) {
            profscroll = Math.max(0, profscroll + amount * PROWH);
            return true;
        }
        return super.mousewheel(c, amount);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        int i = profat(c);
        if (i >= 0) {
            String nm = CharWnd.attrorder.get(i);
            if (c.x >= PROFW - 26) {
                return "Raise " + CharWnd.attrnm.get(nm) + " by one level when its points are full";
            }
            return "Level and points toward the next level. A white tick shows what the selected skill needs (red:"
                    + " your level is too low). Right-click to pin it to the screen.";
        }
        return super.tooltip(c, prev);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        long now = System.currentTimeMillis();
        if (now - lastrefresh > 1000L) {
            lastrefresh = now;
            int sig = FayteSkills.available(gui).size() * 1000
                    + FayteSkills.learned(gui).size();
            if (sig != lastsig) {
                lastsig = sig;
                refilter();
                fillview();
            }
        }
    }

    private class SkList extends Widget {
        List<CharWnd.Skill> skills = new ArrayList<>();
        int scroll = 0;

        SkList(Coord c, Coord sz) {
            super(c, sz, FayteSkillsPanel.this);
        }

        private int rowh() {
            return Math.max(22, FayteText.LIST.line());
        }

        @Override
        public void draw(GOut g) {
            FayteSkin.box(g, Coord.z, sz, FayteSkin.PANEL, FayteSkin.BORDER);
            int rh = rowh();
            int rows = (sz.y - 8) / rh;
            scroll = Math.max(0, Math.min(scroll, Math.max(0, skills.size() - rows)));
            String goal = FayteSkills.goal(FayteSkillsPanel.this.gui);
            for (int i = 0; i < rows && scroll + i < skills.size(); i++) {
                CharWnd.Skill s = skills.get(scroll + i);
                Coord rc = new Coord(4, 4 + i * rh);
                if (s.nm.equals(FayteSkillsPanel.this.cursk)) {
                    FayteSkin.box(g, rc.sub(2, 0), new Coord(sz.x - 4, rh), FayteSkin.BORDER, null);
                }
                Tex ic = FayteSkillsPanel.this.icon(s.nm, s.res);
                if (ic != null) {
                    g.image(ic, rc.add(2, 1), new Coord(rh - 2, rh - 2));
                }
                Color c = FayteSkin.TEXT;
                if (!FayteSkillsPanel.this.learned) {
                    int af = s.afforded();
                    c = af == 0 ? READY : (af == 3 ? FayteSkin.mix(FayteSkin.BORDER, LOCKED, 0.8) : FayteSkin.TEXT);
                }
                String n = FayteSkills.name(s);
                Text t = FayteSkillsPanel.this.text(
                        (s.nm.equals(goal) ? "\u2605 " : "") + (n == null ? "..." : n), c, false);
                g.image(t.tex(), rc.add(rh + 4, (rh - t.sz().y) / 2));
            }
        }

        @Override
        public boolean mousedown(Coord c, int button) {
            if (button == 1) {
                int i = scroll + (c.y - 4) / rowh();
                if (c.y >= 4 && i >= 0 && i < skills.size()) {
                    FayteSkillsPanel.this.selectsk(skills.get(i), FayteSkillsPanel.this.learned);
                }
            }
            return true;
        }

        @Override
        public boolean mousewheel(Coord c, int amount) {
            scroll += amount * 3;
            return true;
        }
    }

    public boolean armsearch() {
        if (search == null || !search.visible) {
            return false;
        }
        search.arm();
        return true;
    }
}
