package haven;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class FayteWatchdog {
    private static volatile long beat = 0L;
    private static Thread thread = null;
    private static boolean dumped = false;

    public static void beat() {
        beat = System.currentTimeMillis();
        if (thread == null) {
            start();
        }
    }

    public static void stop() {
        beat = 0L;
    }

    private static synchronized void start() {
        if (thread != null) {
            return;
        }
        thread = new Thread(
                () -> {
                    while (true) {
                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException e) {
                            return;
                        }
                        long b = beat;
                        long now = System.currentTimeMillis();
                        if (b != 0L && now - b > 6000L) {
                            if (!dumped) {
                                dumped = true;
                                dump(now - b);
                            }
                        } else {
                            dumped = false;
                        }
                    }
                },
                "Fayte freeze watch");
        thread.setDaemon(true);
        thread.start();
    }

    private static void dump(long ms) {
        StringBuilder sb = new StringBuilder();
        sb.append("Arcana stopped responding for ")
                .append(ms / 1000)
                .append(" s at ")
                .append(new Date())
                .append("\n\n");
        for (Map.Entry<Thread, StackTraceElement[]> e :
                Thread.getAllStackTraces().entrySet()) {
            Thread t = e.getKey();
            sb.append("\"")
                    .append(t.getName())
                    .append("\" ")
                    .append(t.getState())
                    .append("\n");
            for (StackTraceElement el : e.getValue()) {
                sb.append("    at ").append(el).append("\n");
            }
            sb.append("\n");
        }
        File f = new File(
                FaytePaths.fayte(), "freeze-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".txt");
        try {
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            FayteLog.log("Freeze: the game stopped responding for " + ms / 1000 + " s; details saved to " + f);
        } catch (Exception e) {
            FayteLog.log("Freeze: could not save details: " + e);
        }
    }
}
