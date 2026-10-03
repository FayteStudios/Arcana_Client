package haven;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteBars {
    public static final int SLOTS = 12;
    public static final int MAXBARS = 6;
    public static final int[][] FORMS = {{12, 1}, {6, 2}, {4, 3}, {3, 4}, {2, 6}, {1, 12}};
    public static final String[] SIZES = {"Small", "Medium", "Large", "Huge", "Giant"};
    public static final int[] PX = {26, 34, 44, 56, 70};
    public static final String[] KEYSETS = {
        "No keys",
        "1 \u2013 =",
        "F1 \u2013 F12",
        "Shift + 1 \u2013 =",
        "Ctrl + 1 \u2013 =",
        "Alt + 1 \u2013 =",
        "Ctrl + F1 \u2013 F12",
        "Alt + F1 \u2013 F12"
    };
    private static final int[] KSMODS = {0, 0, 0, 1, 2, 4, 2, 4};
    private static final boolean[] KSFKEY = {false, false, true, false, false, false, true, true};
    private static final int[] NUMKEYS = {
        KeyEvent.VK_1,
        KeyEvent.VK_2,
        KeyEvent.VK_3,
        KeyEvent.VK_4,
        KeyEvent.VK_5,
        KeyEvent.VK_6,
        KeyEvent.VK_7,
        KeyEvent.VK_8,
        KeyEvent.VK_9,
        KeyEvent.VK_0,
        KeyEvent.VK_MINUS,
        KeyEvent.VK_EQUALS
    };
    private static final String[] NUMLBL = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "="};
    private static Store store = null;

    public static synchronized void reload() {
        store = null;
    }

    public static File datafile() {
        return file();
    }

    private static final List<Live> lives = new ArrayList<>();

    public static class Bar {
        public String name;
        public int form = 0;
        public int size = 1;
        public int keys = 0;
        public boolean shown = true;
        public String[] slots = new String[SLOTS];

        public int cols() {
            return FORMS[Math.max(0, Math.min(FORMS.length - 1, form))][0];
        }

        public int rows() {
            return FORMS[Math.max(0, Math.min(FORMS.length - 1, form))][1];
        }

        public int px() {
            return PX[Math.max(0, Math.min(PX.length - 1, size))];
        }

        public Coord sz() {
            return new Coord(cols() * (px() + 2) + 2, rows() * (px() + 2) + 2);
        }
    }

    public static class Store {
        public List<Bar> bars = new ArrayList<>();
    }

    private static File file() {
        return new File(FaytePaths.fayte(), "bars.json");
    }

    public static synchronized Store store() {
        if (store == null) {
            File f = file();
            if (f.exists()) {
                try {
                    store = new Gson()
                            .fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), Store.class);
                } catch (Exception e) {
                    FayteLog.log("Bars: could not read " + f + ": " + e);
                }
            }
            if (store == null || store.bars == null || store.bars.isEmpty()) {
                store = new Store();
                for (int i = 0; i < 3; i++) {
                    Bar b = new Bar();
                    b.name = "Bar " + (i + 1);
                    b.shown = i == 0;
                    b.keys = i == 0 ? 1 : (i == 1 ? 2 : 0);
                    store.bars.add(b);
                }
            }
            for (Bar b : store.bars) {
                if (b.slots == null || b.slots.length != SLOTS) {
                    String[] n = new String[SLOTS];
                    if (b.slots != null) {
                        System.arraycopy(b.slots, 0, n, 0, Math.min(SLOTS, b.slots.length));
                    }
                    b.slots = n;
                }
            }
        }
        return store;
    }

    public static synchronized void save() {
        File f = file();
        try {
            f.getParentFile().mkdirs();
            FaytePaths.write(
                    f,
                    new GsonBuilder()
                            .setPrettyPrinting()
                            .create()
                            .toJson(store())
                            .getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.log("Bars: could not write " + f + ": " + e);
        }
    }

    public static List<Bar> bars() {
        return store().bars;
    }

    public static Bar add() {
        if (bars().size() >= MAXBARS) {
            return null;
        }
        Bar b = new Bar();
        b.name = "Bar " + (bars().size() + 1);
        bars().add(b);
        save();
        return b;
    }

    public static String keylabel(Bar b, int slot) {
        if (b.keys <= 0 || b.keys >= KEYSETS.length) {
            return null;
        }
        String k = KSFKEY[b.keys] ? "F" + (slot + 1) : NUMLBL[slot];
        int m = KSMODS[b.keys];
        return (m == 1 ? "\u21e7" : (m == 2 ? "^" : (m == 4 ? "A" : ""))) + k;
    }

    public static void setkeys(Bar b, int keys) {
        if (keys != 0) {
            for (Bar o : bars()) {
                if (o != b && o.keys == keys) {
                    o.keys = b.keys;
                }
            }
        }
        b.keys = keys;
        save();
    }

    public static boolean hideold() {
        return Utils.getprefb("fayte_hide_oldbelts", true);
    }

    public static void sethideold(boolean v) {
        Utils.setprefb("fayte_hide_oldbelts", v);
    }

    private static boolean texting(UI ui) {
        if (ui.keygrabbed()) {
            return true;
        }
        Widget f = ui.root;
        while (f != null && f.focused != null) {
            f = f.focused;
        }
        return f instanceof TextEntry || f instanceof FayteTextArea;
    }

    public static boolean keydown(UI ui, KeyEvent ev) {
        if (ui.gui == null || texting(ui)) {
            return false;
        }
        int code = ev.getKeyCode();
        int mods = (ev.isShiftDown() ? 1 : 0) | (ev.isControlDown() ? 2 : 0) | (ev.isAltDown() ? 4 : 0);
        for (Bar b : bars()) {
            if (!b.shown || b.keys <= 0 || b.keys >= KEYSETS.length) {
                continue;
            }
            for (int i = 0; i < SLOTS; i++) {
                int want = KSFKEY[b.keys] ? KeyEvent.VK_F1 + i : NUMKEYS[i];
                boolean hit = code == want && mods == KSMODS[b.keys];

                if (hit) {
                    if (b.slots[i] != null) {
                        FayteActs.run(ui.gui, b.slots[i]);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public static void sync(GameUI gui) {
        boolean hide = hideold();
        for (ToolBeltWdg tb : new ToolBeltWdg[] {gui.fbelt, gui.nbelt}) {
            if (tb != null && tb.visible == hide) {
                tb.show(!hide);
            }
        }
        List<Bar> bs = bars();
        for (Live l : new ArrayList<>(lives)) {
            if (l.parent != gui || !bs.contains(l.bar) || !l.bar.shown) {
                if (l.attached()) {
                    gui.ui.destroy(l);
                }
                lives.remove(l);
            }
        }
        for (int i = 0; i < bs.size(); i++) {
            Bar b = bs.get(i);
            if (!b.shown) {
                continue;
            }
            boolean have = false;
            for (Live l : lives) {
                if (l.bar == b) {
                    have = true;
                }
            }
            if (!have) {
                Live l = new Live(gui, b, new Coord(gui.sz.x / 2 - b.sz().x / 2, gui.sz.y - 60 - i * 56));
                lives.add(FayteHud.reg(l, "bar" + (i + 1)));
            }
        }
    }

    private static final Map<String, Text> tcache = new HashMap<>();

    private static Text txt(String s, Color c) {
        String k = c.getRGB() + "|" + s;
        Text t = tcache.get(k);
        if (t == null) {
            if (tcache.size() > 300) {
                tcache.clear();
            }
            t = FayteSkin.labelf.render(s, c);
            tcache.put(k, t);
        }
        return t;
    }

    public static void drawslot(GOut g, Coord c, int px, String id, String key, boolean hl) {
        FayteSkin.box(
                g,
                c,
                new Coord(px, px),
                hl ? FayteSkin.HOVER : FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.25),
                FayteSkin.BORDER);
        if (id != null) {
            Tex t = FayteActs.icon(id);
            if (t != null) {
                g.image(t, c.add(2, 2), new Coord(px - 4, px - 4));
            } else {
                String n = FayteActs.name(UI.instance == null ? null : UI.instance.gui, id);
                Text lt = txt(n.length() > 3 ? n.substring(0, 3) : n, FayteSkin.TEXT);
                g.aimage(lt.tex(), c.add(px / 2, px / 2), 0.5, 0.5);
            }
        }
        if (key != null) {
            Text kt = txt(key, new Color(0xF0, 0xEB, 0xDD));
            g.chcolor(0, 0, 0, 150);
            g.frect(c.add(px - kt.sz().x - 3, px - kt.sz().y - 1), kt.sz().add(2, 0));
            g.chcolor();
            g.image(kt.tex(), c.add(px - kt.sz().x - 2, px - kt.sz().y - 1));
        }
    }

    public static class Live extends Widget {
        public final Bar bar;
        private int hover = -1;

        Live(GameUI gui, Bar bar, Coord c) {
            super(c, bar.sz(), gui);
            this.bar = bar;
        }

        private int slotat(Coord c) {
            int px = bar.px() + 2;
            int col = (c.x - 1) / px, row = (c.y - 1) / px;
            if (c.x < 1 || c.y < 1 || col >= bar.cols() || row >= bar.rows()) {
                return -1;
            }
            return row * bar.cols() + col;
        }

        @Override
        public void draw(GOut g) {
            if (!sz.equals(bar.sz())) {
                sz = bar.sz();
            }
            int px = bar.px();
            for (int i = 0; i < SLOTS; i++) {
                int col = i % bar.cols(), row = i / bar.cols();
                drawslot(
                        g,
                        new Coord(1 + col * (px + 2), 1 + row * (px + 2)),
                        px,
                        bar.slots[i],
                        keylabel(bar, i),
                        i == hover);
            }
        }

        @Override
        public void mousemove(Coord c) {
            hover = slotat(c);
        }

        @Override
        public boolean mousedown(Coord c, int button) {
            int i = slotat(c);
            if (i >= 0 && button == 1 && !FayteHud.editing()) {
                if (bar.slots[i] != null) {
                    FayteActs.run(getparent(GameUI.class), bar.slots[i]);
                }
                return true;
            } else if (i >= 0 && button == 3) {
                FayteActionsWnd.edit(getparent(GameUI.class), bar);
                return true;
            }
            return false;
        }

        @Override
        public Object tooltip(Coord c, Widget prev) {
            int i = slotat(c);
            if (i >= 0 && bar.slots[i] != null) {
                return FayteActs.name(getparent(GameUI.class), bar.slots[i]);
            }
            return i >= 0 ? "Empty. Right-click to edit this bar in Actions." : null;
        }
    }
}
