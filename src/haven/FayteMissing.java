package haven;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class FayteMissing {
    public static final String DONE = "#done\t";
    private static Set<String> known = null;

    public static File file() {
        return new File(FaytePaths.fayte(), "missing_entries.txt");
    }

    private static void load() {
        known = new HashSet<>();
        File f = file();
        if (f.exists()) {
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith(DONE)) {
                        line = line.substring(DONE.length());
                    }
                    if (!line.startsWith("#")) {
                        String[] p = line.split("\t");
                        if (p.length >= 2) {
                            known.add(p[0] + "|" + p[1].toLowerCase());
                        }
                    }
                }
            } catch (Exception e) {
                FayteLog.log("Missing list: could not read " + f + ": " + e);
            }
        }
    }

    public static synchronized void dismiss(String kind, String name, boolean done) {
        File f = file();
        if (!f.exists()) {
            return;
        }
        StringBuilder out = new StringBuilder();
        try (BufferedReader r =
                new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                boolean was = line.startsWith(DONE);
                String body = was ? line.substring(DONE.length()) : line;
                String[] p = body.split("\t");
                if (!body.startsWith("#") && p.length >= 2 && p[0].equals(kind) && p[1].equalsIgnoreCase(name)) {
                    line = (done ? DONE : "") + body;
                }
                out.append(line).append('\n');
            }
        } catch (Exception e) {
            FayteLog.log("Missing list: could not read " + f + ": " + e);
            return;
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(out.toString());
        } catch (Exception e) {
            FayteLog.log("Missing list: could not write " + f + ": " + e);
        }
    }

    public static synchronized void note(String kind, String name, String res) {
        if (name == null || name.isEmpty()) {
            return;
        }
        if (known == null) {
            load();
        }
        if (known.add(kind + "|" + name.toLowerCase())) {
            File f = file();
            boolean fresh = !f.exists();

            try {
                f.getParentFile().mkdirs();
                try (Writer w = new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8)) {
                    if (fresh) {
                        w.write("# Things the Fayte client found no wiki page or entry for.\n");
                        w.write("# Fix one by adding a wiki page, an entry file in entries\\, or a line in"
                                + " aliases.txt.\n");
                        w.write("# kind\tname\tresource\tfirst seen\n");
                    }
                    w.write(kind + "\t" + name + "\t" + (res == null ? "" : res) + "\t"
                            + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "\n");
                }
            } catch (Exception e) {
                FayteLog.log("Missing list: could not write " + f + ": " + e);
            }
        }
    }
}
