package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class FayteActionsWnd extends Window {
    private static final Coord SZ = new Coord(FayteSkin.s(780), FayteSkin.s(640));
    private static final int SIDEW = FayteSkin.s(130);
    private static final int SECH = FayteSkin.s(28);
    private static final Coord TILE = new Coord(FayteSkin.s(84), FayteSkin.s(62));
    private static final int SLOTPX = FayteSkin.s(30);
    private static final int DOCKROWS = 3;
    private static final int DOCKHEAD = FayteSkin.s(26);
    private static final int BARROW = FayteTitleButton.H + 4 + SLOTPX + 10;
    private static final int BARH = DOCKHEAD + DOCKROWS * BARROW + 18;
    private int barscroll = 0;
    private static final Color LOGIN = new Color(0x58, 0xC8, 0x60);
    private static FayteActionsWnd instance = null;
    private final GameUI gui;
    private final Map<String, Text> texts = new HashMap<>();
    private final List<Widget> dyn = new ArrayList<>();
    private Map<String, List<FayteActs.Act>> secs;
    private String section = Utils.getpref("fayte_act_section", "Toggles");
    private String group = null;
    private int scroll = 0;
    private int bar = 0;
    private String macro = null;
    private String drag = null;
    private int dragfrom = -1;
    private Coord dragat = null;
    private Coord press = null;
    private String pressid = null;
    private int pressslot = -1;
    private long lastrefresh = 0L;

    private FayteActionsWnd(GameUI gui) {
        super(new Coord(160, 80), SZ, gui, "Actions");
        this.gui = gui;
        justclose = true;
        FayteMainBar.seenactions(gui);
        refresh();
        build();
    }

    public static void toggle(GameUI gui) {
        if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
            instance = null;
        } else if (gui != null) {
            instance = new FayteActionsWnd(gui);
        }
    }

    public static void edit(GameUI gui, FayteBars.Bar b) {
        if (instance == null || !instance.attached()) {
            instance = new FayteActionsWnd(gui);
        }
        instance.bar = Math.max(0, FayteBars.bars().indexOf(b));
        instance.barscroll = Math.max(0, instance.bar - DOCKROWS + 1);
        instance.raise();
        instance.build();
    }

    private void refresh() {
        secs = FayteActs.sections(gui);
        if (!secs.containsKey(section)) {
            section = FayteActs.SECTIONS[0];
            group = null;
        }
        lastrefresh = System.currentTimeMillis();
    }

    private Text text(String s, Color c, boolean big) {
        String k = (big ? "b" : "") + c.getRGB() + "|" + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 600) {
                texts.clear();
            }
            t = (big ? Window.bigtf : FayteSkin.labelf).render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private List<String> groups() {
        LinkedHashSet<String> g = new LinkedHashSet<>();
        for (FayteActs.Act a : secs.get(section)) {
            g.add(a.group);
        }
        return new ArrayList<>(g);
    }

    private List<FayteActs.Act> tiles() {
        List<FayteActs.Act> l = new ArrayList<>();
        List<String> gs = groups();
        boolean tabs = gs.size() > 1 && tabbed();
        for (FayteActs.Act a : secs.get(section)) {
            if (!tabs || a.group.equals(group)) {
                l.add(a);
            }
        }
        return l;
    }

    private boolean tabbed() {
        return section.equals("Toggles") || section.equals("Panels") || section.equals("Build");
    }

    private boolean divided() {
        return section.equals("Combat") || section.equals("Music & Emotes") || section.equals("Unsorted");
    }

    private List<Object[]> cells = new ArrayList<>();
    private List<Object[]> heads = new ArrayList<>();
    private int contenth = 0;

    private void layout() {
        List<Object[]> cs = new ArrayList<>();
        List<Object[]> hs = new ArrayList<>();
        int cols = this.cols();
        int y = 0, col = 0;
        String last = null;
        for (FayteActs.Act a : tiles()) {
            if (divided() && !a.group.equals(last)) {
                if (col > 0) {
                    y += TILE.y + 4;
                    col = 0;
                }
                hs.add(new Object[] {a.group.isEmpty() ? "General" : a.group, y});
                y += 22;
                last = a.group;
            }
            cs.add(new Object[] {a, new Coord(col * (TILE.x + 4), y)});
            if (++col >= cols) {
                col = 0;
                y += TILE.y + 4;
            }
        }
        if (col > 0) {
            y += TILE.y + 4;
        }
        cells = cs;
        heads = hs;
        contenth = y;
    }

    private <T extends Widget> T dyn(T w) {
        dyn.add(w);
        return w;
    }

    private void build() {
        for (Widget w : dyn) {
            ui.destroy(w);
        }
        dyn.clear();
        List<String> gs = groups();
        if (tabbed() && gs.size() > 1) {
            if (group == null || !gs.contains(group)) {
                group = gs.get(0);
            }
            int x = SIDEW + 10;
            int y = 0;
            for (String g : gs) {
                final String fg = g;
                FayteTitleButton b = dyn(new FayteTitleButton(
                        this,
                        g.isEmpty() ? "General" : g,
                        "Show " + (g.isEmpty() ? "general" : g.toLowerCase()),
                        () -> {
                            group = fg;
                            scroll = 0;
                            build();
                        }));
                if (x + b.sz.x > SZ.x) {
                    x = SIDEW + 10;
                    y += FayteTitleButton.H + 3;
                }
                b.c = new Coord(x, y);
                b.sel = g.equals(group);
                x += b.sz.x + 4;
            }
            tabsh = y + FayteTitleButton.H + 6;
        } else {
            tabsh = 0;
        }
        List<FayteBars.Bar> bs = FayteBars.bars();
        int by = SZ.y - BARH;
        barscroll = Math.max(0, Math.min(barscroll, Math.max(0, bs.size() - DOCKROWS)));
        int x = SIDEW + 10;
        if (bs.size() < FayteBars.MAXBARS) {
            FayteTitleButton t = dyn(new FayteTitleButton(this, "+ Bar", "Add another bar", () -> {
                FayteBars.Bar nb = FayteBars.add();
                if (nb != null) {
                    bar = FayteBars.bars().indexOf(nb);
                    barscroll = Math.max(0, bar - DOCKROWS + 1);
                }
                build();
            }));
            t.c = new Coord(x, by + 2);
            x += t.sz.x + 4;
        }
        FayteTitleButton gb = dyn(new FayteTitleButton(
                this,
                FayteBars.hideold() ? "Game belts: hidden" : "Game belts: shown",
                "Show or hide the game's own belts",
                () -> {
                    FayteBars.sethideold(!FayteBars.hideold());
                    FayteBars.save();
                    build();
                }));
        gb.c = new Coord(x, by + 2);
        x += gb.sz.x + 4;
        if (bs.size() > DOCKROWS) {
            FayteTitleButton up = dyn(new FayteTitleButton(this, "\u25b2", "Show earlier bars (or scroll here)", () -> {
                barscroll--;
                build();
            }));
            up.c = new Coord(x, by + 2);
            x += up.sz.x + 2;
            FayteTitleButton dn = dyn(new FayteTitleButton(this, "\u25bc", "Show later bars (or scroll here)", () -> {
                barscroll++;
                build();
            }));
            dn.c = new Coord(x, by + 2);
        }
        for (int bi = barscroll; bi < bs.size() && bi < barscroll + DOCKROWS; bi++) {
            final FayteBars.Bar b = bs.get(bi);
            int ry = by + DOCKHEAD + (bi - barscroll) * BARROW;
            String[] labels = {
                b.name,
                "Shape: " + b.cols() + "\u00d7" + b.rows(),
                "Size: " + FayteBars.SIZES[b.size],
                "Keys: " + FayteBars.KEYSETS[b.keys],
                b.shown ? "Shown" : "Hidden",
                "Clear",
            };
            String[] tips = {
                "Rename this bar",
                "How the bar is laid out on screen (click to change)",
                "How big the bar's slots are on screen",
                "Which keys use this bar's slots",
                "Show or hide this bar on screen",
                "Empty every slot of this bar",
            };
            final Coord[] keysat = new Coord[1];
            Runnable[] acts = {
                () -> new FayteAsk(gui, "Rename bar", "Name for this bar.", b.name, (n) -> {
                    if (!n.isEmpty()) {
                        b.name = n;
                        FayteBars.save();
                        build();
                    }
                }),
                () -> b.form = (b.form + 1) % FayteBars.FORMS.length,
                () -> b.size = (b.size + 1) % FayteBars.SIZES.length,
                () -> new FaytePopup(keysat[0], this, FayteBars.KEYSETS, b.keys, (k) -> {
                    FayteBars.setkeys(b, k);
                    build();
                }),
                () -> b.shown = !b.shown,
                () -> {
                    for (int k = 0; k < FayteBars.SLOTS; k++) {
                        b.slots[k] = null;
                    }
                },
            };
            int cx = SIDEW + 10;
            for (int k = 0; k < labels.length; k++) {
                final Runnable r = acts[k];
                FayteTitleButton t = dyn(new FayteTitleButton(this, labels[k], tips[k], () -> {
                    r.run();
                    FayteBars.save();
                    build();
                }));
                t.c = new Coord(cx, ry);
                t.sel = k == 0;
                if (k == 3) {
                    keysat[0] = new Coord(cx, ry + FayteTitleButton.H + 2);
                }
                cx += t.sz.x + 4;
            }
        }
        if (section.equals("Macros")) {
            macrobuttons();
        }
    }

    private int tabsh = 0;

    private void macrobuttons() {
        int x = SIDEW + 10;
        int y = SZ.y - BARH - 30;
        String[] labels = {"New macro", "Rename", "Delete", "+ Text", "+ Command", "+ Wait", "+ Equip", "Run"};
        for (int i = 0; i < labels.length; i++) {
            final int fi = i;
            dyn(new Button(new Coord(x, y), 84, this, labels[i]) {
                @Override
                public void click() {
                    FayteActionsWnd.this.macroact(fi);
                }
            });
            x += 88;
        }
    }

    private void macroact(int i) {
        FayteMacros.Macro m = macro == null ? null : FayteMacros.get(macro);
        switch (i) {
            case 0:
                new FayteAsk(gui, "New macro", "Name the macro. Add actions with right-click on any tile.", "", (n) -> {
                    FayteMacros.Macro nm = FayteMacros.create(n.isEmpty() ? "Macro" : n);
                    macro = nm.id;
                    refresh();
                    build();
                });
                break;
            case 1:
                if (m != null) {
                    new FayteAsk(gui, "Rename macro", "New name.", m.name, (n) -> {
                        if (!n.isEmpty()) {
                            m.name = n;
                            FayteMacros.save();
                            refresh();
                        }
                    });
                }
                break;
            case 2:
                if (m != null) {
                    FayteMacros.remove(m);
                    macro = null;
                    refresh();
                }
                break;
            case 3:
                if (m != null) {
                    new FayteAsk(
                            gui,
                            "Say text",
                            "Sent to the chat channel you have selected.",
                            "",
                            (t) -> addstep(m, "say", t));
                }
                break;
            case 4:
                if (m != null) {
                    new FayteAsk(gui, "Console command", "For example: almanac", "", (t) -> addstep(m, "cmd", t));
                }
                break;
            case 5:
                if (m != null) {
                    new FayteAsk(gui, "Wait", "Seconds to wait.", "1", (t) -> {
                        try {
                            FayteMacros.Step s = new FayteMacros.Step("wait", "", (int) (Double.parseDouble(t) * 1000));
                            m.steps.add(s);
                            FayteMacros.save();
                        } catch (NumberFormatException e) {
                        }
                    });
                }
                break;
            case 6:
                if (m != null) {
                    new FayteAsk(
                            gui,
                            "Equip from belt",
                            "Item to put in your hand, for example: sword. What you were holding goes where it was.",
                            "",
                            (t) -> addstep(m, "equip", t.trim()));
                }
                break;
            default:
                if (m != null) {
                    FayteMacros.start(gui, m.id);
                }
        }
    }

    private void addstep(FayteMacros.Macro m, String kind, String v) {
        if (!v.isEmpty()) {
            m.steps.add(new FayteMacros.Step(kind, v, 500));
            FayteMacros.save();
        }
    }

    private Coord gridc() {
        return new Coord(SIDEW + 10, tabsh);
    }

    private int gridh() {
        int h = SZ.y - BARH - tabsh - 6;
        if (section.equals("Macros")) {
            h = 2 * (TILE.y + 4);
        }
        return h;
    }

    private int cols() {
        return Math.max(1, (SZ.x - SIDEW - 10) / (TILE.x + 4));
    }

    private FayteActs.Act tileat(Coord p) {
        Coord g = gridc();
        if (p.x < g.x || p.y < g.y || p.y >= g.y + gridh()) {
            return null;
        }
        for (Object[] c : cells) {
            Coord tc = g.add((Coord) c[1]).sub(0, scroll);
            if (p.isect(tc, TILE)) {
                return (FayteActs.Act) c[0];
            }
        }
        return null;
    }

    private Coord slotc(int bi, int i) {
        int ry = SZ.y - BARH + DOCKHEAD + (bi - barscroll) * BARROW + FayteTitleButton.H + 4;
        return new Coord(SIDEW + 10 + i * (SLOTPX + 2), ry);
    }

    private int slotat(Coord p) {
        List<FayteBars.Bar> bs = FayteBars.bars();
        for (int bi = barscroll; bi < bs.size() && bi < barscroll + DOCKROWS; bi++) {
            for (int i = 0; i < FayteBars.SLOTS; i++) {
                if (p.isect(slotc(bi, i), new Coord(SLOTPX, SLOTPX))) {
                    return bi * 100 + i;
                }
            }
        }
        return -1;
    }

    private int stepat(Coord p) {
        int y0 = gridc().y + gridh() + 4;
        if (p.x < SIDEW + 10 || p.y < y0) {
            return -1;
        }
        int i = (p.y - y0) / 20;
        FayteMacros.Macro m = macro == null ? null : FayteMacros.get(macro);
        return m != null && i >= 0 && i < m.steps.size() && p.y < SZ.y - BARH - 34 ? i : -1;
    }

    @Override
    public void cdraw(GOut g) {
        if (System.currentTimeMillis() - lastrefresh > 5000L && drag == null) {
            refresh();
        }
        FayteSkin.box(g, Coord.z, new Coord(SIDEW, SZ.y), FayteSkin.PANEL, FayteSkin.BORDER);
        for (int i = 0; i < FayteActs.SECTIONS.length; i++) {
            String s = FayteActs.SECTIONS[i];
            int y = 4 + i * SECH;
            if (s.equals(section)) {
                FayteSkin.box(g, new Coord(3, y), new Coord(SIDEW - 6, SECH - 2), FayteSkin.BORDER, null);
            }
            int n = secs.get(s).size();
            g.aimage(
                    text(s, n == 0 ? FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.5) : FayteSkin.TEXT, true)
                            .tex(),
                    new Coord(10, y + SECH / 2 - 1),
                    0.0,
                    0.5);
        }
        layout();
        Coord gc = gridc();
        int gh = gridh();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contenth - gh)));
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.6);
        if (cells.isEmpty()) {
            String msg = section.equals("Macros") ? "No macros yet. Click New macro." : "Nothing here.";
            g.image(text(msg, dim, false).tex(), gc.add(4, 6));
        }
        for (Object[] h : heads) {
            int hy = gc.y + (Integer) h[1] - scroll;
            if (hy >= gc.y && hy + 20 <= gc.y + gh) {
                g.image(text((String) h[0], FayteSkin.TEXT, true).tex(), new Coord(gc.x + 2, hy));
                g.chcolor(FayteSkin.BORDER);
                g.frect(new Coord(gc.x, hy + 19), new Coord(SZ.x - gc.x - 8, 1));
                g.chcolor();
            }
        }
        for (Object[] cell : cells) {
            {
                FayteActs.Act a = (FayteActs.Act) cell[0];
                Coord tc = gc.add((Coord) cell[1]).sub(0, scroll);
                if (tc.y < gc.y || tc.y + TILE.y > gc.y + gh) {
                    continue;
                }
                boolean selm = a.id.equals("macro:" + macro);
                FayteSkin.box(g, tc, TILE, selm ? FayteSkin.BORDER : FayteSkin.PANEL, FayteSkin.BORDER);
                int lh = FayteSkin.labelf.height() + 6;
                int isz = Math.min(FayteSkin.s(34), TILE.y - lh - 8);
                int iy = Math.max(4, (TILE.y - lh - isz) / 2);
                Tex ic = FayteActs.icon(a.id);
                if (ic != null) {
                    g.image(ic, tc.add(TILE.x / 2 - isz / 2, iy), new Coord(isz, isz));
                } else {
                    g.aimage(
                            text(a.name.isEmpty() ? "?" : a.name.substring(0, 1).toUpperCase(), FayteSkin.TEXT, true)
                                    .tex(),
                            tc.add(TILE.x / 2, iy + isz / 2),
                            0.5,
                            0.5);
                }
                String nm = a.name;
                Text nt = text(nm, FayteSkin.TEXT, false);
                while (nt.sz().x > TILE.x - 6 && nm.length() > 3) {
                    nm = nm.substring(0, nm.length() - 2);
                    nt = text(nm + "\u2026", FayteSkin.TEXT, false);
                }
                g.aimage(nt.tex(), tc.add(TILE.x / 2, TILE.y - 4), 0.5, 1.0);
                if (FayteActs.atstart(a.id)) {
                    g.chcolor(LOGIN);
                    g.frect(tc.add(3, 3), new Coord(7, 7));
                    g.chcolor();
                }
            }
        }
        if (section.equals("Macros")) {
            drawmacro(g, dim);
        }
        int by = SZ.y - BARH;
        g.chcolor(FayteSkin.BORDER);
        g.frect(new Coord(SIDEW + 6, by), new Coord(SZ.x - SIDEW - 6, 1));
        g.chcolor();
        List<FayteBars.Bar> dbs = FayteBars.bars();
        for (int bi = barscroll; bi < dbs.size() && bi < barscroll + DOCKROWS; bi++) {
            FayteBars.Bar b = dbs.get(bi);
            for (int i = 0; i < FayteBars.SLOTS; i++) {
                Coord sc = slotc(bi, i);
                int enc = bi * 100 + i;
                FayteBars.drawslot(
                        g,
                        sc,
                        SLOTPX,
                        (dragfrom == enc && drag != null) ? null : b.slots[i],
                        FayteBars.keylabel(b, i),
                        drag != null && dragat != null && dragat.isect(sc, new Coord(SLOTPX, SLOTPX)));
            }
        }
        g.image(
                text(
                                "Drag tiles into any bar, or between bars. Right-click a slot to empty it; right-click"
                                        + " a tile for more.",
                                dim,
                                false)
                        .tex(),
                new Coord(SIDEW + 10, SZ.y - 16));
        if (drag != null && dragat != null) {
            Tex ic = FayteActs.icon(drag);
            if (ic != null) {
                g.image(ic, dragat.sub(16, 16), new Coord(32, 32));
            } else {
                g.aimage(text(FayteActs.name(gui, drag), FayteSkin.TEXT, false).tex(), dragat, 0.5, 0.5);
            }
        }
    }

    private void drawmacro(GOut g, Color dim) {
        FayteMacros.Macro m = macro == null ? null : FayteMacros.get(macro);
        int y0 = gridc().y + gridh() + 4;
        if (m == null) {
            g.image(
                    text("Pick a macro above (click its tile) to edit its steps.", dim, false)
                            .tex(),
                    new Coord(SIDEW + 12, y0));
            return;
        }
        g.image(
                text(
                                m.name
                                        + (m.steps.isEmpty()
                                                ? ": no steps yet. Right-click any tile \u2192 Add to macro."
                                                : ""),
                                FayteSkin.TEXT,
                                true)
                        .tex(),
                new Coord(SIDEW + 12, y0 - 2));
        for (int i = 0; i < m.steps.size(); i++) {
            FayteMacros.Step s = m.steps.get(i);
            int y = y0 + 20 + i * 20;
            if (y > SZ.y - BARH - 50) {
                break;
            }
            g.image(
                    text((i + 1) + ". " + FayteMacros.describe(gui, s), FayteSkin.TEXT, false)
                            .tex(),
                    new Coord(SIDEW + 16, y));
            g.aimage(
                    text("then wait " + (s.delay / 1000.0) + " s   [\u2191] [\u2193] [x]", dim, false)
                            .tex(),
                    new Coord(SZ.x - 8, y),
                    1.0,
                    0.0);
        }
    }

    private void tilemenu(FayteActs.Act a, Coord at) {
        List<String> opts = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        opts.add("Use");
        acts.add(() -> FayteActs.run(gui, a.id));
        for (final FayteBars.Bar b : FayteBars.bars()) {
            opts.add("Put on " + b.name);
            acts.add(() -> {
                for (int i = 0; i < FayteBars.SLOTS; i++) {
                    if (b.slots[i] == null) {
                        b.slots[i] = a.id;
                        FayteBars.save();
                        return;
                    }
                }
                FayteMsg.say(b.name + " is full.");
            });
        }
        FayteMacros.Macro m = macro == null ? null : FayteMacros.get(macro);
        if (m != null && !a.id.equals("macro:" + m.id)) {
            opts.add("Add to macro " + m.name);
            acts.add(() -> {
                m.steps.add(new FayteMacros.Step("act", a.id, 500));
                FayteMacros.save();
            });
            if (!a.id.startsWith("macro:")) {
                opts.add("Use this icon for " + m.name);
                acts.add(() -> {
                    m.icon = a.id;
                    FayteMacros.save();
                });
            }
        }
        if (FayteActs.toggleable(a.id) || section.equals("Toggles")) {
            boolean on = FayteActs.atstart(a.id);
            opts.add(on ? "Don't turn on at login" : "Turn on at login");
            acts.add(() -> FayteActs.setstart(a.id, !on));
        }
        new FaytePopup(
                at, this, opts.toArray(new String[0]), -1, (i) -> acts.get(i).run());
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        Coord p = c.sub(atl);
        if (p.x >= 0 && p.x < SIDEW && p.y >= 4 && button == 1) {
            int i = (p.y - 4) / SECH;
            if (i >= 0 && i < FayteActs.SECTIONS.length) {
                section = FayteActs.SECTIONS[i];
                Utils.setpref("fayte_act_section", section);
                scroll = 0;
                group = null;
                build();
                return true;
            }
        }
        FayteActs.Act ta = tileat(p);
        if (ta != null) {
            FayteActs.Act a = ta;
            if (button == 3) {
                tilemenu(a, p);
            } else if (button == 1) {
                press = p;
                pressid = a.id;
                pressslot = -1;
                ui.grabmouse(this);
            }
            return true;
        }
        int enc = slotat(p);
        if (enc >= 0) {
            FayteBars.Bar b = FayteBars.bars().get(enc / 100);
            int s = enc % 100;
            bar = enc / 100;
            if (button == 3) {
                b.slots[s] = null;
                FayteBars.save();
            } else if (button == 1 && b.slots[s] != null) {
                press = p;
                pressid = b.slots[s];
                pressslot = enc;
                ui.grabmouse(this);
            }
            return true;
        }
        int st = stepat(p);
        if (st >= 0 && button == 1) {
            FayteMacros.Macro m = FayteMacros.get(macro);
            int rx = SZ.x - 8;
            if (p.x > rx - 30) {
                m.steps.remove(st);
            } else if (p.x > rx - 60 && st < m.steps.size() - 1) {
                m.steps.add(st + 1, m.steps.remove(st));
            } else if (p.x > rx - 90 && st > 0) {
                m.steps.add(st - 1, m.steps.remove(st));
            } else {
                FayteMacros.Step fs = m.steps.get(st);
                new FayteAsk(
                        gui,
                        "Delay after this step",
                        "Seconds to wait after this step.",
                        Double.toString(fs.delay / 1000.0),
                        (v) -> {
                            try {
                                fs.delay = (int) (Double.parseDouble(v) * 1000);
                                FayteMacros.save();
                            } catch (NumberFormatException e) {
                            }
                        });
            }
            FayteMacros.save();
            return true;
        }
        return super.mousedown(c, button);
    }

    @Override
    public void mousemove(Coord c) {
        Coord p = c.sub(atl);
        if (press != null && drag == null && p.dist(press) > 5) {
            drag = pressid;
            dragfrom = pressslot;
        }
        if (drag != null) {
            dragat = p;
        }
        super.mousemove(c);
    }

    @Override
    public boolean mouseup(Coord c, int button) {
        if (button == 1 && press != null) {
            Coord p = c.sub(atl);
            ui.grabmouse(null);
            if (drag != null) {
                List<FayteBars.Bar> bs = FayteBars.bars();
                int enc = slotat(p);
                FayteBars.Bar src = dragfrom >= 0 ? bs.get(dragfrom / 100) : null;
                int ss = dragfrom >= 0 ? dragfrom % 100 : -1;
                if (enc >= 0) {
                    FayteBars.Bar tb = bs.get(enc / 100);
                    int ts = enc % 100;
                    String old = tb.slots[ts];
                    tb.slots[ts] = drag;
                    if (src != null && enc != dragfrom) {
                        src.slots[ss] = old;
                    }
                } else if (src != null) {
                    src.slots[ss] = null;
                }
                FayteBars.save();
            } else if (pressid != null) {
                if (pressid.startsWith("macro:") && section.equals("Macros")) {
                    macro = pressid.substring(6);
                } else {
                    FayteActs.run(gui, pressid);
                }
            }
            press = null;
            pressid = null;
            drag = null;
            dragfrom = -1;
            dragat = null;
            return true;
        }
        return super.mouseup(c, button);
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        Coord p = c.sub(atl);
        if (p.y >= SZ.y - BARH && FayteBars.bars().size() > DOCKROWS) {
            barscroll += amount > 0 ? 1 : -1;
            build();
            return true;
        }
        scroll += (amount > 0 ? 1 : -1) * (TILE.y + 4);
        return true;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        Coord p = c.sub(atl);
        FayteActs.Act a = tileat(p);
        if (a != null) {
            return a.name + (FayteActs.atstart(a.id) ? " (turns on at login)" : "")
                    + "  \u2014 click to use, drag to a bar slot, right-click for more";
        }
        int st = stepat(p);
        if (st >= 0) {
            return "Click the text to change the delay; arrows move the step; x removes it";
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
