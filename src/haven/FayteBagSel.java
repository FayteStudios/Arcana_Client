package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;

public class FayteBagSel {
    public static final Color SEL = new Color(0x58, 0xC8, 0x60, 110);
    public static final Color SELEDGE = new Color(0x58, 0xC8, 0x60, 230);
    public static final Color DRAG = new Color(0xF0, 0xEB, 0xDD, 60);
    private static final Map<Inventory, Boolean> modes = new WeakHashMap<>();
    private static final Map<GItem, String> sel = new WeakHashMap<>();

    static void reset() {
        sel.clear();
        modes.clear();
        draginv = null;
    }

    private static Inventory draginv = null;
    private static Coord dragstart = null;
    private static Coord dragcur = null;
    private static int dragbtn = 0;

    public static boolean on(Inventory inv) {
        return Boolean.TRUE.equals(modes.get(inv));
    }

    public static void setmode(Inventory inv, boolean on) {
        modes.put(inv, on);
        if (!on) {
            for (WItem w : FayteXfer.items(inv)) {
                sel.remove(w.item);
            }
        }
    }

    private static void add(WItem w) {
        String n = FayteSort.name(w);
        if (n != null && !n.isEmpty()) {
            sel.put(w.item, n);
        }
    }

    private static boolean toggle(WItem w) {
        if (sel.containsKey(w.item)) {
            sel.remove(w.item);
            return false;
        }
        add(w);
        return true;
    }

    public static boolean isselected(GItem g) {
        return sel.containsKey(g);
    }

    public static boolean selected(WItem w) {
        return sel.containsKey(w.item);
    }

    public static List<WItem> selected(Inventory inv) {
        List<WItem> l = new ArrayList<>();
        for (WItem w : FayteXfer.items(inv)) {
            if (sel.containsKey(w.item)) {
                l.add(w);
            }
        }
        return l;
    }

    public static List<Inventory> inventories(Widget root) {
        List<Inventory> l = new ArrayList<>();
        collect(root, l);
        return l;
    }

    private static void collect(Widget w, List<Inventory> into) {
        for (Widget c = w.child; c != null; c = c.next) {
            if (c instanceof Inventory && c.visible && c.parent != null && c.parent.visible) {
                into.add((Inventory) c);
            }
            collect(c, into);
        }
    }

    public static void button(Inventory inv, Window win) {
        FayteTitleButton[] b = new FayteTitleButton[1];
        b[0] = new FayteTitleButton(
                win,
                "\u25a1",
                "Select items: drag to select (left adds, right removes). Click here for actions, right-click to stop"
                        + " selecting.",
                () -> {
                    if (!on(inv)) {
                        setmode(inv, true);
                        b[0].sel = true;
                        FayteMsg.say("Selecting in " + (win.cap == null ? "this bag" : win.cap.text)
                                + ": drag or click items (left adds, right removes). Click the \u25a1 button"
                                + " again for actions.");
                        return;
                    }
                    actions(inv, b[0]);
                });
        b[0].rclick = () -> {
            if (on(inv)) {
                setmode(inv, false);
                b[0].sel = false;
            }
        };
        win.addtwdg(b[0]);
    }

    private static void actions(Inventory inv, FayteTitleButton b) {
        List<WItem> s = selected(inv);
        String[] opts = {
            "Transfer selected (" + s.size() + ")",
            "Drop selected (" + s.size() + ")",
            "Select all of the same kind",
            "Clear selection",
            "Stop selecting",
        };
        new FaytePopup(b.rootpos().add(0, b.sz.y), inv.ui.root, opts, -1, (i) -> {
            List<WItem> cur = selected(inv);
            switch (i) {
                case 0:
                    for (WItem w : cur) {
                        w.item.wdgmsg("transfer", Coord.z);
                    }
                    break;
                case 1:
                    for (WItem w : cur) {
                        w.item.wdgmsg("drop", Coord.z);
                    }
                    break;
                case 2:
                    Set<String> names = new HashSet<>();
                    for (WItem w : cur) {
                        names.add(FayteSort.name(w));
                    }
                    for (WItem w : FayteXfer.items(inv)) {
                        if (names.contains(FayteSort.name(w))) {
                            add(w);
                        }
                    }
                    break;
                case 3:
                    for (WItem w : FayteXfer.items(inv)) {
                        sel.remove(w.item);
                    }
                    break;
                default:
                    setmode(inv, false);
                    b.sel = false;
            }
        });
    }

    public static synchronized Set<String> selnames() {
        Set<String> s = new HashSet<>(sel.values());
        s.remove("");
        return s;
    }

    public static synchronized void clearall() {
        sel.clear();
    }

    public static synchronized boolean hasonly() {
        return !sel.isEmpty();
    }

    public static synchronized String onlydesc() {
        if (sel.isEmpty()) {
            return null;
        }
        return sel.size() + " selected " + String.join(", ", new TreeSet<>(sel.values()));
    }

    public static synchronized boolean excluded(WItem w, String name) {
        return !sel.containsKey(w.item) && sel.containsValue(name);
    }

    private static WItem at(Inventory inv, Coord c) {
        for (WItem w : FayteXfer.items(inv)) {
            if (c.isect(w.c, w.sz)) {
                return w;
            }
        }
        return null;
    }

    public static boolean mousedown(Inventory inv, Coord c, int button) {
        if (!on(inv) || (button != 1 && button != 3)) {
            return false;
        }
        draginv = inv;
        dragstart = c;
        dragcur = c;
        dragbtn = button;
        inv.ui.grabmouse(inv);
        return true;
    }

    public static boolean mousemove(Inventory inv, Coord c) {
        if (draginv != inv) {
            return false;
        }
        dragcur = c;
        return true;
    }

    public static boolean mouseup(Inventory inv, Coord c, int button) {
        if (draginv != inv || button != dragbtn) {
            return false;
        }
        inv.ui.grabmouse(null);
        Coord a = new Coord(Math.min(dragstart.x, c.x), Math.min(dragstart.y, c.y));
        Coord b = new Coord(Math.max(dragstart.x, c.x), Math.max(dragstart.y, c.y));
        boolean add = dragbtn == 1;
        if (b.sub(a).x < 4 && b.sub(a).y < 4) {
            WItem w = at(inv, c);
            if (w != null) {
                if (add) {
                    toggle(w);
                } else {
                    sel.remove(w.item);
                }
            }
        } else {
            for (WItem w : FayteXfer.items(inv)) {
                Coord wa = w.c, wb = w.c.add(w.sz);
                if (wa.x < b.x && wb.x > a.x && wa.y < b.y && wb.y > a.y) {
                    if (add) {
                        add(w);
                    } else {
                        sel.remove(w.item);
                    }
                }
            }
        }
        draginv = null;
        dragstart = null;
        return true;
    }

    public static final Color ASIDE = new Color(0xE0, 0x50, 0x48, 120);

    public static void draw(Inventory inv, GOut g) {
        FayteRecipes.Recipe pv = FayteRecipes.previewing;
        if (pv != null && inv.ui != null && inv.ui.gui != null && inv == inv.ui.gui.maininv) {
            for (WItem w : FayteXfer.items(inv)) {
                if (FayteRecipes.excluded(pv.name, w, FayteSort.name(w)) || FayteRecipes.holdsrestricted(pv.name, w)) {
                    g.chcolor(ASIDE);
                    g.frect(w.c, w.sz);
                    g.chcolor(0xE0, 0x50, 0x48, 230);
                    g.line(w.c.add(3, 3), w.c.add(w.sz).sub(3, 3), 2);
                    g.chcolor();
                }
            }
        }
        if (sel.isEmpty() && draginv != inv) {
            return;
        }
        for (WItem w : FayteXfer.items(inv)) {
            if (sel.containsKey(w.item)) {
                g.chcolor(SEL);
                g.frect(w.c, w.sz);
                g.chcolor(SELEDGE);
                g.rect(w.c, w.sz.add(1, 1));
            }
        }
        if (draginv == inv && dragstart != null && dragcur != null) {
            Coord a = new Coord(Math.min(dragstart.x, dragcur.x), Math.min(dragstart.y, dragcur.y));
            Coord b = new Coord(Math.max(dragstart.x, dragcur.x), Math.max(dragstart.y, dragcur.y));
            g.chcolor(DRAG);
            g.frect(a, b.sub(a));
            g.chcolor(dragbtn == 1 ? SELEDGE : new Color(0xE0, 0x50, 0x48, 230));
            g.rect(a, b.sub(a).add(1, 1));
        }
        g.chcolor();
    }
}
