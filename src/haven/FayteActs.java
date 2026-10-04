package haven;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FayteActs {
    public static final String[] SECTIONS = {
        "Toggles", "Actions", "Panels", "Combat", "Music & Emotes", "Macros", "Unsorted"
    };
    public static final String[] SPEEDS = {"Crawl", "Walk", "Run", "Sprint"};
    private static final String[] SPEEDRES = {"crawl", "walk", "run", "sprint"};
    private static final String[] TOGGLEWORDS = {"track", "crim", "swim", "prospect", "claim"};
    private static final Set<String> TOGGLES = new HashSet<>(
            Arrays.asList("paginae/act/crime", "paginae/act/tracking", "paginae/act/swimming", "paginae/atk/prospect"));
    private static final String[][] PANELS = {
        {"invpack", "Bag"},
        {"almanac", "Almanac"},
        {"recipes", "Recipes"},
        {"pilgrims", "Kin"},
        {"skills", "Skills"},
        {"options", "Options"},
        {"worldmap", "World map"},
        {"ftimers", "Timers"},
        {"selections", "Selections"},
        {"tools", "Tools"},
        {"view", "View"},
        {"keybinds", "Key bindings"},
        {"chatwindow", "Chat window"},
        {"actions", "Actions"},
    };
    private static final String[][] FAYTE = {
        {"footprints", "Footprints"}, {"lockui", "Lock UI"}, {"smart", "Smart interact"}, {"cancel", "Cancel action"},
    };
    private static final Map<String, Tex> icons = new HashMap<>();
    private static final Map<String, String> ICONS = new HashMap<>();
    private static final Map<String, String> NAMES = new HashMap<>();

    static {
        ICONS.put("cmd:invpack", "gfx/hud/invup");
        ICONS.put("cmd:pilgrims", "gfx/hud/budup");
        ICONS.put("cmd:skills", "gfx/hud/chrup");
        ICONS.put("cmd:options", "gfx/hud/optup");
        ICONS.put("cmd:actions", "paginae/skills/bullying");
        ICONS.put("cmd:recipes", "paginae/act/craft");
        ICONS.put("fayte:abacus", "gfx/hud/equup");
        ICONS.put("fayte:footprints", "paginae/act/tracking");
        ICONS.put("fayte:markers", "gfx/hud/ptrup");
        ICONS.put("fayte:equipment", "gfx/hud/equup");
        NAMES.put("fayte:equipment", "Equipment");
        ICONS.put("fayte:claimp", "gfx/hud/claup");
        ICONS.put("fayte:claimt", "gfx/hud/towup");
        ICONS.put("fayte:claimw", "gfx/hud/warup");
        ICONS.put("fayte:pointer", "gfx/hud/ptrup");
        NAMES.put("fayte:claimp", "Personal claims");
        NAMES.put("fayte:claimt", "Town claims");
        NAMES.put("fayte:claimw", "Waste claims");
        NAMES.put("fayte:pointer", "Homestead pointer");
    }

    public static class Act {
        public final String id;
        public final String name;
        public final String group;

        Act(String id, String name, String group) {
            this.id = id;
            this.name = name;
            this.group = group;
        }
    }

    private static List<Glob.Pagina> pags(GameUI gui) {
        Glob glob = gui.ui.sess.glob;
        synchronized (glob.paginae) {
            return new ArrayList<>(glob.paginae);
        }
    }

    public static Glob.Pagina pagina(GameUI gui, String resname) {
        for (Glob.Pagina p : pags(gui)) {
            try {
                Resource r = p.res();
                if (r != null && r.name.equals(resname)) {
                    return p;
                }
            } catch (Loading e) {
            }
        }
        return null;
    }

    private static List<String> path(Resource.AButton a) {
        List<String> ret = new ArrayList<>();
        Resource pr = a.parent;
        for (int i = 0; pr != null && i < 8; i++) {
            try {
                Resource.AButton pa = pr.layer(Resource.action);
                if (pa == null) {
                    break;
                }
                ret.add(0, pa.name);
                pr = pa.parent;
            } catch (Loading e) {
                break;
            }
        }
        return ret;
    }

    private static boolean has(String n, String... words) {
        for (String w : words) {
            if (n.contains(w)) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, List<Act>> sections(GameUI gui) {
        Map<String, List<Act>> s = new LinkedHashMap<>();
        for (String n : SECTIONS) {
            s.put(n, new ArrayList<>());
        }
        s.get("Toggles").add(new Act("cmd:lockui", "Lock UI", "Misc"));
        s.get("Toggles").add(new Act("fayte:claimp", "Personal claims", "Map"));
        s.get("Toggles").add(new Act("fayte:claimt", "Town claims", "Map"));
        s.get("Toggles").add(new Act("fayte:claimw", "Waste claims", "Map"));
        s.get("Toggles").add(new Act("fayte:pointer", "Homestead pointer", "Map"));
        s.get("Panels").add(new Act("fayte:abacus", "Abacus", "Info"));
        s.get("Panels").add(new Act("fayte:equipment", "Equipment", "Info"));
        s.get("Toggles").add(new Act("fayte:radius", "Object radius", "Misc"));
        String[][] panels = {
            {"cmd:actions", "Actions", "Info"},
            {"cmd:almanac", "Almanac", "Info"},
            {"cmd:invpack", "Bag", "Info"},
            {"cmd:pilgrims", "Kin", "Info"},
            {"cmd:worldmap", "World map", "Map"},
            {"cmd:selections", "Selections", "Map"},
            {"fayte:footprints", "Footprints", "Map"},
            {"fayte:markers", "Markers & alerts", "Map"},
            {"cmd:ftimers", "Timers", "Info"},
        };
        for (String[] pn : panels) {
            s.get("Panels").add(new Act(pn[0], pn[1], pn[2]));
        }
        for (Glob.Pagina p : pags(gui)) {
            try {
                Resource r = p.res();
                Resource.AButton a = r == null ? null : r.layer(Resource.action);
                if (a == null || a.ad == null || a.ad.length == 0) {
                    continue;
                }
                String rn = r.name;
                String par = a.parent == null ? "" : a.parent.name;
                List<String> path = path(a);
                String grp = path.isEmpty() ? "" : path.get(path.size() - 1);
                String id = "pag:" + rn;
                if (rn.startsWith("paginae/add/")
                        || rn.startsWith("paginae/help/")
                        || rn.startsWith("paginae/craft/")
                        || (gui.menu != null && gui.menu.isCrafting(p))) {
                    continue;
                } else if (rn.equals("paginae/act/swimming") || par.equals("paginae/act/mov")) {
                    s.get("Toggles").add(new Act(id, a.name, "Movement"));
                } else if (par.equals("paginae/act/climb")) {
                    s.get("Toggles").add(new Act(id, a.name, "Climbing"));
                } else if (TOGGLES.contains(rn)) {
                    s.get("Toggles").add(new Act(id, a.name, "Misc"));
                } else if (par.equals("paginae/act/adv")
                        || par.equals("paginae/act/travel")
                        || rn.equals("paginae/bld/claim")
                        || par.equals("paginae/bld/wasteclaims")) {
                    s.get("Actions").add(new Act(id, a.name, "Basic"));
                } else if (rn.startsWith("paginae/bld/") || par.equals("paginae/act/bld")) {
                    continue;
                } else if (rn.startsWith("paginae/atk/")) {
                    s.get("Combat").add(new Act(id, a.name, grp));
                } else if (rn.startsWith("paginae/music/")) {
                    s.get("Music & Emotes").add(new Act(id, a.name, "Music"));
                } else if (rn.startsWith("paginae/pose/")) {
                    s.get("Music & Emotes").add(new Act(id, a.name, "Emotes"));
                } else {
                    s.get("Unsorted").add(new Act(id, a.name, grp));
                }
            } catch (Loading e) {
            }
        }
        for (Map.Entry<String, List<Act>> e : s.entrySet()) {
            if (e.getKey().equals("Panels")) {
                continue;
            }
            List<String> order = ORDER.get(e.getKey());
            e.getValue().sort((x, y) -> {
                int g;
                if (order != null) {
                    int xi = order.indexOf(x.group), yi = order.indexOf(y.group);
                    g = Integer.compare(xi < 0 ? 99 : xi, yi < 0 ? 99 : yi);
                } else {
                    g = x.group.compareToIgnoreCase(y.group);
                }
                return g != 0 ? g : x.name.compareToIgnoreCase(y.name);
            });
        }
        for (FayteMacros.Macro m : FayteMacros.all()) {
            s.get("Macros").add(new Act("macro:" + m.id, m.name, ""));
        }
        return s;
    }

    private static final Map<String, List<String>> ORDER = new HashMap<>();

    static {
        ORDER.put("Toggles", Arrays.asList("Movement", "Climbing", "Misc", "Map"));
        ORDER.put("Music & Emotes", Arrays.asList("Music", "Emotes"));
    }

    public static String name(GameUI gui, String id) {
        if (id == null) {
            return "";
        } else if (id.startsWith("speed:")) {
            int i = Integer.parseInt(id.substring(6));
            return i >= 0 && i < SPEEDS.length ? SPEEDS[i] : id;
        } else if (id.startsWith("cmd:")) {
            String c = id.substring(4);
            for (String[] p : PANELS) {
                if (p[0].equals(c)) {
                    return p[1];
                }
            }
            for (String[] f : FAYTE) {
                if (f[0].equals(c)) {
                    return f[1];
                }
            }
            return c;
        } else if (id.equals("fayte:radius")) {
            return "Object radius";
        } else if (id.equals("fayte:abacus")) {
            return "Abacus";
        } else if (NAMES.containsKey(id)) {
            return NAMES.get(id);
        } else if (id.startsWith("fayte:")) {
            for (String[] f : FAYTE) {
                if (f[0].equals(id.substring(6))) {
                    return f[1];
                }
            }
        } else if (id.startsWith("macro:")) {
            FayteMacros.Macro m = FayteMacros.get(id.substring(6));
            return m == null ? "(deleted macro)" : m.name;
        } else if (id.startsWith("pag:")) {
            try {
                Resource.AButton a = Resource.load(id.substring(4)).layer(Resource.action);
                return a == null ? id.substring(4) : a.name;
            } catch (Loading e) {
                return "...";
            }
        }
        return id;
    }

    public static Tex icon(String id) {
        if (icons.containsKey(id)) {
            return icons.get(id);
        }
        String res = ICONS.get(id);
        if (id.startsWith("macro:")) {
            FayteMacros.Macro m = FayteMacros.get(id.substring(6));
            if (m != null && m.icon != null && !m.icon.startsWith("macro:")) {
                Tex it = icon(m.icon);
                if (it != null) {
                    return it;
                }
            }
            if (m != null) {
                for (FayteMacros.Step st : m.steps) {
                    if (st.kind.equals("act") && !st.value.startsWith("macro:")) {
                        return icon(st.value);
                    }
                }
            }
            return null;
        }
        if (res != null && res.startsWith("gfx/hud/")) {
            try {
                Tex t = new TexI(Resource.loadimg(res));
                icons.put(id, t);
                return t;
            } catch (Loading e) {
                return null;
            } catch (RuntimeException e) {
                icons.put(id, null);
                return null;
            }
        } else if (res != null) {
        } else if (id.startsWith("pag:")) {
            res = id.substring(4);
        } else if (id.startsWith("cmd:")) {
            res = "paginae/add/" + id.substring(4);
        } else if (id.equals("fayte:radius")) {
            res = "paginae/add/radius";
        } else if (id.startsWith("speed:")) {
            int i = Integer.parseInt(id.substring(6));
            res = "gfx/hud/meter/rmeter/" + SPEEDRES[Math.max(0, Math.min(3, i))] + "-on";
        }
        if (res == null) {
            icons.put(id, null);
            return null;
        }
        try {
            Resource r = Resource.load(res);
            Resource.Image img = r.layer(Resource.imgc);
            Tex t = img == null ? null : img.tex();
            icons.put(id, t);
            return t;
        } catch (Loading e) {
            return null;
        } catch (RuntimeException e) {
            icons.put(id, null);
            return null;
        }
    }

    private static boolean alreadyon(String id) {
        if (id.equals("fayte:footprints")) {
            return FayteConfig.outlineAll.get();
        } else if (id.equals("cmd:lockui")) {
            return FayteConfig.lockUi.get();
        }
        return false;
    }

    public static boolean toggleable(String id) {
        return id.startsWith("speed:")
                || id.startsWith("fayte:footprints")
                || (id.startsWith("pag:") && has(id.toLowerCase(), TOGGLEWORDS));
    }

    public static void run(GameUI gui, String id) {
        if (gui == null || id == null) {
            return;
        }
        if (id.startsWith("pag:")) {
            Glob.Pagina p = pagina(gui, id.substring(4));
            if (p != null && gui.menu != null) {
                gui.menu.use(p);
            } else {
                FayteMsg.say(name(gui, id) + " isn't available right now.");
            }
        } else if (id.startsWith("cmd:")) {
            try {
                gui.ui.cons.run(new String[] {id.substring(4)});
            } catch (Exception e) {
                FayteMsg.say("Could not run " + id.substring(4) + ": " + e.getMessage(), GameUI.MsgType.BAD);
            }
        } else if (id.startsWith("speed:")) {
            Speedget sg = speedget(gui);
            if (sg != null) {
                sg.wdgmsg("set", Integer.parseInt(id.substring(6)));
            }
        } else if (id.equals("fayte:radius")) {
            Config.toggleRadius();
        } else if (id.equals("fayte:abacus")) {
            OverviewTool.instance(gui.ui).toggle();
        } else if (id.startsWith("fayte:claim") || id.equals("fayte:pointer")) {
            if (gui.mainmenu != null) {
                GameUI.MenuButton b = id.equals("fayte:claimp")
                        ? gui.mainmenu.clab
                        : id.equals("fayte:claimt")
                                ? gui.mainmenu.towb
                                : id.equals("fayte:claimw") ? gui.mainmenu.warb : gui.mainmenu.ptrb;
                b.click();
            }
        } else if (id.equals("fayte:markers")) {
            FayteMarksWnd.toggle(gui);
        } else if (id.equals("fayte:equipment")) {
            gui.toggleequ();
        } else if (id.equals("fayte:footprints")) {
            FayteConfig.outlineAll.set(!FayteConfig.outlineAll.get());
            FayteMsg.say("Footprints " + (FayteConfig.outlineAll.get() ? "shown" : "hidden"));
        } else if (id.startsWith("macro:")) {
            FayteMacros.start(gui, id.substring(6));
        }
    }

    public static Speedget speedget(Widget w) {
        for (Widget c = w.child; c != null; c = c.next) {
            if (c instanceof Speedget) {
                return (Speedget) c;
            }
            Speedget s = speedget(c);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    private static final String STARTPREF = "fayte_start_toggles";

    private static String startkey() {
        String c = Config.currentCharName;
        return STARTPREF + "_" + Config.server + "_" + (c == null ? "" : c);
    }

    public static List<String> startlist() {
        String v = Utils.getpref(startkey(), null);
        if (v == null) {
            v = Utils.getpref(STARTPREF, "");
            if (!v.isEmpty() && Config.currentCharName != null && !Config.currentCharName.isEmpty()) {
                Utils.setpref(startkey(), v);
                Utils.setpref(STARTPREF, "");
            }
        }
        List<String> l = new ArrayList<>();
        for (String s : v.split("\\|")) {
            if (!s.isEmpty()) {
                l.add(s);
            }
        }
        return l;
    }

    public static boolean atstart(String id) {
        return startlist().contains(id);
    }

    public static void setstart(String id, boolean on) {
        List<String> l = startlist();
        l.remove(id);
        if (on) {
            l.add(id);
        }
        Utils.setpref(startkey(), String.join("|", l));
    }

    private static GameUI startgui = null;
    private static long startat = 0L;
    private static List<String> pending = null;

    public static void tick(GameUI gui) {
        if (startgui != gui) {
            startgui = gui;
            startat = System.currentTimeMillis();
            pending = new ArrayList<>(startlist());
        }
        if (pending == null || pending.isEmpty()) {
            return;
        }
        long el = System.currentTimeMillis() - startat;
        if (el < 4000L) {
            return;
        }
        for (String id : new ArrayList<>(pending)) {
            boolean ok = !id.startsWith("pag:") || pagina(gui, id.substring(4)) != null;
            if (id.startsWith("speed:") && speedget(gui) == null) {
                ok = false;
            }
            if (ok) {
                if (!alreadyon(id)) {
                    run(gui, id);
                    if (id.startsWith("pag:") && !TOGGLES.contains(id.substring(4))) {
                        FayteMsg.say(name(gui, id) + " is now turned on.", GameUI.getMsgColor(GameUI.MsgType.BAD));
                    }
                }
                pending.remove(id);
            }
        }
        if (el > 60000L) {
            pending.clear();
        }
    }

    public static List<String> ids(List<Act> l) {
        List<String> r = new ArrayList<>();
        for (Act a : l) {
            r.add(a.id);
        }
        return r;
    }

    public static List<String> words() {
        return Arrays.asList(TOGGLEWORDS);
    }
}
