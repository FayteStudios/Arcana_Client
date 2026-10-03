package haven;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

public class FayteDump {
    private static GameUI done = null;
    private static long since = 0L;

    private static long lastmem = 0L;

    private static final Map<Window, long[]> stations = new WeakHashMap<>();
    private static final Map<Window, Map<String, String>> seenvals = new WeakHashMap<>();
    private static final Set<String> stationsdone = new HashSet<>();
    private static final Map<Long, String> objstate = new HashMap<>();
    private static final String[] OBJWORDS = {
        "smelter",
        "furnace",
        "forge",
        "cask",
        "kiln",
        "oven",
        "smoker",
        "clamp",
        "sawbuck",
        "timber",
        "crucible",
        "stove",
        "fireplace",
        "ttub",
        "tub",
        "rack",
        "dframe",
        "trough",
        "support",
        "haystack",
        "hay",
        "coop",
        "skep",
        "compost",
        "field",
        "herbpot",
        "cauldron",
        "anvil",
        "loom",
        "grind",
        "gem",
        "whittl",
        "carpent",
        "cotton",
        "churn",
        "conserv",
        "alchemy",
        "woodpile",
        "pile",
        "bonfire",
        "fire",
        "plants/"
    };
    private static long lastobj = 0L;
    private static long lastvals = 0L;

    private static void write(String text) {
        try {
            File f = new File(FaytePaths.fayte(), "stations_dump.txt");
            Files.write(
                    f.toPath(),
                    text.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (Exception e) {
            FayteLog.log("Station dump failed: " + e);
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private static void stationtick(GameUI gui, long now) {
        boolean check = now - lastvals > 2000L;
        if (check) {
            lastvals = now;
        }
        for (Widget w = gui.child; w != null; w = w.next) {
            if (w.getClass() == Window.class && w.visible && ((Window) w).cap != null && w != gui.invwnd) {
                Window win = (Window) w;
                long[] st = stations.get(win);
                if (st == null) {
                    stations.put(win, new long[] {now, 0});
                } else if (st[1] == 0 && now - st[0] > 500L) {
                    st[1] = 1;
                    station(gui, win);
                    seenvals.put(win, values(win));
                } else if (st[1] == 1 && check) {
                    Map<String, String> old = seenvals.get(win);
                    Map<String, String> cur = values(win);
                    if (old != null && !cur.equals(old)) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("-- ")
                                .append(win.cap.text)
                                .append(" changed ")
                                .append(stamp())
                                .append(" (")
                                .append((now - st[0]) / 1000L)
                                .append(" s after opening)\n");
                        for (Map.Entry<String, String> e : cur.entrySet()) {
                            String ov = old.get(e.getKey());
                            if (!e.getValue().equals(ov)) {
                                sb.append("  ")
                                        .append(e.getKey())
                                        .append(": ")
                                        .append(ov == null ? "(new)" : ov)
                                        .append(" -> ")
                                        .append(e.getValue())
                                        .append("\n");
                            }
                        }
                        for (String k : old.keySet()) {
                            if (!cur.containsKey(k)) {
                                sb.append("  ")
                                        .append(k)
                                        .append(": ")
                                        .append(old.get(k))
                                        .append(" -> (gone)\n");
                            }
                        }
                        write(sb.toString());
                    }
                    seenvals.put(win, cur);
                }
            }
        }
        if (now - lastobj > 2000L) {
            lastobj = now;
            objtick(gui);
        }
    }

    private static Map<String, String> values(Window w) {
        Map<String, String> m = new TreeMap<>();
        values(w, "", m);
        return m;
    }

    private static void values(Widget w, String path, Map<String, String> m) {
        int n = 0;
        for (Widget c = w.child; c != null; c = c.next) {
            String k = path + c.getClass().getSimpleName() + "#" + (n++);
            try {
                if (c instanceof Label) {
                    m.put(k, String.valueOf(((Label) c).texts));
                } else if (c instanceof VMeter) {
                    Object tt = c.tooltip;
                    m.put(k, ((VMeter) c).amount + (tt instanceof Text ? " \"" + ((Text) tt).text + "\"" : ""));
                } else if (c instanceof WItem) {
                    GItem it = ((WItem) c).item;
                    m.put(
                            path + "item@" + ((WItem) c).server_c,
                            FayteAlmanac.itemname(it) + " meter=" + it.meter + " num=" + it.num);
                    continue;
                }
            } catch (Exception e) {
                FayteLog.once("FayteDump.values", e);
            }
            if (!(c instanceof GItem)) {
                values(c, k + "/", m);
            }
        }
    }

    private static void objtick(GameUI gui) {
        if (gui.map == null) {
            return;
        }
        Gob pl = gui.map.player();
        if (pl == null || pl.rc == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob g : gui.ui.sess.glob.oc) {
                if (g.rc == null || g.rc.dist(pl.rc) > 11 * 12) {
                    continue;
                }
                String rn = FayteMsg.resname(g);
                if (rn == null || !rn.startsWith("gfx/terobjs/")) {
                    continue;
                }
                String low = rn.toLowerCase();
                boolean want = false;
                for (String k : OBJWORDS) {
                    if (low.contains(k)) {
                        want = true;
                        break;
                    }
                }
                if (!want) {
                    continue;
                }
                StringBuilder state = new StringBuilder();
                ResDrawable rd = g.getattr(ResDrawable.class);
                if (rd != null && rd.sdt != null && rd.sdt.blob != null) {
                    state.append("state=");
                    for (byte b : rd.sdt.blob) {
                        state.append(String.format("%02x", b & 0xff));
                    }
                }
                List<String> ols = new ArrayList<>();
                synchronized (g.ols) {
                    for (Gob.Overlay ol : g.ols) {
                        if (ol.res == null) {
                            ols.add("client");
                            continue;
                        }
                        try {
                            Resource r = ol.res.get();
                            StringBuilder hx = new StringBuilder();
                            if (ol.sdt != null && ol.sdt.blob != null) {
                                for (byte x : ol.sdt.blob) {
                                    hx.append(String.format("%02x", x & 0xff));
                                }
                            }
                            ols.add((r == null ? "?" : r.name) + (hx.length() > 0 ? "(" + hx + ")" : ""));
                        } catch (Loading e) {
                            ols.add("loading");
                        }
                    }
                }
                if (!ols.isEmpty()) {
                    state.append(" overlays=").append(ols);
                }
                String st = state.toString();
                String old = objstate.get(g.id);
                if (!st.equals(old)) {
                    objstate.put(g.id, st);
                    sb.append("~~ object ")
                            .append(rn)
                            .append(" #")
                            .append(g.id)
                            .append(" ")
                            .append(stamp())
                            .append(old == null ? " first seen: " : " changed: ")
                            .append(old == null ? "" : old + " -> ")
                            .append(st)
                            .append("\n");
                }
            }
        }
        if (objstate.size() > 5000) {
            objstate.clear();
        }
        if (sb.length() > 0) {
            write(sb.toString());
        }
    }

    private static void station(GameUI gui, Window w) {
        String cap = w.cap.text;
        StringBuilder sb = new StringBuilder();
        sb.append("== ").append(cap).append(" (opened) ").append(stamp()).append("\n");
        String obj = FayteTools.lastobject(gui);
        if (obj != null) {
            sb.append("last clicked object: ").append(obj).append("\n");
        }
        if (stationsdone.add(cap)) {
            tree(w, 1, sb);
        } else {
            for (Map.Entry<String, String> e : values(w).entrySet()) {
                sb.append("  ")
                        .append(e.getKey())
                        .append(": ")
                        .append(e.getValue())
                        .append("\n");
            }
        }
        write(sb.append("\n").toString());
    }

    private static void tree(Widget w, int depth, StringBuilder sb) {
        for (Widget c = w.child; c != null; c = c.next) {
            StringBuilder l = new StringBuilder();
            for (int i = 0; i < depth; i++) {
                l.append("  ");
            }
            l.append(
                    c.getClass().getSimpleName().isEmpty()
                            ? c.getClass().getName()
                            : c.getClass().getSimpleName());
            try {
                if (c instanceof Label) {
                    l.append(" \"").append(((Label) c).texts).append("\"");
                } else if (c instanceof VMeter) {
                    l.append(" amount=")
                            .append(((VMeter) c).amount)
                            .append(" stat=")
                            .append(((VMeter) c).stat);
                } else if (c instanceof Inventory) {
                    l.append(" size=").append(((Inventory) c).isz);
                } else if (c instanceof WItem) {
                    GItem it = ((WItem) c).item;
                    l.append(" \"")
                            .append(FayteAlmanac.itemname(it))
                            .append("\" res=")
                            .append(it.resname())
                            .append(" meter=")
                            .append(it.meter)
                            .append(" num=")
                            .append(it.num);
                } else if (c instanceof GItem) {
                    GItem it = (GItem) c;
                    l.append(" \"")
                            .append(FayteAlmanac.itemname(it))
                            .append("\" meter=")
                            .append(it.meter);
                } else if (c instanceof Button && ((Button) c).text != null) {
                    l.append(" \"").append(((Button) c).text.text).append("\"");
                }
            } catch (Exception e) {
                l.append(" (").append(e.getClass().getSimpleName()).append(")");
            }
            Object tt = c.tooltip;
            if (tt instanceof Text) {
                l.append(" tip=\"").append(((Text) tt).text).append("\"");
            } else if (tt instanceof String) {
                l.append(" tip=\"").append(tt).append("\"");
            }
            sb.append(l).append("\n");
            tree(c, depth + 1, sb);
        }
    }

    public static void tick(GameUI gui) {
        if (!FayteConfig.DEV) {
            return;
        }
        long nowm = System.currentTimeMillis();
        if (FayteConfig.diag()) {
            stationtick(gui, nowm);
        }
        if (nowm - lastmem > 300000L) {
            lastmem = nowm;
            Runtime rt = Runtime.getRuntime();
            long live = 0L;
            for (MemoryPoolMXBean mp : ManagementFactory.getMemoryPoolMXBeans()) {
                MemoryUsage cu = mp.getCollectionUsage();
                if (cu != null && mp.getType() == MemoryType.HEAP) {
                    live += cu.getUsed();
                }
            }
            FayteLog.log(String.format(
                    "Memory: %d MB in use after last cleanup, %d MB now, %d MB max",
                    live >> 20, (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20));
        }
        if (done == gui) {
            return;
        }
        long now = System.currentTimeMillis();
        if (since == 0L) {
            since = now;
            return;
        }
        if (now - since < 30000L) {
            return;
        }
        done = gui;
        since = 0L;
        try {
            dump(gui);
        } catch (Exception e) {
            FayteLog.log("Dump failed: " + e);
        }
    }

    private static void dump(GameUI gui) throws Exception {
        Glob glob = gui.ui.sess.glob;
        List<Glob.Pagina> pags;
        synchronized (glob.paginae) {
            pags = new ArrayList<>(glob.paginae);
        }
        StringBuilder sb = new StringBuilder("# resource\tname\tparent\tad\thotkey\n");
        for (Glob.Pagina p : pags) {
            try {
                Resource r = p.res();
                Resource.AButton a = r.layer(Resource.action);
                sb.append(r.name).append('\t');
                if (a != null) {
                    sb.append(a.name)
                            .append('\t')
                            .append(a.parent == null ? "" : a.parent.name)
                            .append('\t')
                            .append(a.ad == null ? "" : String.join(" ", a.ad))
                            .append('\t')
                            .append(a.hk);
                }
                sb.append('\n');
            } catch (Loading e) {
                sb.append("(loading)\n");
            }
        }
        File d = FaytePaths.fayte();
        Files.write(new File(d, "paginae_dump.tsv").toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder cb = new StringBuilder("# attribute\tbase\tcomp\n");
        synchronized (glob.cattr) {
            for (Map.Entry<String, Glob.CAttr> e : glob.cattr.entrySet()) {
                cb.append(e.getKey())
                        .append('\t')
                        .append(e.getValue().base)
                        .append('\t')
                        .append(e.getValue().comp)
                        .append('\n');
            }
        }
        Files.write(new File(d, "cattr_dump.tsv").toPath(), cb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
