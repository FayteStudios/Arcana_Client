package haven;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class FayteTools {
    private static final String[] FILLABLE = {
        "bucket", "pail", "pot", "jug", "waterskin", "flask", "bottle", "kettle", "canteen"
    };
    private static final String[] TOOLS = {
        "pickaxe", "shovel", "spade", "scythe", "sickle", "hammer", "chisel", "knife", "saw", "hoe", "axe"
    };
    private static final String[] BUILDINGS = {"/arch/", "house", "barn", "cabin", "hut", "church", "mine", "tower"};
    private static final int LHAND = 6;
    private static final int RHAND = 7;
    private static final Map<String, Integer> doors = new HashMap<>();
    private static boolean doorsloaded = false;

    private static WItem lastrc = null;
    private static long lastrcat = 0;
    private static boolean menuseen = false;
    private static WItem fillitem = null;
    private static Inventory fillinv = null;
    private static Coord fillat = null;
    private static Object[] filltarget = null;
    private static int fillstage = 0;
    private static long filluntil = 0;

    private static Object[] feedtarget = null;
    private static String feedkind = null;
    private static int feedstage = 0;
    private static long feeduntil = 0;
    private static Object[] lastclick = null;
    private static String swaptool = null;
    private static int swapstage = 0;
    private static long swapuntil = 0;
    private static long lastswap = 0;

    private static String equipname = null;
    private static Inventory equipinv = null;
    private static Coord equipat = null;
    private static int equipslot = RHAND;
    private static int equipstage = 0;
    private static long equipuntil = 0;
    private static final int BELTSLOT = 5;
    private static Window beltopened = null;
    private static boolean beltwait = false;

    private static long atkarmed = 0;
    private static long atkcancel = 0;
    private static String movemode = null;
    private static boolean swimming = false;
    private static long dryat = 0;

    private static Coord stallpos = null;
    private static long stallsince = 0;
    private static long lastgate = 0;

    public static boolean on() {
        return FayteModules.TOOLS.on();
    }

    private static String kind(GItem g) {
        try {
            return g.resname();
        } catch (Loading e) {
            return null;
        }
    }

    private static String gobname(Gob g) {
        String n = FayteMsg.resname(g);
        return n == null ? "" : n;
    }

    private static boolean has(String s, String[] words) {
        for (String w : words) {
            if (s.contains(w)) {
                return true;
            }
        }
        return false;
    }

    public static String[] menutarget(GameUI gui) {
        long now = System.currentTimeMillis();
        boolean item = lastrc != null && now - lastrcat < 3000L && lastrcat >= lastclickat;
        if (item) {
            String k = kind(lastrc.item);
            String n = FayteAlmanac.itemname(lastrc.item);
            return k == null ? null : new String[] {k, n != null ? n : k.substring(k.lastIndexOf('/') + 1)};
        }
        if (gui != null && now - lastclickat < 3000L) {
            Gob g = lastgob(gui);
            String rn = g == null ? null : FayteMsg.resname(g);
            if (rn != null) {
                String d = null;
                try {
                    d = FayteWorldNames.display(rn);
                } catch (RuntimeException e) {
                    FayteLog.once("FayteTools.menutarget", e);
                }
                return new String[] {rn, d != null ? d : rn.substring(rn.lastIndexOf('/') + 1)};
            }
        }
        return null;
    }

    public static void itemrc(WItem w) {
        lastrc = w;
        lastrcat = System.currentTimeMillis();
        menuseen = false;
    }

    public static class FillMenu extends FlowerMenu {
        FillMenu(Coord c, Widget parent) {
            super(c, parent);
        }

        @Override
        public void choose(FlowerMenu.Petal opt) {
            if (opt != null && opt.num == -2) {
                startfill();
                uimsg("act", -2);
            } else {
                uimsg("cancel");
            }
        }
    }

    private static long sentat = 0L;

    private static Coord cursor(GameUI gui) {
        return gui.map.rootxlate(gui.ui.mc);
    }

    private static void mapclick(GameUI gui, Object... args) {
        gui.ui.lcc = gui.ui.mc;
        sentat = System.currentTimeMillis();
        gui.map.wdgmsg("click", args);
    }

    public static boolean ourmenu() {
        return System.currentTimeMillis() - sentat < 2000L;
    }

    public static void menuopened() {
        menuseen = true;
    }

    private static void nomenu(GameUI gui, long now) {
        if (menuseen || lastrc == null || now - lastrcat < 350) {
            return;
        }
        menuseen = true;
        if (now - lastrcat < 1500 && fillable() && lastrc.attached()) {
            new FillMenu(gui.ui.mc, gui.ui.root);
        }
    }

    public static boolean fillable() {
        if (!on() || !FayteConfig.toolsFill.get() || lastrc == null) {
            return false;
        }
        String n = FayteAlmanac.itemname(lastrc.item);
        if (n == null) {
            return false;
        }
        for (String w : n.toLowerCase().split("[^a-z]+")) {
            for (String v : FILLABLE) {
                if (w.equals(v)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inwater(GameUI gui) {
        Gob pl = gui.map == null ? null : gui.map.player();
        if (pl == null || pl.rc == null) {
            return false;
        }
        try {
            MCache map = gui.ui.sess.glob.map;
            Resource r = map.tilesetr(map.gettile(pl.rc.div(MCache.tilesz)));
            return r != null && (r.name.contains("water") || r.name.contains("shallow"));
        } catch (Loading e) {
            return false;
        }
    }

    public static void startfill() {
        if (lastrc == null || !(lastrc.parent instanceof Inventory) || lastrc.ui == null || lastrc.ui.gui == null) {
            return;
        }
        GameUI gui = lastrc.ui.gui;
        if (!inwater(gui)) {
            FayteMsg.say("Stand in shallow water to fill it");
            return;
        }
        if (!gui.hand.isEmpty()) {
            FayteMsg.say("Empty your hand first");
            return;
        }
        Gob pl = gui.map.player();
        fillitem = lastrc;
        fillinv = (Inventory) lastrc.parent;
        fillat = lastrc.server_c;
        filltarget = new Object[] {Coord.z, pl.rc, 0};
        fillitem.item.wdgmsg("take", Coord.z);
        fillstage = 1;
        filluntil = System.currentTimeMillis() + 2000;
    }

    public static void feedstart(GameUI gui, boolean ctrl, Object[] itemact) {
        if (!on() || !FayteConfig.toolsFeed.get() || !ctrl || gui.hand.isEmpty()) {
            feedtarget = null;
            return;
        }
        boolean fresh = feedstage == 0;
        feedkind = kind(gui.hand.iterator().next());
        feedtarget = itemact;
        feedstage = 1;
        feeduntil = System.currentTimeMillis() + 3000;
        if (fresh) {
            FayteMsg.say("Repeating on this target. To stop: right-click, drop the item, or put it back.");
        }
    }

    public static void feedcancel() {
        if (feedstage != 0 || feedtarget != null) {
            boolean was = feedstage != 0;
            feedstage = 0;
            feedtarget = null;
            if (was) {
                FayteMsg.say("Repeat stopped.");
            }
        }
    }

    private static WItem first(Inventory inv, String k) {
        if (inv == null || k == null) {
            return null;
        }
        for (Widget w = inv.child; w != null; w = w.next) {
            if (w instanceof WItem && k.equals(kind(((WItem) w).item))) {
                return (WItem) w;
            }
        }
        return null;
    }

    private static long lastclickat = 0;

    public static boolean lastwasworld() {
        return lastclickat >= lastrcat;
    }

    public static long lastclickage() {
        return System.currentTimeMillis() - lastclickat;
    }

    public static void clicked(Object[] click) {
        lastclick = click;
        lastclickat = System.currentTimeMillis();
        lastrc = null;
    }

    private static void loaddoors() {
        if (doorsloaded) {
            return;
        }
        doorsloaded = true;
        File f = new File(FaytePaths.fayte(), "doors.tsv");
        if (f.exists()) {
            try {
                for (String l : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    String[] p = l.trim().split("\t");
                    if (p.length == 2) {
                        doors.put(p[0], Integer.parseInt(p[1]));
                    }
                }
            } catch (Exception e) {
                FayteLog.once("FayteTools.loaddoors", e);
            }
        }
    }

    private static void savedoors() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : doors.entrySet()) {
            sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        try {
            File f = new File(FaytePaths.fayte(), "doors.tsv");
            f.getParentFile().mkdirs();
            FaytePaths.write(f, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.once("FayteTools.savedoors", e);
        }
    }

    public static int part(Gob gob, int part, int button, int mods) {
        if (!on() || !FayteConfig.doorsBuilding.get() || button != 3 || mods != 0) {
            return part;
        }
        String n = gobname(gob);
        if (!has(n, BUILDINGS)) {
            return part;
        }
        loaddoors();
        if (part >= 0) {
            Integer known = doors.get(n);
            if (known == null || known != part) {
                doors.put(n, part);
                savedoors();
            }
            return part;
        }
        Integer d = doors.get(n);
        return d != null ? d : part;
    }

    public static void onmessage(String msg) {
        if (!on() || msg == null) {
            return;
        }
        String m = msg.toLowerCase();
        if (m.contains("need") || m.contains("must") || m.contains("require") || m.contains("without")) {
            FayteLog.log("Game message: " + msg);
            if (!FayteConfig.toolsSwap.get() || swapstage != 0 || System.currentTimeMillis() - lastswap < 3000) {
                return;
            }
            for (String t : TOOLS) {
                if (m.contains(t)) {
                    swaptool = t;
                    swapstage = 1;
                    swapuntil = System.currentTimeMillis() + 3000;
                    lastswap = System.currentTimeMillis();
                    return;
                }
            }
        }
    }

    private static WItem findtool(GameUI gui, String t) {
        Inventory[] invs = {gui.maininv, beltinv(gui)};
        for (Inventory inv : invs) {
            if (inv == null) {
                continue;
            }
            for (Widget w = inv.child; w != null; w = w.next) {
                if (w instanceof WItem) {
                    String k = kind(((WItem) w).item);
                    if (k != null) {
                        String last = k.substring(k.lastIndexOf('/') + 1).toLowerCase();
                        if (last.contains(t) && !(t.equals("axe") && last.contains("pickaxe"))) {
                            return (WItem) w;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Inventory beltinv(GameUI gui) {
        for (Widget w = gui.child; w != null; w = w.next) {
            if (w instanceof Window && (w.visible || w == beltopened) && ((Window) w).cap != null) {
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

    public static void smart(GameUI gui) {
        smart(gui, false);
    }

    private static boolean carrying(GameUI gui) {
        Gob pl = gui.map == null ? null : gui.map.player();
        if (pl == null) {
            return true;
        }

        synchronized (gui.ui.sess.glob.oc) {
            for (Gob g : gui.ui.sess.glob.oc) {
                Following f = g.getattr(Following.class);
                if (f != null && f.tgt == pl.id) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean ground(GameUI gui, Coord mc) {
        if (!on() || !FayteConfig.smartGround.get() || gui == null || !gui.hand.isEmpty()) {
            return false;
        }
        if (!UI.isCursor("gfx/hud/curs/arw") || carrying(gui)) {
            return false;
        }
        return near(gui, mc, MCache.tilesz.x * 1.5);
    }

    public static void cancel(GameUI gui) {
        if (gui == null || gui.map == null) {
            return;
        }
        Gob pl = gui.map.player();
        if (pl != null && pl.rc != null) {
            mapclick(gui, cursor(gui), pl.rc, 3, 0);
        }
    }

    public static void smart(GameUI gui, boolean quiet) {
        if (gui == null || gui.map == null) {
            return;
        }
        if (!gui.hand.isEmpty() || !UI.isCursor("gfx/hud/curs/arw") || carrying(gui)) {
            return;
        }
        Gob pl = gui.map.player();
        if (pl == null) {
            return;
        }
        if (!near(gui, pl.rc, FayteConfig.smartRadius.get() * MCache.tilesz.x) && !quiet) {
            FayteMsg.say("Nothing to interact with nearby");
        }
    }

    private static boolean near(GameUI gui, Coord pc, double best) {
        Gob pl = gui.map.player();
        Gob tgt = null;
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob g : gui.ui.sess.glob.oc) {
                if (g == pl || g.rc == null) {
                    continue;
                }
                double d = g.rc.dist(pc);
                if (d < best && smartok(g)) {
                    best = d;
                    tgt = g;
                }
            }
        }
        if (tgt == null) {
            return false;
        }
        mapclick(gui, cursor(gui), tgt.rc, 3, 0, 0, (int) tgt.id, tgt.rc, 0, -1);
        return true;
    }

    private static boolean smartok(Gob g) {
        String n = gobname(g);
        if (n.isEmpty()) {
            return false;
        }
        if (n.startsWith("gfx/borka")) {
            return false;
        } else if (FayteConfig.smartAny.get()) {
            return true;
        } else if (n.startsWith("gfx/kritter")) {
            return FayteConfig.smartAnimals.get();
        } else if (n.startsWith("gfx/invobjs")) {
            return FayteConfig.smartItems.get();
        } else if (n.contains("/herbs/") || n.contains("flower")) {
            return FayteConfig.smartHerbs.get();
        } else if (n.startsWith("gfx/terobjs/plants/")) {
            return FayteConfig.smartCrops.get();
        } else if (n.contains("/bushes/")) {
            return FayteConfig.smartBushes.get();
        } else if (n.contains("/trees/")) {
            return FayteConfig.smartTrees.get();
        } else if (n.contains("bumling") || n.contains("boulder")) {
            return FayteConfig.smartBoulders.get();
        }
        return false;
    }

    public static void tick(GameUI gui) {
        long now = System.currentTimeMillis();
        if (atkcancel != 0 && now > atkcancel) {
            atkcancel = 0;
            if (!UI.isCursor("gfx/hud/curs/arw")) {
                cancel(gui);
            }
        }
        nomenu(gui, now);
        filltick(gui, now);
        feedtick(gui, now);
        swaptick(gui, now);
        equiptick(gui, now);
        swimtick(gui, now);
        gatetick(gui, now);
    }

    public static Gob lastgob(GameUI gui) {
        Object[] lc = lastclick;
        if (lc == null || lc.length < 6 || !(lc[5] instanceof Integer)) {
            return null;
        }
        return gui.ui.sess.glob.oc.getgob((Integer) lc[5]);
    }

    public static String lastobject(GameUI gui) {
        Object[] lc = lastclick;
        if (lc == null || lc.length < 6 || !(lc[5] instanceof Integer)) {
            return null;
        }
        Gob g = gui.ui.sess.glob.oc.getgob((Integer) lc[5]);
        return g == null ? null : FayteMsg.resname(g);
    }

    public static void acted(String[] ad) {
        if (ad != null && ad.length > 1 && "blk".equals(ad[0])) {
            movemode = ad[1];
        }
        if (on()
                && ad != null
                && ad.length > 1
                && "atk".equals(ad[0])
                && !"prospect".equals(ad[1])
                && FayteConfig.autoAttack.get()) {
            atkarmed = System.currentTimeMillis();
        }
    }

    public static void cursor(Widget w, boolean set) {
        if (atkarmed == 0 || !set) {
            return;
        }
        long at = atkarmed;
        atkarmed = 0;
        GameUI gui = w.ui == null ? null : w.ui.gui;
        if (System.currentTimeMillis() - at > 2000 || gui == null || gui.map == null) {
            return;
        }
        if (gui.map.clickatmouse(1)) {
            atkcancel = System.currentTimeMillis() + 250L;
            return;
        }
        if (gui.fv == null || gui.fv.current == null) {
            return;
        }
        Gob g = gui.ui.sess.glob.oc.getgob(gui.fv.current.gobid);
        if (g == null || g.rc == null) {
            return;
        }
        gui.ui.lcc = gui.ui.mc;

        gui.map.wdgmsg("click", gui.map.sz.div(2), g.rc, 1, 0, 0, (int) g.id, g.rc, 0, -1);
        atkcancel = System.currentTimeMillis() + 250L;
    }

    private static void swimtick(GameUI gui, long now) {
        if (movemode == null || gui.map == null || !FayteConfig.swimMode.get()) {
            return;
        }
        Gob pl = gui.map.player();
        if (pl == null || pl.rc == null) {
            return;
        }
        boolean deep;
        try {
            Resource r = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(pl.rc.div(MCache.tilesz)));
            deep = r != null && r.name.contains("deep");
        } catch (Loading e) {
            return;
        }
        if (deep) {
            swimming = true;
            dryat = 0;
        } else if (swimming) {
            if (dryat == 0) {
                dryat = now;
            } else if (now - dryat > 1200) {
                swimming = false;
                dryat = 0;
                gui.menu.wdgmsg("act", "blk", movemode);
            }
        }
    }

    private static boolean named(GItem g, String want) {
        String w = want.toLowerCase().replaceAll("[^a-z]", "");
        String n = FayteAlmanac.itemname(g);
        if (n != null && n.toLowerCase().replaceAll("[^a-z]", "").contains(w)) {
            return true;
        }
        String k = kind(g);
        return k != null
                && k.substring(k.lastIndexOf('/') + 1)
                        .toLowerCase()
                        .replaceAll("[^a-z]", "")
                        .contains(w);
    }

    private static boolean shield(GItem g) {
        String k = kind(g);
        String n = FayteAlmanac.itemname(g);
        String t = ((k == null ? "" : k) + " " + (n == null ? "" : n)).toLowerCase();
        return t.contains("shield") || t.contains("buckler");
    }

    public static void equip(GameUI gui, String name) {
        Equipory e = gui.getEquipory();
        if (e == null || name == null || name.trim().isEmpty()) {
            return;
        }
        if (!gui.hand.isEmpty()) {
            FayteMsg.say("Empty your hand first");
            return;
        }
        for (int sl : new int[] {RHAND, LHAND}) {
            if (e.slots[sl] != null && named(e.slots[sl].item, name)) {
                return;
            }
        }
        WItem tool = null;
        Inventory from = null;
        Inventory[] invs = {beltinv(gui), gui.maininv};
        for (Inventory inv : invs) {
            if (inv == null || tool != null) {
                continue;
            }
            for (Widget w = inv.child; w != null; w = w.next) {
                if (w instanceof WItem && named(((WItem) w).item, name)) {
                    tool = (WItem) w;
                    from = inv;
                    break;
                }
            }
        }
        if (tool == null && beltinv(gui) == null && e.slots[BELTSLOT] != null && !beltwait) {
            beltwait = true;
            equipname = name;
            equipuntil = System.currentTimeMillis() + 3000;
            equipstage = 10;
            e.slots[BELTSLOT].mousedown(Coord.z, 3);
            return;
        }
        if (tool == null) {
            FayteMsg.say("No " + name + " in your belt or bag");
            closebelt();
            return;
        }
        equipname = name;
        equipinv = from;
        equipat = tool.server_c;
        equipslot = e.slots[RHAND] != null
                ? RHAND
                : (e.slots[LHAND] != null && !shield(e.slots[LHAND].item) ? LHAND : RHAND);
        equipuntil = System.currentTimeMillis() + 4000;
        if (e.slots[equipslot] != null) {
            e.slots[equipslot].item.wdgmsg("take", Coord.z);
            equipstage = 1;
        } else {
            tool.item.wdgmsg("take", Coord.z);
            equipstage = 3;
        }
    }

    private static Window beltwindow(GameUI gui) {
        Inventory inv = beltinv(gui);
        return inv == null ? null : inv.getparent(Window.class);
    }

    private static void closebelt() {
        Window w = beltopened;
        beltopened = null;
        beltwait = false;
        if (w != null && w.linked()) {
            w.wdgmsg("close");
        }
    }

    private static void equiptick(GameUI gui, long now) {
        if (equipstage == 0) {
            return;
        }
        Equipory e = gui.getEquipory();
        if (equipstage == 10) {
            Window bw = beltwindow(gui);
            if (bw != null) {
                beltopened = bw;
                bw.hide();
                equipstage = 0;
                String n = equipname;
                equip(gui, n);
                if (equipstage == 0) {
                    closebelt();
                }
            } else if (now > equipuntil) {
                beltwait = false;
                equipstage = 0;
                FayteMsg.say("Could not open your belt to find " + equipname, GameUI.MsgType.BAD);
            }
            return;
        }
        if (now > equipuntil || e == null) {
            if (equipstage != 4) {
                FayteMsg.say("Could not swap to " + equipname, GameUI.MsgType.BAD);
            }
            equipstage = 0;
            closebelt();
            return;
        }
        GItem held = gui.hand.isEmpty() ? null : gui.hand.iterator().next();
        if (equipstage == 1 && held != null) {
            equipinv.wdgmsg("drop", equipat);
            equipstage = 2;
        } else if (equipstage == 2 && held != null && named(held, equipname)) {
            e.wdgmsg("drop", equipslot);
            equipstage = 4;
        } else if (equipstage == 3 && held != null) {
            e.wdgmsg("drop", equipslot);
            equipstage = 4;
        } else if (equipstage == 4 && held == null) {
            equipstage = 0;
            closebelt();
        }
    }

    private static void filltick(GameUI gui, long now) {
        if (fillstage == 0) {
            return;
        }
        if (now > filluntil) {
            if (fillstage == 2 && !gui.hand.isEmpty() && fillinv != null && fillat != null) {
                fillinv.wdgmsg("drop", fillat);
            }
            fillstage = 0;
            return;
        }
        if (fillstage == 1 && !gui.hand.isEmpty()) {
            if (gui.map != null) {
                gui.map.wdgmsg("itemact", filltarget);
            }
            fillstage = 2;
            filluntil = now + 1500;
        } else if (fillstage == 2 && !gui.hand.isEmpty() && fillinv != null && fillat != null) {
            try {
                if (ItemInfo.getContent(gui.hand.iterator().next().info()) != null) {
                    fillinv.wdgmsg("drop", fillat);
                    fillstage = 0;
                }
            } catch (Loading e) {
            } catch (RuntimeException e) {
                FayteLog.once("FayteTools.filltick", e);
            }
        }
    }

    private static void feedtick(GameUI gui, long now) {
        if (feedstage == 0 || feedtarget == null) {
            return;
        }
        if (feedstage == 1) {
            if (gui.hand.isEmpty()) {
                WItem w = first(gui.maininv, feedkind);
                if (w == null) {
                    feedstage = 0;
                    FayteMsg.say("Out of items to feed");
                    return;
                }
                w.item.wdgmsg("take", Coord.z);
                feedstage = 2;
                feeduntil = now + 2000;
            } else if (now > feeduntil) {
                feedstage = 0;
            }
        } else if (feedstage == 2) {
            if (!gui.hand.isEmpty()) {
                Object[] t = feedtarget.clone();
                t[2] = 0;
                gui.map.wdgmsg("itemact", t);
                feedstage = 1;
                feeduntil = now + 3000;
            } else if (now > feeduntil) {
                feedstage = 0;
            }
        }
    }

    private static void swaptick(GameUI gui, long now) {
        if (swapstage == 0) {
            return;
        }
        Equipory e = gui.getEquipory();
        if (now > swapuntil || e == null) {
            swapstage = 0;
            return;
        }
        if (swapstage == 1) {
            if (!gui.hand.isEmpty()) {
                swapstage = 0;
                return;
            }
            WItem tool = findtool(gui, swaptool);
            if (tool == null) {
                FayteMsg.say("No " + swaptool + " in your inventory or belt");
                swapstage = 0;
                return;
            }
            tool.item.wdgmsg("take", Coord.z);
            swapstage = 2;
        } else if (swapstage == 2 && !gui.hand.isEmpty()) {
            int slot = e.slots[RHAND] == null ? RHAND : e.slots[LHAND] == null ? LHAND : RHAND;
            if (e.slots[slot] == null || !FayteXfer.equipdrop(e, slot)) {
                e.wdgmsg("drop", slot);
            }
            swapstage = 3;
        } else if (swapstage == 3 && gui.hand.isEmpty()) {
            if (lastclick != null && gui.map != null) {
                Object[] args = lastclick.clone();
                args[0] = cursor(gui);
                mapclick(gui, args);
            }
            swapstage = 0;
        }
    }

    private static void gatetick(GameUI gui, long now) {
        if (!FayteConfig.doorsGates.get() || gui.map == null) {
            return;
        }
        Gob pl = gui.map.player();
        if (pl == null || pl.rc == null) {
            return;
        }
        boolean moving = pl.getattr(Moving.class) != null;
        if (!moving) {
            stallpos = null;
            return;
        }
        if (stallpos == null || !stallpos.equals(pl.rc)) {
            stallpos = pl.rc;
            stallsince = now;
            return;
        }
        if (now - stallsince < 350 || now - lastgate < 2500) {
            return;
        }
        Gob gate = null;
        double best = 1.6 * MCache.tilesz.x;
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob g : gui.ui.sess.glob.oc) {
                if (g.rc != null && gobname(g).contains("gate")) {
                    double d = g.rc.dist(pl.rc);
                    if (d < best) {
                        best = d;
                        gate = g;
                    }
                }
            }
        }
        if (gate != null) {
            lastgate = now;
            mapclick(gui, cursor(gui), gate.rc, 3, 0, 0, (int) gate.id, gate.rc, 0, -1);
        }
    }
}
