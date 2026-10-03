package haven;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class FayteXfer {
    private static final int BELTSLOT = 5;
    private static WeakReference<Inventory> lastother = new WeakReference<>(null);
    private static final Map<Inventory, String> lastkind = new WeakHashMap<>();
    private static int stage = 0;
    private static int swapslot = -1;
    private static long until = 0;
    private static String wornkind = null;
    private static Set<GItem> before = new HashSet<>();
    private static WItem moved = null;
    private static Coord movedat = null;
    private static Inventory belt = null;
    private static boolean beltopened = false;

    public static boolean on() {
        return FayteModules.CONTAINERS.on();
    }

    public static void touch(Inventory inv) {
        if (inv != null && inv.ui != null && inv.ui.gui != null && inv != inv.ui.gui.maininv) {
            lastother = new WeakReference<>(inv);
        }
    }

    private static Inventory counterpart(Inventory inv) {
        GameUI gui = inv.ui == null ? null : inv.ui.gui;
        if (gui == null) {
            return null;
        } else if (inv != gui.maininv) {
            return gui.maininv;
        }
        Inventory o = lastother.get();
        return o != null && o.attached() && o.visible ? o : null;
    }

    public static List<WItem> items(Inventory inv) {
        List<WItem> ret = new ArrayList<>();
        for (Widget w = inv.child; w != null; w = w.next) {
            if (w.visible && w instanceof WItem) {
                ret.add((WItem) w);
            }
        }
        return ret;
    }

    private static String kind(WItem w) {
        try {
            return w.item.resname();
        } catch (Loading e) {
            return null;
        }
    }

    public static String purity(WItem w) {
        try {
            Alchemy a = ItemInfo.find(Alchemy.class, w.item.info());
            return a == null ? null : String.format("%.2f", 100 * a.purity());
        } catch (Loading e) {
            return null;
        }
    }

    private static WItem at(Inventory inv, Coord c) {
        for (WItem w : items(inv)) {
            if (c.isect(w.c, w.sz)) {
                return w;
            }
        }
        return null;
    }

    private static WItem first(Inventory inv, String kind) {
        for (WItem w : items(inv)) {
            if (kind.equals(kind(w))) {
                return w;
            }
        }
        return null;
    }

    public static boolean wheel(Inventory inv, Coord c, int amount) {
        WItem over = at(inv, c);
        String k = over != null ? kind(over) : lastkind.get(inv);
        touch(inv);
        if (amount > 0) {
            WItem w = k == null ? null : first(inv, k);
            if (w == null) {
                List<WItem> all = items(inv);
                w = all.isEmpty() ? null : all.get(0);
            }
            if (w != null) {
                lastkind.put(inv, kind(w));
                w.item.wdgmsg("transfer", Coord.z);
            }
        } else {
            Inventory from = counterpart(inv);
            WItem w = from == null || k == null ? null : first(from, k);
            if (w != null) {
                w.item.wdgmsg("transfer", Coord.z);
            }
        }
        return true;
    }

    public static boolean samepurity(WItem clicked) {
        if (!(clicked.parent instanceof Inventory)) {
            return false;
        }
        Inventory inv = (Inventory) clicked.parent;
        touch(inv);
        String k = kind(clicked);
        String p = purity(clicked);
        if (k == null) {
            return true;
        }
        int n = 0;
        for (WItem w : items(inv)) {
            if (k.equals(kind(w)) && (p == null ? purity(w) == null : p.equals(purity(w)))) {
                w.item.wdgmsg("transfer", Coord.z);
                if (Config.limit_transfer_amount && ++n >= 72) {
                    break;
                }
            }
        }
        return true;
    }

    private static String refillkind = null;
    private static long refilluntil = 0;

    public static void itemact(GameUI gui, boolean shift) {
        if (!on() || gui == null || gui.hand.isEmpty()) {
            refillkind = null;
            return;
        }
        if (shift) {
            try {
                refillkind = gui.hand.iterator().next().resname();
                refilluntil = System.currentTimeMillis() + 3000;
            } catch (Loading e) {
                refillkind = null;
            }
        } else {
            refillkind = null;
        }
    }

    public static void handempty(GameUI gui) {
        String k = refillkind;
        refillkind = null;
        if (!on() || k == null || System.currentTimeMillis() > refilluntil || gui.maininv == null) {
            return;
        }
        WItem w = first(gui.maininv, k);
        if (w != null) {
            w.item.wdgmsg("take", Coord.z);
        }
    }

    public static boolean openall(WItem clicked) {
        if (!(clicked.parent instanceof Inventory)) {
            return false;
        }
        Inventory inv = (Inventory) clicked.parent;
        String k = kind(clicked);
        if (k == null) {
            return true;
        }
        Widget wp = inv.parent;
        while (wp != null && !(wp instanceof Window)) {
            wp = wp.parent;
        }
        if (wp != null) {
            FaytePlacer.anchor(new Coord(wp.c.x + wp.sz.x, wp.c.y));
        }
        for (WItem w : items(inv)) {
            if (k.equals(kind(w))) {
                FayteLabels.openedmore(w);
                w.item.wdgmsg("iact", Coord.z);
            }
        }
        return true;
    }

    private static Window findwindow(GameUI gui, String... words) {
        for (Widget w = gui.child; w != null; w = w.next) {
            if (w instanceof Window && w.visible && ((Window) w).cap != null) {
                String t = ((Window) w).cap.text.toLowerCase();
                for (String word : words) {
                    if (t.contains(word)) {
                        return (Window) w;
                    }
                }
            }
        }
        return null;
    }

    public static void invpack(GameUI gui) {
        if (gui == null || gui.invwnd == null) {
            return;
        }
        Window bp = findwindow(gui, "pack");
        boolean inv = gui.invwnd.visible;
        if (inv && bp != null) {
            Set<String> names = new HashSet<>();
            List<Inventory> invs = new ArrayList<>();
            invs.add(gui.maininv);
            for (Widget c = bp.child; c != null; c = c.next) {
                if (c instanceof Inventory) {
                    invs.add((Inventory) c);
                }
            }
            for (Inventory iv : invs) {
                if (iv != null) {
                    for (WItem w : items(iv)) {
                        String n = FayteAlmanac.itemname(w.item);
                        if (n != null) {
                            names.add(n.toLowerCase());
                        }
                    }
                }
            }
            List<Window> sacks = new ArrayList<>();
            for (Widget w = gui.child; w != null; w = w.next) {
                if (w instanceof Window
                        && w != gui.invwnd
                        && w != bp
                        && ((Window) w).cap != null
                        && ((Window) w).hasinv()
                        && names.contains(((Window) w).cap.text.toLowerCase())) {
                    sacks.add((Window) w);
                }
            }
            for (Window w : sacks) {
                w.wdgmsg(w, "close");
            }
            gui.invwnd.hide();
            bp.wdgmsg(bp, "close");
            return;
        }
        if (!inv) {
            gui.invwnd.show();
            gui.invwnd.raise();
        }
        Equipory e = gui.getEquipory();
        if (bp == null && e != null && e.slots.length > 14 && e.slots[14] != null) {
            e.slots[14].mousedown(Coord.z, 3);
        }
    }

    private static Inventory findbelt(GameUI gui) {
        for (Widget w = gui.child; w != null; w = w.next) {
            if (w instanceof Window && w.visible && ((Window) w).cap != null) {
                String t = ((Window) w).cap.text.toLowerCase();
                if (t.contains("belt") || t.contains("sash") || t.contains("pouch")) {
                    for (Widget c = w.child; c != null; c = c.next) {
                        if (c instanceof Inventory) {
                            return (Inventory) c;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Coord freecell(Inventory inv) {
        Set<Coord> used = new HashSet<>();
        for (WItem w : items(inv)) {
            if (w.server_c != null) {
                used.add(w.server_c);
            }
        }
        for (int y = 0; y < inv.isz.y; y++) {
            for (int x = 0; x < inv.isz.x; x++) {
                Coord c = new Coord(x, y);
                if (!used.contains(c)) {
                    return c;
                }
            }
        }
        return null;
    }

    private static void reset() {
        stage = 0;
        swapslot = -1;
        moved = null;
        movedat = null;
        belt = null;
        beltopened = false;
        before = new HashSet<>();
    }

    public static boolean equipdrop(Equipory e, int slot) {
        GameUI gui = e.ui.gui;
        if (!on()
                || stage != 0
                || slot < 0
                || slot >= e.slots.length
                || e.slots[slot] == null
                || gui == null
                || gui.hand.isEmpty()) {
            return false;
        }
        wornkind = kind(e.slots[slot]);
        before = new HashSet<>();
        if (gui.maininv != null) {
            for (WItem w : items(gui.maininv)) {
                before.add(w.item);
            }
        }
        e.slots[slot].item.wdgmsg("transfer", Coord.z);
        swapslot = slot;
        stage = 1;
        until = System.currentTimeMillis() + 2000;
        return true;
    }

    public static void equiptick(Equipory e) {
        if (stage == 0) {
            return;
        }
        GameUI gui = e.ui.gui;
        long now = System.currentTimeMillis();
        if (gui == null || gui.maininv == null) {
            reset();
            return;
        }
        switch (stage) {
            case 1:
                if (now > until || gui.hand.isEmpty()) {
                    reset();
                } else if (e.slots[swapslot] == null) {
                    e.wdgmsg("drop", swapslot);
                    stage = 2;
                    until = now + 2000;
                }
                break;
            case 2:
                if (moved == null) {
                    for (WItem w : items(gui.maininv)) {
                        if (!before.contains(w.item) && wornkind != null && wornkind.equals(kind(w))) {
                            moved = w;
                            movedat = w.server_c;
                        }
                    }
                }
                belt = findbelt(gui);
                if (belt == null && !beltopened && e.slots[BELTSLOT] != null) {
                    beltopened = true;
                    e.slots[BELTSLOT].mousedown(Coord.z, 3);
                }
                if (now > until) {
                    reset();
                } else if (moved != null && belt != null && gui.hand.isEmpty()) {
                    if (freecell(belt) == null) {
                        reset();
                    } else {
                        moved.item.wdgmsg("take", Coord.z);
                        stage = 3;
                        until = now + 1500;
                    }
                }
                break;
            case 3:
                if (now > until) {
                    reset();
                } else if (!gui.hand.isEmpty()) {
                    Coord fc = belt == null || !belt.attached() ? null : freecell(belt);
                    if (fc == null) {
                        gui.maininv.wdgmsg("drop", movedat);
                        reset();
                    } else {
                        belt.wdgmsg("drop", fc);
                        stage = 4;
                        until = now + 1500;
                    }
                }
                break;
            case 4:
                if (gui.hand.isEmpty()) {
                    reset();
                } else if (now > until) {
                    if (movedat != null) {
                        gui.maininv.wdgmsg("drop", movedat);
                    }
                    FayteMsg.say("The belt did not accept that item, so it went back to your inventory");
                    reset();
                }
                break;
            default:
                reset();
        }
    }
}
