package haven;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public class FayteDeath {
    private static final Pattern DIED = Pattern.compile(
            "(?i).*(knocked (you )?out|knocked unconscious|you (have )?(been )?knocked|you (are|were|have been)"
                    + " (unconscious|defeated|beaten)|you faint|you pass(ed)? out).*");
    private static final Pattern HINT = Pattern.compile(
            "(?i).*\\b(die|died|dead|death|kill|killed|slain|perish|knock|knocked|unconscious|faint|defeated|beaten)\\b.*");
    private static final Pattern KOPOSE =
            Pattern.compile("(?i).*/(knock|knocked|knockout|ko|faint|fainted|unconscious|dead)$");
    private static String lastposes = null;
    private static Gob pendgob = null;
    private static List<ResData> pending = null;

    public static void poses(Gob g, Collection<ResData> poses) {
        try {
            pending = null;
            GameUI gui = UI.instance == null ? null : UI.instance.gui;
            if (gui == null || gui.map == null || g == null || poses == null || g != gui.map.player()) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            boolean ko = false;
            boolean loading = false;
            for (ResData rd : poses) {
                String n;
                try {
                    n = rd.res.get().name;
                } catch (Loading e) {
                    loading = true;
                    continue;
                }
                sb.append(sb.length() > 0 ? ", " : "").append(n);
                ko |= KOPOSE.matcher(n).matches();
            }
            if (loading) {
                pendgob = g;
                pending = new ArrayList<>(poses);
            }
            String all = sb.toString();
            if (!all.equals(lastposes)) {
                lastposes = all;
                if (ko && FayteConfig.diag()) {
                    FayteLog.log("Death check: your poses are now [" + all + "]");
                }
                FayteAuto.posed(gui, all);
            }
            if (ko) {
                mark(gui);
            }
        } catch (RuntimeException e) {
            FayteLog.once("FayteDeath.poses", e);
        }
    }

    private static Coord lasttile = null;
    private static long lastat = 0L;
    private static long marked = 0L;

    public static void tick(GameUI gui) {
        if (pending != null) {
            poses(pendgob, pending);
        }
        long now = System.currentTimeMillis();
        if (now - lastat < 2000L) {
            return;
        }
        lastat = now;
        Coord t = WorldMapMarkers.playertile(gui);
        if (t != null) {
            lasttile = t;
        }
    }

    public static void onmessage(GameUI gui, String msg) {
        if (msg == null || gui == null) {
            return;
        }
        if (FayteConfig.diag() && HINT.matcher(msg).matches()) {
            FayteLog.log("Death check: game message \"" + msg + "\"");
        }
        if (DIED.matcher(msg).matches()) {
            mark(gui);
        }
    }

    public static void mark(GameUI gui) {
        long now = System.currentTimeMillis();
        if (now - marked < 60000L || !FayteModules.WORLDMAP.on()) {
            return;
        }
        Coord t = WorldMapMarkers.playertile(gui);
        if (t == null) {
            t = lasttile;
        }
        if (t == null) {
            FayteLog.log("Death check: no position known, so no marker");
            return;
        }
        marked = now;
        String name = "Knocked out " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
        WorldMapMarkers.Marker m = WorldMapMarkers.addat(gui, "custom", name, t);
        if (m != null) {
            FayteMsg.say("Added a World Map marker where you were knocked out.");
            FayteLog.log("Death check: marker added at " + t);
        }
    }
}
