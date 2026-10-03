package haven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class FayteGains {
    private static final String[] HUMOURS = {"blood", "phlegm", "ybile", "bbile"};
    private static final String[] HUMOURNAMES = {"Blood", "Phlegm", "Yellow Bile", "Black Bile"};
    private static final long BURST_MS = 3000;
    private static GameUI lastgui = null;
    private static long settleuntil = 0L;
    private static long lastcraveat = 0L;
    private static Map<String, Integer> last = null;
    private static final Map<String, int[]> burst = new LinkedHashMap<>();
    private static long burstat = 0;
    private static boolean craving = false;
    private static long cravingat = 0;
    private static final List<String> eaten = new ArrayList<>();
    private static long feaststart = 0;
    private static final Map<String, Integer> feastfoods = new TreeMap<>();
    private static final Map<String, int[]> feastgains = new LinkedHashMap<>();

    private static String label(String key) {
        if (key.endsWith("#b")) {
            key = key.substring(0, key.length() - 2);
        }
        for (int i = 0; i < HUMOURS.length; i++) {
            if (HUMOURS[i].equals(key)) {
                return HUMOURNAMES[i];
            }
        }
        String n = CharWnd.attrnm.get(key);
        return n != null ? n : key;
    }

    private static boolean humour(String key) {
        if (key.endsWith("#b")) {
            key = key.substring(0, key.length() - 2);
        }
        for (String h : HUMOURS) {
            if (h.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String fmt(String key, int v) {
        return humour(key) ? Integer.toString(v / 1000) : Integer.toString(v);
    }

    private static Map<String, Integer> snapshot(Glob glob) {
        Map<String, Integer> s = new HashMap<>();
        synchronized (glob.cattr) {
            for (String h : HUMOURS) {
                Glob.CAttr a = glob.cattr.get(h);
                if (a != null) {
                    s.put(h, a.comp);
                    s.put(h + "#b", a.base);
                }
            }
            for (String p : CharWnd.attrnm.keySet()) {
                Glob.CAttr a = glob.cattr.get(p);
                if (a != null) {
                    s.put(p, a.base);
                }
            }
        }
        return s;
    }

    private static String craved = null;

    public static void craving(Indir<Resource> r) {
        craved = null;
        try {
            if (r != null) {
                craved = FayteCraving.cravname(r.get());
            }
        } catch (Loading e) {
        }
        craving = true;
        cravingat = System.currentTimeMillis();
    }

    public static void ate(String name) {
        if (name == null) {
            return;
        }
        eaten.add(name);
        if (eaten.size() > 20) {
            eaten.remove(0);
        }
        if (feaststart != 0) {
            feastfoods.merge(name, 1, Integer::sum);
        }
    }

    public static void feast(boolean on) {
        long now = System.currentTimeMillis();
        if (on && feaststart == 0) {
            feaststart = now;
            feastfoods.clear();
            feastgains.clear();
        } else if (!on && feaststart != 0) {
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[] {
                "Duration", ((now - feaststart) / 60000) + " min " + (((now - feaststart) / 1000) % 60) + " s"
            });
            for (Map.Entry<String, int[]> g : feastgains.entrySet()) {
                rows.add(new String[] {
                    label(g.getKey()), fmt(g.getKey(), g.getValue()[0]) + " \u2192 " + fmt(g.getKey(), g.getValue()[1])
                });
            }
            StringBuilder foods = new StringBuilder();
            for (Map.Entry<String, Integer> f : feastfoods.entrySet()) {
                foods.append(foods.length() > 0 ? ", " : "")
                        .append(FayteWikiGlance.linkify(f.getKey()))
                        .append(f.getValue() > 1 ? " \u00d7" + f.getValue() : "");
            }
            if (foods.length() > 0) {
                rows.add(new String[] {"Ate", foods.toString()});
            }
            if (!feastgains.isEmpty()) {
                FayteAlmanac.journal("Feast", rows);
            }
            feaststart = 0;
        }
    }

    public static void tick(GameUI gui) {
        if (gui.ui == null || gui.ui.sess == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Map<String, Integer> s = snapshot(gui.ui.sess.glob);
        if (gui != lastgui) {
            lastgui = gui;
            last = null;
            burst.clear();
            settleuntil = now + 20000L;
            feaststart = 0;
            feastfoods.clear();
            feastgains.clear();
            eaten.clear();
            craving = false;
            craved = null;
        }
        if (now < settleuntil) {
            last = s;
            return;
        }
        if (last != null) {
            for (Map.Entry<String, Integer> e : s.entrySet()) {
                Integer o = last.get(e.getKey());
                if (o != null && e.getValue() > o && o > 0) {
                    int d = e.getValue() - o;
                    boolean base = e.getKey().endsWith("#b");
                    if ((humour(e.getKey()) && d > 30000) || (!humour(e.getKey()) && d > 20)) {
                        continue;
                    }
                    if (base) {
                        if (feaststart != 0) {
                            int[] fg = feastgains.get(e.getKey());
                            if (fg == null) {
                                feastgains.put(e.getKey(), new int[] {o, e.getValue()});
                            } else {
                                fg[1] = e.getValue();
                            }
                        }
                        continue;
                    }
                    int[] b = burst.get(e.getKey());
                    if (b == null) {
                        burst.put(e.getKey(), new int[] {o, e.getValue()});
                    } else {
                        b[1] = e.getValue();
                    }
                    burstat = now;
                    if (feaststart != 0 && !humour(e.getKey())) {
                        int[] fg = feastgains.get(e.getKey());
                        if (fg == null) {
                            feastgains.put(e.getKey(), new int[] {o, e.getValue()});
                        } else {
                            fg[1] = e.getValue();
                        }
                    }
                }
            }
        }
        last = s;
        if (!burst.isEmpty() && now - burstat > BURST_MS) {
            flushburst(now);
        }
    }

    private static void flushburst(long now) {
        boolean crave = craving && now - cravingat < 10000;
        boolean hum = false;
        List<String[]> rows = new ArrayList<>();
        for (Map.Entry<String, int[]> e : burst.entrySet()) {
            int[] v = e.getValue();
            int d = v[1] - v[0];
            if (humour(e.getKey()) && d < 50) {
                continue;
            }
            hum |= humour(e.getKey());
            String delta = humour(e.getKey()) ? String.format("+%.1f", d / 1000.0) : "+" + d;
            rows.add(new String[] {
                label(e.getKey()), fmt(e.getKey(), v[0]) + " \u2192 " + fmt(e.getKey(), v[1]) + "   (" + delta + ")"
            });
        }
        String title = crave && hum ? "Craving fulfilled" : (hum ? "Humours raised" : "Proficiency up");
        List<String[]> jrows = new ArrayList<>(rows);
        if (crave && hum) {
            String ate = eaten.isEmpty() ? null : eaten.get(eaten.size() - 1);
            boolean exact =
                    ate != null && craved != null && FayteCraving.norm(ate).equals(FayteCraving.norm(craved));
            title = ate == null || craved == null
                    ? "Craving fulfilled"
                    : (exact ? "Craving: exact" : "Craving: good enough");
            if (craved != null) {
                jrows.add(0, new String[] {"Craving", craved});
            }
            if (ate != null) {
                jrows.add(1, new String[] {"Ate", ate});
            }
            if (ate != null && craved != null) {
                jrows.add(2, new String[] {
                    "Match",
                    exact ? "Exactly what you craved (+1 to every humour)" : "Same food group (+1 to one humour)"
                });
            }
        }
        burst.clear();
        if (rows.isEmpty()) {
            return;
        }
        if (crave && hum) {
            craving = false;
            lastcraveat = now;
        } else if (hum && now - lastcraveat < 10000L) {
            return;
        }
        if (!crave) {
            List<String[]> keep = new ArrayList<>();
            for (String[] r : jrows) {
                boolean h = false;
                for (String hn : HUMOURNAMES) {
                    h |= hn.equals(r[0]);
                }
                if (!h) {
                    keep.add(r);
                }
            }
            jrows = keep;
            title = "Proficiency up";
        }
        if (FayteModules.ALMANAC.on() && !jrows.isEmpty()) {
            FayteAlmanac.journal(title, jrows);
        }
    }
}
