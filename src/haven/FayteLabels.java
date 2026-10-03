package haven;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class FayteLabels {
    private static final Map<String, String> labels = new TreeMap<>();
    private static String loadedfor = null;
    public static int version = 0;
    private static final ArrayDeque<Object[]> pending = new ArrayDeque<>();

    public static boolean on() {
        return FayteModules.CONTAINERS.on();
    }

    private static String who() {
        return String.valueOf(Config.server) + "/"
                + (Config.currentCharName == null || Config.currentCharName.isEmpty()
                        ? "default"
                        : Config.currentCharName);
    }

    private static File file() {
        String[] w = who().split("/", 2);
        return new File(
                new File(new File(FaytePaths.fayte(), "labels"), FaytePaths.safename(w[0])),
                FaytePaths.safename(w[1]) + ".tsv");
    }

    private static synchronized void ensure() {
        String w = who();
        if (w.equals(loadedfor)) {
            return;
        }
        loadedfor = w;
        labels.clear();
        version++;
        File f = file();
        if (f.exists()) {
            try {
                for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    int t = line.indexOf('\t');
                    if (t > 0) {
                        labels.put(line.substring(0, t), line.substring(t + 1).trim());
                    }
                }
            } catch (Exception e) {
                FayteLog.log("Labels: could not read " + f + ": " + e);
            }
        }
    }

    public static synchronized String get(String key) {
        if (key == null) {
            return null;
        }
        ensure();
        return labels.get(key);
    }

    public static synchronized void set(String key, String label) {
        if (key == null) {
            return;
        }
        ensure();
        String l =
                label == null ? "" : label.replace('\t', ' ').replace('\n', ' ').trim();
        if (l.isEmpty()) {
            labels.remove(key);
        } else {
            labels.put(key, l);
        }
        save();
    }

    private static void save() {
        version++;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : labels.entrySet()) {
            sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        try {
            File f = file();
            f.getParentFile().mkdirs();
            FaytePaths.write(f, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.log("Labels: could not save: " + e);
        }
    }

    public static synchronized void opened(String key) {
        pending.clear();
        pending.addLast(new Object[] {key, System.currentTimeMillis(), ""});
    }

    public static synchronized void opened(WItem w) {
        pending.clear();
        openedmore(w);
    }

    public static synchronized void openedmore(WItem w) {
        String key = itemkey(w);
        if (key == null) {
            return;
        }
        String nm = FayteSort.name(w);
        pending.addLast(new Object[] {
            key, System.currentTimeMillis(), nm == null ? "" : nm.toLowerCase().trim()
        });
        while (pending.size() > 64) {
            pending.removeFirst();
        }
    }

    public static synchronized String claim(String caption) {
        long now = System.currentTimeMillis();
        String cap = caption == null ? "" : caption.toLowerCase().trim();
        for (Iterator<Object[]> it = pending.iterator(); it.hasNext(); ) {
            Object[] p = it.next();
            if (now - (Long) p[1] >= 4000) {
                it.remove();
                continue;
            }
            String nm = (String) p[2];
            if (nm.isEmpty() || (!cap.isEmpty() && (cap.equals(nm) || cap.contains(nm) || nm.contains(cap)))) {
                it.remove();
                return (String) p[0];
            }
        }
        return null;
    }

    private static Object[] carried = null;

    public static synchronized void left(Inventory inv, WItem w) {
        if (inv.fkey == null || w == null || w.server_c == null) {
            return;
        }
        String key = inv.fkey + "/" + w.server_c.x + "," + w.server_c.y;
        String l = get(key);
        if (l == null) {
            return;
        }
        carried = new Object[] {l, w.item.resname(), System.currentTimeMillis()};
        set(key, null);
    }

    public static synchronized void arrived(Inventory inv, WItem w) {
        if (carried == null || inv.fkey == null || w == null || w.server_c == null) {
            return;
        }
        if (System.currentTimeMillis() - (Long) carried[2] > 15000L) {
            carried = null;
            return;
        }
        String rn = w.item.resname();
        if (rn == null || !rn.equals(carried[1])) {
            return;
        }
        set(inv.fkey + "/" + w.server_c.x + "," + w.server_c.y, (String) carried[0]);
        carried = null;
    }

    public static String itemkey(WItem w) {
        if (!(w.parent instanceof Inventory) || w.server_c == null) {
            return null;
        }
        String ck = ((Inventory) w.parent).fkey;
        return ck == null ? null : ck + "/" + w.server_c.x + "," + w.server_c.y;
    }

    public static void prompt(Widget near, String key, String what) {
        if (key == null || near.ui == null || near.ui.gui == null) {
            return;
        }
        new FayteNameWnd(near.ui.gui, key, what, get(key));
    }
}
