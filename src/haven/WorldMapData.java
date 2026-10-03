package haven;

import java.io.File;
import java.util.Set;

public class WorldMapData {
    public static final long REFRESH_MS = 5000L;
    public static final int MINTILES = 6;
    private static volatile WorldMapIndex.Result result = null;
    private static volatile String status = null;
    private static volatile int cleanable = 0;
    private static Thread worker = null;
    private static long lastbuild = 0L;
    private static String builtfor = null;

    public static WorldMapIndex.Result result() {
        return result;
    }

    public static String status() {
        return status;
    }

    public static int cleanable() {
        return cleanable;
    }

    public static File mapfolder() {
        return new File(String.format("%s/map/%s/", Config.userhome, Config.server));
    }

    public static synchronized boolean running() {
        return worker != null && worker.isAlive();
    }

    private static synchronized void start(Runnable r, String name) {
        worker = new Thread(null, r, name, 16L << 20);
        worker.setDaemon(true);
        worker.start();
    }

    public static synchronized void refresh(final long current, boolean force) {
        if (running()) {
            return;
        }
        String srv = String.valueOf(Config.server);
        if (!srv.equals(builtfor)) {
            builtfor = srv;
            result = null;
            cleanable = 0;
        }
        if (!force && result != null && System.currentTimeMillis() - lastbuild < REFRESH_MS) {
            return;
        }
        final FayteMapStore st = FayteMapStore.current();
        final boolean first = result == null;
        lastbuild = System.currentTimeMillis();
        if (first) {
            status = "Loading map...";
        }
        start(
                () -> {
                    try {
                        if (!st.migrated()) {
                            st.migrate(s -> status = s);
                        }
                        Set<Long> keep = WorldMapMarkers.segs();
                        WorldMapIndex.Result r = st.result(current, keep, MINTILES);
                        result = r;
                        status = r.islands.isEmpty() ? "No saved map tiles yet" : null;
                        if (first) {
                            cleanable = st.cleanable().size();
                        }
                    } catch (InterruptedException e) {
                    } catch (RuntimeException e) {
                        status = "Map loading failed: " + e;
                        FayteLog.log("World map: " + e);
                    }
                },
                "World map loader");
    }

    public static synchronized boolean cleanup(final String keep) {
        if (running() || cleanable == 0) {
            return false;
        }
        final FayteMapStore st = FayteMapStore.current();
        status = "Cleaning up old map files...";
        start(
                () -> {
                    try {
                        int n = st.cleanup(keep, s -> status = s);
                        cleanable = 0;
                        status = null;
                        FayteMsg.say("Deleted " + n + " old map folders.");
                    } catch (RuntimeException e) {
                        status = "Clean up failed: " + e;
                        FayteLog.log("World map cleanup: " + e);
                    }
                },
                "World map cleanup");
        return true;
    }
}
