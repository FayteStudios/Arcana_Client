package haven;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FayteMapSeen {
    private static final Map<String, Set<Long>> sets = new HashMap<>();
    private static final Set<String> dirty = new HashSet<>();
    private static long lastsave = 0L;

    public static boolean on() {
        return FayteConfig.mapPerChar.get();
    }

    private static String who() {
        String c = Config.currentCharName;
        return c == null || c.isEmpty() ? null : c;
    }

    private static File dir(FayteMapStore st) {
        return new File(st.dir(), "seen");
    }

    private static File file(FayteMapStore st, String ch) {
        return new File(dir(st), FaytePaths.safename(ch) + ".txt");
    }

    private static String key(FayteMapStore st, String ch) {
        return st.dir().getAbsolutePath() + "|" + ch;
    }

    private static Set<Long> set(FayteMapStore st, String ch) {
        synchronized (st) {
            synchronized (FayteMapSeen.class) {
                String k = key(st, ch);
                Set<Long> s = sets.get(k);
                if (s == null) {
                    s = new HashSet<>();
                    File f = file(st, ch);
                    if (f.exists()) {
                        try {
                            for (String l :
                                    new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                                l = l.trim();
                                if (!l.isEmpty()) {
                                    s.add(Long.parseLong(l));
                                }
                            }
                        } catch (Exception e) {
                            FayteLog.log("Map: could not read " + f + ": " + e);
                        }
                    } else if (!Utils.getprefb("fayte_map_seeded", false)) {
                        s.addAll(st.allids());
                        Utils.setprefb("fayte_map_seeded", true);
                        dirty.add(k);
                        FayteLog.log("Map: gave " + ch + " the map explored so far (" + s.size() + " grids)");
                    }
                    sets.put(k, s);
                }
                return s;
            }
        }
    }

    public static void mark(FayteMapStore st, Collection<Long> ids) {
        synchronized (st) {
            synchronized (FayteMapSeen.class) {
                String ch = who();
                if (!on() || ch == null || ids.isEmpty()) {
                    return;
                }
                Set<Long> s = set(st, ch);
                if (s.addAll(ids)) {
                    dirty.add(key(st, ch));
                }
            }
        }
    }

    public static boolean visible(FayteMapStore st, long id) {
        synchronized (st) {
            synchronized (FayteMapSeen.class) {
                String ch = who();
                if (!on() || ch == null) {
                    return true;
                }
                return set(st, ch).contains(id);
            }
        }
    }

    public static List<String> others(FayteMapStore st) {
        synchronized (st) {
            synchronized (FayteMapSeen.class) {
                List<String> ret = new ArrayList<>();
                String me = who();
                String[] fs = dir(st).list();
                if (fs != null) {
                    for (String f : fs) {
                        if (f.endsWith(".txt")) {
                            String n = f.substring(0, f.length() - 4);
                            if (me == null || !n.equals(FaytePaths.safename(me))) {
                                ret.add(n);
                            }
                        }
                    }
                }
                return ret;
            }
        }
    }

    public static int importfrom(FayteMapStore st, String other) {
        synchronized (st) {
            synchronized (FayteMapSeen.class) {
                String ch = who();
                if (ch == null) {
                    return 0;
                }
                Set<Long> mine = set(st, ch);
                Set<Long> theirs = set(st, other);
                int before = mine.size();
                mine.addAll(theirs);
                dirty.add(key(st, ch));
                save(true);
                return mine.size() - before;
            }
        }
    }

    public static int giveall(FayteMapStore st) {
        synchronized (st) {
            synchronized (FayteMapSeen.class) {
                String ch = who();
                if (ch == null) {
                    return 0;
                }
                Set<Long> mine = set(st, ch);
                int before = mine.size();
                mine.addAll(st.allids());
                dirty.add(key(st, ch));
                save(true);
                return mine.size() - before;
            }
        }
    }

    private static long lasthere = 0L;

    public static void here(GameUI gui) {
        long now = System.currentTimeMillis();
        if (!on() || now - lasthere < 5000L || gui.map == null) {
            return;
        }
        lasthere = now;
        FayteMapStore st = FayteMapStore.current();
        Gob pl = gui.map.player();
        if (st == null || pl == null || pl.rc == null) {
            return;
        }
        Coord gc = pl.rc.div(MCache.tilesz).div(MCache.cmaps);
        List<Long> ids = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                long id = FayteMapStore.gridid(gui.ui.sess.glob.map, gc.add(dx, dy));
                if (id != 0L) {
                    ids.add(id);
                    ids.add(st.resolve(id));
                }
            }
        }
        mark(st, ids);
    }

    public static synchronized void save(boolean force) {
        long now = System.currentTimeMillis();
        if (dirty.isEmpty() || (!force && now - lastsave < 30000L)) {
            return;
        }
        lastsave = now;
        for (String k : new ArrayList<>(dirty)) {
            int bar = k.lastIndexOf('|');
            File d = new File(new File(k.substring(0, bar)), "seen");
            File f = new File(d, FaytePaths.safename(k.substring(bar + 1)) + ".txt");
            StringBuilder sb = new StringBuilder();
            for (Long id : sets.get(k)) {
                sb.append(id).append('\n');
            }
            try {
                d.mkdirs();
                FaytePaths.write(f, sb.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                FayteLog.log("Map: could not save " + f + ": " + e);
            }
        }
        dirty.clear();
    }
}
