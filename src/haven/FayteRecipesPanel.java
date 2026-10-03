package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class FayteRecipesPanel extends Widget {
    private static final String SPLITPREF = "fayte_alm_split_recipes";
    private int LISTW = FayteAlmanacWnd.prefint(SPLITPREF, 260);
    private final FayteSplit split;
    private final Label makelbl;
    private final Button craftb;
    private final Button craftnb;
    private final Button stopb;
    private static final int HEADH = FayteSkin.s(250);
    private static final String ALL = "All";
    private final GameUI gui;
    private final TextEntry search;
    private final FayteTitleButton catbtn;
    private final FayteTitleButton favbtn;
    private final FayteTitleButton skillbtn;
    private final TextEntry qty;
    private final Button minusb;
    private final Button plusb;
    private final Button allb;
    private final Button gamebtn;
    private final FayteRecipesPanel.RList list;
    private final FayteWikiEntry.View view;
    private final Map<String, Text> texts = new HashMap<>();
    private final Map<BufferedImage, Tex> texs = new IdentityHashMap<>();
    private String cat = ALL;
    private String skill = ALL;
    private List<String> station = null;

    public void station(List<String> st) {
        station = st;
        cur = null;
        cat = ALL;
        catbtn.setlabel((bmode ? "Type: " : "Menu: ") + "at " + st.get(0));
        refilter();
    }

    private FayteRecipes.Recipe cur;
    private List<String[]> ingr = new ArrayList<>();
    private long lastrefresh = 0L;
    private int lastsize = -1;

    private final boolean bmode;
    private static final String FAV = "\u2605 Favorites";

    public FayteRecipesPanel(Coord c, Coord sz, Widget parent, GameUI gui) {
        this(c, sz, parent, gui, false);
    }

    private List<FayteRecipes.Recipe> source() {
        return bmode ? FayteRecipes.builds(gui) : FayteRecipes.list(gui);
    }

    public FayteRecipesPanel(Coord c, Coord sz, Widget parent, GameUI gui, boolean build) {
        super(c, sz, parent);
        this.gui = gui;
        bmode = build;
        search = new TextEntry(new Coord(0, 0), new Coord(LISTW, 20), this, "") {
            @Override
            protected void changed() {
                FayteRecipesPanel.this.refilter();
            }
        };
        search.clicktotype = true;
        catbtn = new FayteTitleButton(
                this,
                build ? "Type: All" : "Menu: All",
                build
                        ? "Only show one kind of building, or your favorites"
                        : "Only show recipes from one part of the crafting menu, or your favorites",
                this::pickcat);
        catbtn.c = new Coord(0, 26);
        favbtn = new FayteTitleButton(
                this, "\u2605", "Quick swap between your favorites and the last menu filter", this::togglefav);
        favbtn.c = new Coord(catbtn.sz.x + 4, 26);
        skillbtn = new FayteTitleButton(
                this, "Skill: All", "Only show recipes unlocked by one skill (from the wiki)", this::pickskill);
        skillbtn.c = new Coord(LISTW / 2 + 2, 26);
        list = new FayteRecipesPanel.RList(new Coord(0, 48), new Coord(LISTW, sz.y - 48));
        int rx = LISTW + 8;
        int by = HEADH - Button.bh() - 6;
        makelbl = new Label(new Coord(rx + 6, by + 3), this, "Make");
        minusb = new Button(new Coord(rx + FayteSkin.s(44), by - 2), FayteSkin.s(22), this, "\u2212") {
            @Override
            public void click() {
                FayteRecipesPanel.this.bump(-1);
            }
        };
        plusb = new Button(new Coord(rx + FayteSkin.s(112), by - 2), FayteSkin.s(22), this, "+") {
            @Override
            public void click() {
                FayteRecipesPanel.this.bump(1);
            }
        };
        qty = new TextEntry(new Coord(rx + FayteSkin.s(68), by), new Coord(FayteSkin.s(42), 20), this, "1") {
            @Override
            public void activate(String text) {
                FayteRecipesPanel.this.craft(FayteRecipesPanel.this.count());
            }
        };
        qty.clicktotype = true;
        craftb = new Button(new Coord(rx + FayteSkin.s(140), by - 2), FayteSkin.s(64), this, "Craft") {
            @Override
            public void click() {
                FayteRecipesPanel.this.craft(1);
            }
        };
        craftnb = new Button(new Coord(rx + FayteSkin.s(208), by - 2), FayteSkin.s(80), this, "Craft N") {
            @Override
            public void click() {
                FayteRecipesPanel.this.craft(FayteRecipesPanel.this.count());
            }
        };
        allb = new Button(new Coord(rx + FayteSkin.s(292), by - 2), FayteSkin.s(52), this, "All") {
            @Override
            public void click() {
                FayteRecipesPanel.this.craftall();
            }
        };
        stopb = new Button(new Coord(rx + FayteSkin.s(348), by - 2), FayteSkin.s(56), this, "Stop") {
            @Override
            public void click() {
                FayteRecipes.stop("Crafting stopped");
            }
        };
        gamebtn = new Button(new Coord(rx + FayteSkin.s(408), by - 2), FayteSkin.s(110), this, "Game window") {
            @Override
            public void click() {
                if (FayteRecipesPanel.this.cur != null) {
                    FayteRecipes.opengame(gui, FayteRecipesPanel.this.cur);
                }
            }
        };
        gamebtn.tooltip =
                Text.render("Open the game's own crafting window for this recipe, in case Arcana's data is wrong.");
        view = new FayteWikiEntry.View(new Coord(rx, HEADH), new Coord(sz.x - rx, sz.y - HEADH), this, null);
        view.onlink = (t) -> FayteAlmanacWnd.showlink(gui, t);
        split = new FayteSplit(this, true, (p) -> {
            LISTW = Math.max(160, Math.min(this.sz.x - FayteSkin.s(530), p - 1));
            Utils.setpref(SPLITPREF, Integer.toString(LISTW));
            layout();
        });
        visible = false;

        refilter();
        layout();
    }

    public void layout() {
        LISTW = Math.max(160, Math.min(Math.max(160, sz.x - FayteSkin.s(530)), LISTW));
        int rx = LISTW + 8;
        int by = HEADH - Button.bh() - 6;
        search.sz = new Coord(LISTW, 20);
        favbtn.c = new Coord(catbtn.sz.x + 4, 26);
        skillbtn.c = new Coord(Math.max(favbtn.c.x + favbtn.sz.x + 4, LISTW / 2 + 2), 26);
        int ly = 26 + FayteTitleButton.H + 4;
        list.c = new Coord(0, ly);
        list.sz = new Coord(LISTW, sz.y - ly);
        makelbl.c = new Coord(rx + 6, by + 3);
        minusb.c = new Coord(rx + FayteSkin.s(44), by - 2);
        qty.c = new Coord(rx + FayteSkin.s(68), by);
        plusb.c = new Coord(rx + FayteSkin.s(112), by - 2);
        craftb.c = new Coord(rx + FayteSkin.s(140), by - 2);
        craftnb.c = new Coord(rx + FayteSkin.s(208), by - 2);
        allb.c = new Coord(rx + FayteSkin.s(292), by - 2);
        stopb.c = new Coord(rx + FayteSkin.s(348), by - 2);
        gamebtn.c = new Coord(rx + FayteSkin.s(408), by - 2);
        if (bmode) {
            makelbl.visible = false;
            minusb.visible = false;
            plusb.visible = false;
            allb.visible = false;
            qty.visible = false;
            craftnb.visible = false;
            stopb.visible = false;
            craftb.c = new Coord(rx + 6, by - 2);
            gamebtn.c = new Coord(craftb.c.x + craftb.sz.x + FayteSkin.s(6), by - 2);
            if (!buildlabel) {
                buildlabel = true;
                craftb.change("Build");
            }
        }
        view.c = new Coord(rx, HEADH);
        view.resize(new Coord(sz.x - rx, sz.y - HEADH));
        split.place(LISTW + 1, 0, sz.y);
    }

    @Override
    public void resize(Coord sz) {
        super.resize(sz);
        layout();
    }

    private boolean buildlabel = false;

    private void craftall() {
        if (cur == null || bmode) {
            return;
        }
        craft(999);
    }

    public void shoplist() {
        if (cur == null) {
            FayteMsg.say("Pick a recipe first.");
            return;
        }
        FayteShopList.add(gui, cur.name, count());
    }

    private void bump(int d) {
        int n = 1;
        try {
            n = Integer.parseInt(qty.text.trim());
        } catch (NumberFormatException e) {
        }
        qty.settext(Integer.toString(Math.max(1, Math.min(999, n + d))));
    }

    private int count() {
        if (bmode) {
            return 1;
        }
        try {
            return Math.max(1, Math.min(999, Integer.parseInt(qty.text.trim())));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void craft(int n) {
        if (cur == null) {
            return;
        }
        if (bmode) {
            FayteRecipes.build(gui, cur, null);
            FayteAlmanacWnd.closelater();
            return;
        }
        if (FayteRecipes.busy()) {
            FayteRecipes.stop(null);
        }
        FayteRecipes.craft(gui, cur, n);
    }

    private String lastcat = ALL;

    private void togglefav() {
        station = null;
        cat = FAV.equals(cat) ? lastcat : FAV;
        catbtn.setlabel((bmode ? "Type: " : "Menu: ") + cat);
        favbtn.sel = FAV.equals(cat);
        layout();
        refilter();
    }

    private void pickcat() {
        List<String> cs = new ArrayList<>();
        cs.add(ALL);
        cs.add(FAV);
        TreeSet<String> tops = new TreeSet<>();
        for (FayteRecipes.Recipe r : source()) {
            tops.add(r.top());
        }
        cs.addAll(tops);
        String[] opts = cs.toArray(new String[0]);
        new FaytePopup(catbtn.c.add(0, FayteTitleButton.H + 2), this, opts, cs.indexOf(cat), (i) -> {
            station = null;
            cat = opts[i];
            if (!FAV.equals(cat)) {
                lastcat = cat;
            }
            catbtn.setlabel((bmode ? "Type: " : "Menu: ") + cat);
            favbtn.sel = FAV.equals(cat);
            layout();
            refilter();
        });
    }

    private void pickskill() {
        List<String> ss = new ArrayList<>();
        ss.add(ALL);
        TreeSet<String> sks = new TreeSet<>();
        for (FayteRecipes.Recipe r : source()) {
            sks.addAll(FayteRecipes.skillsfor(r.name));
        }
        ss.addAll(sks);
        String[] opts = ss.toArray(new String[0]);
        new FaytePopup(skillbtn.c.add(0, FayteTitleButton.H + 2), this, opts, ss.indexOf(skill), (i) -> {
            skill = opts[i];
            skillbtn.setlabel("Skill: " + skill);
            refilter();
        });
    }

    private void refilter() {
        String q = search.text == null ? "" : search.text.trim().toLowerCase();
        List<FayteRecipes.Recipe> all = source();
        List<FayteRecipes.Recipe> ret = new ArrayList<>();
        for (FayteRecipes.Recipe r : all) {
            if (!q.isEmpty() && !r.name.toLowerCase().contains(q)) {
                continue;
            }
            if (station != null) {
                if (!FayteStations.uses(r.name, station)) {
                    continue;
                }
            } else if (FAV.equals(cat)) {
                if (!FayteRecipes.fav(r.res)) {
                    continue;
                }
            } else if (!ALL.equals(cat) && !cat.equals(r.top())) {
                continue;
            }
            if (!ALL.equals(skill) && !FayteRecipes.skillsfor(r.name).contains(skill)) {
                continue;
            }
            ret.add(r);
        }
        lastsize = all.size();
        list.recs = ret;
        if (cur != null) {
            for (FayteRecipes.Recipe r : all) {
                if (r.res.equals(cur.res)) {
                    cur = r;
                }
            }
        }
        if (cur == null && station != null && !ret.isEmpty()) {
            FayteWikiEntry e = new FayteWikiEntry();
            e.name = "Pick something to make";
            e.summary = ret.size() + (ret.size() == 1 ? " recipe" : " recipes")
                    + " you know can be made here. Choose one on the left.";
            view.set(e);
        } else if (cur == null && !ret.isEmpty()) {
            select(ret.get(0));
        } else if (cur == null) {
            FayteWikiEntry e = new FayteWikiEntry();
            e.name = all.isEmpty() ? (bmode ? "Nothing to build yet" : "No recipes yet") : "No matches";
            e.summary = all.isEmpty()
                    ? (bmode ? "Buildings you know show up here." : "Recipes you know show up here.")
                    : "Nothing matches the search and filters.";
            view.set(e);
        }
    }

    public String curname() {
        return cur == null ? null : cur.name;
    }

    public void previewcur() {
        if (cur != null && !bmode) {
            FayteRecipes.preview(gui, cur);
        }
    }

    public void select(String name) {
        for (FayteRecipes.Recipe r : source()) {
            if (r.name.equalsIgnoreCase(name)) {
                select(r);
                list.ensurevisible(r);
                return;
            }
        }
    }

    private void select(FayteRecipes.Recipe r) {
        cur = r;
        FayteAlmanac.visited(FayteAlmanac.get(FayteAlmanac.RECIPES, r.name));
        if (!bmode && visible && parent != null && parent.visible) {
            FayteRecipes.preview(gui, r);
        }
        ingr = FayteRecipes.ingredients(r.name);
        FayteWikiData.Entry w = FayteWikiData.find(r.name);
        FayteWikiEntry g;
        if (w != null) {
            g = FayteWikiGlance.full(w, r.res);
            g.name = r.name;
        } else {
            g = new FayteWikiEntry();
            g.name = r.name;
            g.icon = r.res;
            g.summary = "No wiki page found for this recipe.";
        }
        view.set(g);
    }

    private Text text(String s, Color c, boolean big) {
        String k = (big ? "b" : "") + c.getRGB() + "|" + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 300) {
                texts.clear();
            }
            t = (big ? Window.bigtf : FayteSkin.labelf).render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private Tex tex(BufferedImage img) {
        Tex t = texs.get(img);
        if (t == null) {
            t = new TexI(img);
            texs.put(img, t);
        }
        return t;
    }

    private BufferedImage pagicon(FayteRecipes.Recipe r) {
        try {
            Resource res = r.pag.res();
            Resource.Image img = res == null ? null : res.layer(Resource.imgc);
            return img == null ? null : img.img;
        } catch (Loading e) {
            return null;
        }
    }

    private Text fit(String s, Color c, boolean big, int w) {
        Text t = text(s, c, big);
        String cut = s;
        while (t.sz().x > w && cut.length() > 3) {
            cut = cut.substring(0, cut.length() - 1);
            t = text(cut.trim() + "\u2026", c, big);
        }
        return t;
    }

    public int reserve = 0;

    private static String scaled(String c, int n) {
        try {
            return String.valueOf(Integer.parseInt(c.trim()) * n);
        } catch (NumberFormatException e) {
            return n == 1 ? c : c + " \u00d7" + n;
        }
    }

    @Override
    public void draw(GOut g) {
        int rx = LISTW + 8;
        Coord hsz = new Coord(sz.x - rx, HEADH - Button.bh() - 8);
        FayteSkin.box(g, new Coord(rx, 0), hsz, FayteSkin.PANEL, FayteSkin.BORDER);
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.7);
        if (cur != null) {
            int tic = FayteSkin.s(40);
            BufferedImage ic = pagicon(cur);
            if (ic != null) {
                g.image(tex(ic), new Coord(rx + 8, 8), new Coord(tic, tic));
            }
            int hw = sz.x - (rx + tic + 16) - 10 - reserve;
            g.image(fit(cur.name, FayteSkin.TEXT, true, hw).tex(), new Coord(rx + tic + 16, 6));
            String path = cur.path.isEmpty() ? "Crafting" : String.join(" \u203a ", cur.path);
            List<String> sk = FayteRecipes.skillsfor(cur.name);
            if (!sk.isEmpty()) {
                path += "   \u00b7   Skill: " + String.join(", ", sk);
            }
            g.image(fit(path, dim, false, hw).tex(), new Coord(rx + tic + 16, 8 + Window.bigtf.height()));
            int n = count();
            int y0 = tic + 18;
            g.image(
                    text(n > 1 ? "You need, for " + n + ":" : "You need:", HEADC, false)
                            .tex(),
                    new Coord(rx + 8, y0));
            y0 += FayteSkin.labelf.height() + 4;
            if (ingr.isEmpty()) {
                g.image(
                        text(
                                        "Ingredients unknown (no wiki data). The game's crafting window will show"
                                                + " them.",
                                        dim,
                                        false)
                                .tex(),
                        new Coord(rx + 8, y0 + 4));
            }
            updhave();
            List<int[]> chips = new ArrayList<>();
            int cw = FayteSkin.s(170), chh = FayteSkin.s(54), cic = FayteSkin.s(36), vic = FayteSkin.s(14);
            int x = rx + 8, y = y0;
            int maxy = HEADH - Button.bh() - 16 - FayteSkin.labelf.height() - 6;
            int shown = 0;
            for (int k = 0; k < ingr.size(); k++) {
                String[] in = ingr.get(k);
                if (x + cw > sz.x - 6) {
                    x = rx + 8;
                    y += chh + 6;
                }
                if (y + chh > maxy) {
                    g.image(
                            text("+" + (ingr.size() - shown) + " more", dim, false)
                                    .tex(),
                            new Coord(x, y - chh / 2));
                    break;
                }
                String ch = FayteRecipes.choice(cur.name, in[0]);
                List<String> vs = FayteRecipes.variants(in[0]);
                BufferedImage ii = FayteEntries.iconbuf(ch != null ? ch : in[0]);
                FayteRecipes.Have h = k < haves.size() ? haves.get(k) : null;
                int need = -1;
                try {
                    need = Integer.parseInt(in[1].trim()) * n;
                } catch (NumberFormatException e) {
                }
                int have = h == null ? 0 : h.count;
                boolean enough = need >= 0 && have >= need;
                FayteSkin.box(
                        g,
                        new Coord(x, y),
                        new Coord(cw, chh),
                        FayteSkin.mix(FayteSkin.PANEL, enough ? READY : FayteSkin.BORDER, 0.12),
                        ch != null ? READY : FayteSkin.BORDER);
                if (ii != null) {
                    g.image(tex(ii), new Coord(x + 4, y + 4), new Coord(cic, cic));
                } else {
                    FayteSkin.box(g, new Coord(x + 4, y + 4), new Coord(cic, cic), FayteSkin.PANEL, FayteSkin.BORDER);
                    g.aimage(
                            text(in[0].substring(0, 1), FayteSkin.TEXT, false).tex(),
                            new Coord(x + 4 + cic / 2, y + 4 + cic / 2),
                            0.5,
                            0.5);
                }
                int tx = x + cic + 10;
                int tw = cw - cic - 14;
                String nm = ch != null ? ch : in[0];
                Text nt = text(nm, FayteSkin.TEXT, false);
                GOut ng = g.reclip(new Coord(tx, y + 2), new Coord(tw, nt.sz().y));
                ng.image(nt.tex(), Coord.z);
                String nl = "need " + scaled(in[1], n);
                g.image(text(nl, FayteSkin.TEXT, false).tex(), new Coord(tx, y + 3 + FayteSkin.labelf.height()));
                Text ht = text(
                        "have " + have + (h != null && h.aside > 0 ? " (+" + h.aside + ")" : ""),
                        enough ? READY : MISSING,
                        false);
                g.aimage(ht.tex(), new Coord(x + cw - 5, y + 3 + FayteSkin.labelf.height()), 1.0, 0.0);
                if (!vs.isEmpty()) {
                    int vx = tx;
                    int vy = y + chh - vic - 3;
                    for (int v = 0; v < vs.size() && vx + vic <= x + cw - 12; v++) {
                        BufferedImage vi = FayteEntries.iconbuf(vs.get(v));
                        if (vi != null) {
                            g.image(tex(vi), new Coord(vx, vy), new Coord(vic, vic));
                            if (vs.get(v).equals(ch)) {
                                g.chcolor(READY);
                                g.rect(new Coord(vx - 1, vy - 1), new Coord(vic + 2, vic + 2));
                                g.chcolor();
                            }
                            vx += vic + 2;
                        }
                    }
                    g.aimage(text("\u25be", FayteSkin.TEXT, false).tex(), new Coord(x + cw - 4, y + chh - 2), 1.0, 1.0);
                }
                chips.add(new int[] {x, cw, k, y, chh});
                x += cw + 6;
                shown++;
            }
            this.chips = chips;
            int hy = HEADH - Button.bh() - 14 - FayteSkin.labelf.height();
            String only = FayteBagSel.onlydesc();
            if (only != null) {
                Text ot = text("Using selected: " + only + "   [clear]", READY, false);
                g.image(ot.tex(), new Coord(rx + 8, hy));
                onlyx = new int[] {rx + 8, ot.sz().x, hy};
            } else {
                g.image(
                        text(
                                        "Click an \"Any\" ingredient to choose which kind, or use a bag's \u25a1 to"
                                                + " craft with chosen items.",
                                        dim,
                                        false)
                                .tex(),
                        new Coord(rx + 8, hy));
                onlyx = null;
            }
        }
        String st = FayteRecipes.status();
        if (st != null) {
            g.aimage(text(st, FayteText.LINK, false).tex(), new Coord(sz.x - 4, HEADH - Button.bh() / 2 - 6), 1.0, 0.5);
        }
        super.draw(g);
    }

    private static final Color READY = new Color(0x58, 0xC8, 0x60);
    private static final Color MISSING = new Color(0xE0, 0x70, 0x60);
    private static final Color HEADC = new Color(0xE3, 0xA8, 0x4A);
    private List<int[]> chips = new ArrayList<>();
    private List<FayteRecipes.Have> haves = new ArrayList<>();
    private long lasthave = 0L;
    private int[] onlyx = null;

    private void updhave() {
        long now = System.currentTimeMillis();
        if (now - lasthave < 500L) {
            return;
        }
        lasthave = now;
        List<FayteRecipes.Have> l = new ArrayList<>();
        for (String[] in : ingr) {
            l.add(FayteRecipes.have(gui, cur.name, in[0]));
        }
        haves = l;
    }

    private int chipat(Coord c) {
        if (cur == null) {
            return -1;
        }
        for (int[] ch : chips) {
            if (c.x >= ch[0] && c.x < ch[0] + ch[1] && c.y >= ch[3] && c.y < ch[3] + ch[4]) {
                return ch[2];
            }
        }
        return -1;
    }

    private void pickvariant(int i, Coord at) {
        String ing = ingr.get(i)[0];
        List<String> vs = FayteRecipes.variants(ing);
        if (vs.isEmpty()) {
            FayteMsg.say(ing
                    + " has no variants listed. To pick exact items, select them in a bag (\u25a1 in its"
                    + " title) and choose \"Craft with only these\".");
            return;
        }
        List<String> opts = new ArrayList<>();
        opts.add("Any (the game picks)");
        opts.addAll(vs);
        String ch = FayteRecipes.choice(cur.name, ing);
        int sel = ch == null ? 0 : Math.max(0, opts.indexOf(ch));
        String rec = cur.name;
        new FaytePopup(at, this, opts.toArray(new String[0]), sel, (k) -> {
            FayteRecipes.setchoice(rec, ing, k == 0 ? null : opts.get(k));
            lasthave = 0L;
        });
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        int i = chipat(c);
        if (i >= 0 && button == 1) {
            pickvariant(i, c);
            return true;
        }
        if (button == 1
                && onlyx != null
                && c.y >= onlyx[2]
                && c.y < onlyx[2] + FayteSkin.labelf.height() + 2
                && c.x >= onlyx[0]
                && c.x < onlyx[0] + onlyx[1]) {
            FayteBagSel.clearall();
            lasthave = 0L;
            return true;
        }
        return super.mousedown(c, button);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        int i = chipat(c);
        if (i >= 0 && i < ingr.size()) {
            String[] in = ingr.get(i);
            int n = count();
            StringBuilder b = new StringBuilder(in[0] + " \u00d7 " + scaled(in[1], n));
            String ch = FayteRecipes.choice(cur.name, in[0]);
            if (ch != null) {
                b.append("  \u00b7  only ").append(ch);
            }
            FayteRecipes.Have h = i < haves.size() ? haves.get(i) : null;
            if (h != null && h.count > 0) {
                b.append("  \u00b7  in inventory: ").append(h.count);
                if (h.purity() != null) {
                    b.append(", average purity ").append(h.purity());
                }
            }
            if (h != null && h.aside > 0) {
                b.append("  \u00b7  ").append(h.aside).append(" will be set aside");
            }
            if (!FayteRecipes.variants(in[0]).isEmpty()) {
                b.append("  \u00b7  click to choose a variant");
            }
            return b.toString();
        }
        return super.tooltip(c, prev);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        long now = System.currentTimeMillis();
        if (now - lastrefresh > 2000L) {
            lastrefresh = now;
            if (source().size() != lastsize) {
                refilter();
            }
        }
    }

    @Override
    public void destroy() {
        for (Tex t : texs.values()) {
            t.dispose();
        }
        texs.clear();
        super.destroy();
    }

    private class RList extends Widget {
        List<FayteRecipes.Recipe> recs = new ArrayList<>();
        int scroll = 0;

        RList(Coord c, Coord sz) {
            super(c, sz, FayteRecipesPanel.this);
        }

        private int rowh() {
            return FayteText.LIST.line();
        }

        void ensurevisible(FayteRecipes.Recipe r) {
            int i = recs.indexOf(r);
            if (i >= 0) {
                int rows = (sz.y - 8) / rowh();
                if (i < scroll) {
                    scroll = i;
                } else if (i >= scroll + rows) {
                    scroll = i - rows + 1;
                }
            }
        }

        @Override
        public void draw(GOut g) {
            FayteSkin.box(g, Coord.z, sz, FayteSkin.PANEL, FayteSkin.BORDER);
            int rh = rowh();
            int rows = (sz.y - 8) / rh;
            scroll = Math.max(0, Math.min(scroll, Math.max(0, recs.size() - rows)));
            for (int i = 0; i < rows && scroll + i < recs.size(); i++) {
                FayteRecipes.Recipe r = recs.get(scroll + i);
                Coord rc = new Coord(4, 4 + i * rh);
                if (FayteRecipesPanel.this.cur != null && r.res.equals(FayteRecipesPanel.this.cur.res)) {
                    FayteSkin.box(g, rc.sub(2, 0), new Coord(sz.x - 4, rh), FayteSkin.BORDER, null);
                }
                BufferedImage ic = FayteRecipesPanel.this.pagicon(r);
                if (ic != null) {
                    g.image(FayteRecipesPanel.this.tex(ic), rc.add(2, 1), new Coord(rh - 2, rh - 2));
                }
                Text t = FayteRecipesPanel.this.text(r.name, FayteSkin.TEXT, false);
                g.image(t.tex(), rc.add(rh + 4, (rh - t.sz().y) / 2));
                boolean fav = FayteRecipes.fav(r.res);
                Text st = FayteRecipesPanel.this.text(
                        fav ? "\u2605" : "\u2606",
                        fav ? new Color(0xF0, 0xD0, 0x48) : FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.4),
                        false);
                g.aimage(st.tex(), new Coord(sz.x - 8, rc.y + rh / 2), 1.0, 0.5);
            }
        }

        @Override
        public boolean mousedown(Coord c, int button) {
            if (button == 1) {
                int i = scroll + (c.y - 4) / rowh();
                if (c.y >= 4 && i >= 0 && i < recs.size()) {
                    if (c.x >= sz.x - 24) {
                        FayteRecipes.togglefav(recs.get(i).res);
                    } else {
                        FayteRecipesPanel.this.select(recs.get(i));
                    }
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
