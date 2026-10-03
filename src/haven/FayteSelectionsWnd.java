package haven;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteSelectionsWnd extends Window {
    private static final int W = FayteSkin.s(480);
    private static final int ROWH = FayteSkin.s(24);
    private static final int ROWS = 10;
    private static final int BW = FayteSkin.s(40);
    private static FayteSelectionsWnd instance = null;
    private final TextEntry name;
    private final Map<String, Text> texts = new HashMap<>();
    private int scroll = 0;

    private FayteSelectionsWnd(Widget parent) {
        super(
                new Coord(FayteSkin.s(260), FayteSkin.s(150)),
                new Coord(W, ROWS * ROWH + FayteSkin.s(60)),
                parent,
                "Selections");
        justclose = true;
        int y = ROWS * ROWH + 8;
        name = new TextEntry(new Coord(0, y), FayteSkin.s(220), this, "") {
            public void activate(String text) {
                FayteSelectionsWnd.this.add();
            }
        };
        name.clicktotype = true;
        new Button(new Coord(FayteSkin.s(226), y - FayteSkin.s(2)), FayteSkin.s(80), this, "New") {
            public void click() {
                FayteSelectionsWnd.this.add();
            }
        };
        new Label(
                new Coord(0, y + FayteSkin.s(26)),
                this,
                "Edit: left-click/drag adds tiles, right-click/drag removes them. H: height view.");
        FayteTitleButton[] pop = new FayteTitleButton[1];
        pop[0] = new FayteTitleButton(this, "Pop out", "Keep a small list of your selections on screen", () -> {
            FayteSelList.setpopped(!FayteSelList.popped());
            pop[0].sel = FayteSelList.popped();
        });
        pop[0].sel = FayteSelList.popped();
        addtwdg(pop[0]);
        pack();
    }

    public static void toggle(GameUI gui) {
        if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
        } else if (gui != null) {
            instance = new FayteSelectionsWnd(gui);
        }
    }

    private void add() {
        String n = name.text.trim();
        if (n.isEmpty()) {
            n = "Selection " + (FayteSelections.all().size() + 1);
        }
        FayteSelections.Sel s = FayteSelections.add(n);
        name.settext("");
        FayteSelections.startedit(ui.gui, s);
    }

    private Text text(String s, Color c) {
        String k = c.getRGB() + "|" + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 300) {
                texts.clear();
            }
            t = FayteSkin.labelf.render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private void button(GOut g, int x, int y, String label, boolean on) {
        FayteSkin.box(
                g,
                new Coord(x, y + 3),
                new Coord(BW - 4, ROWH - 6),
                on ? FayteSkin.BORDER : FayteSkin.PANEL,
                FayteSkin.BORDER);
        g.aimage(text(label, FayteSkin.TEXT).tex(), new Coord(x + (BW - 4) / 2, y + ROWH / 2), 0.5, 0.5);
    }

    @Override
    public void cdraw(GOut g) {
        List<FayteSelections.Sel> ss = FayteSelections.all();
        scroll = Math.max(0, Math.min(scroll, ss.size() - ROWS));
        int bx = asz.x - (BW * 4);
        if (ss.isEmpty()) {
            g.image(
                    text(
                                    "No selections yet. Name one below and click New.",
                                    FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.6))
                            .tex(),
                    new Coord(4, 4));
        }
        for (int i = 0; i < ROWS && i + scroll < ss.size(); i++) {
            FayteSelections.Sel s = ss.get(i + scroll);
            int y = i * ROWH;
            boolean ed = FayteSelections.editing == s;
            if (ed) {
                FayteSkin.box(
                        g,
                        new Coord(0, y),
                        new Coord(asz.x, ROWH),
                        FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.5),
                        null);
            }
            Color c = FayteSelections.COLORS[s.color];
            g.chcolor(c);
            g.frect(new Coord(4, y + 5), new Coord(14, ROWH - 10));
            g.chcolor();
            g.aimage(
                    text(s.name, s.on ? FayteSkin.TEXT : FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.5))
                            .tex(),
                    new Coord(24, y + ROWH / 2),
                    0.0,
                    0.5);
            String info = (s.height && s.known)
                    ? ("dig " + s.dig + " \u00b7 fill " + s.fill + " \u00b7 ok " + s.level + "  ("
                            + FayteSelections.TARGETS[s.target] + " " + s.tz + ")")
                    : (s.count() + " tiles");
            g.aimage(
                    text(info, FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.7))
                            .tex(),
                    new Coord(bx - 8, y + ROWH / 2),
                    1.0,
                    0.5);
            button(g, bx, y, s.on ? "On" : "Off", s.on);
            button(g, bx + BW, y, "H", s.height);
            button(g, bx + BW * 2, y, ed ? "Done" : "Edit", ed);
            button(g, bx + BW * 3, y, "X", false);
        }
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        Coord p = c.sub(atl);
        List<FayteSelections.Sel> ss = FayteSelections.all();
        int bx = asz.x - (BW * 4);
        if (button == 1 && p.y >= 0 && p.y < ROWS * ROWH) {
            int i = p.y / ROWH + scroll;
            if (i < ss.size()) {
                FayteSelections.Sel s = ss.get(i);
                if (p.x < 22) {
                    FayteSelections.cycle(s);
                    return true;
                } else if (s.height && p.x >= bx - 200 && p.x < bx) {
                    FayteSelections.cycletarget(s);
                    return true;
                } else if (p.x >= bx) {
                    int b = (p.x - bx) / BW;
                    if (b == 0) {
                        FayteSelections.toggle(s);
                    } else if (b == 1) {
                        FayteSelections.toggleheight(s);
                    } else if (b == 2) {
                        if (FayteSelections.editing == s) {
                            FayteSelections.stopedit();
                        } else {
                            FayteSelections.startedit(ui.gui, s);
                        }
                    } else if (b == 3) {
                        FayteSelections.remove(s);
                    }
                    return true;
                }
            }
        }
        return super.mousedown(c, button);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        Coord p = c.sub(atl);
        if (p.x >= 0 && p.x < 22 && p.y >= 0 && p.y < ROWS * ROWH) {
            return "Click to change the color";
        }
        return super.tooltip(c, prev);
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        scroll = Math.max(0, scroll + (amount > 0 ? 1 : -1));
        return true;
    }

    @Override
    public void destroy() {
        FayteSelections.stopedit();
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
