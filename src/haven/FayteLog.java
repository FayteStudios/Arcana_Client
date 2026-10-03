package haven;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class FayteLog {
    private static final long MAXSZ = 512L * 1024L;

    public static File file() {
        return new File(FaytePaths.fayte(), "fayte.log");
    }

    private static final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(5000);
    private static Thread writer = null;

    public static void log(String msg) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " " + msg + "\n";
        start();
        queue.offer(line);
    }

    private static synchronized void start() {
        if (writer != null) {
            return;
        }
        writer = new Thread(
                () -> {
                    while (true) {
                        try {
                            String first = queue.take();
                            List<String> batch = new ArrayList<>();
                            batch.add(first);
                            queue.drainTo(batch, 500);
                            write(batch);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                },
                "Fayte log writer");
        writer.setDaemon(true);
        writer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(FayteLog::drain, "Fayte log flush"));
    }

    private static void drain() {
        List<String> batch = new ArrayList<>();
        queue.drainTo(batch);
        if (!batch.isEmpty()) {
            write(batch);
        }
    }

    private static synchronized void write(List<String> lines) {
        try {
            File f = file();
            f.getParentFile().mkdirs();
            boolean append = !f.exists() || f.length() < MAXSZ;
            try (Writer w = new OutputStreamWriter(new FileOutputStream(f, append), StandardCharsets.UTF_8)) {
                for (String l : lines) {
                    w.write(l);
                }
            }
        } catch (Exception e) {
            System.out.println("Could not write fayte.log: " + e);
        }
    }

    private static final Set<String> onced = Collections.synchronizedSet(new HashSet<>());

    public static void once(String where, Throwable t) {
        String key = where + "|" + t.getClass().getName();
        if (onced.size() < 500 && onced.add(key)) {
            log(where + " failed (logged once)", t);
        }
    }

    public static void log(String msg, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        log(msg + ": " + sw);
    }
}
