package haven;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class FayteIconLibrary {
    public static final String MARKER = ".library";
    private static final byte[] SIG = "Haven Resource 1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    public static final String INDEX = "index.tsv";

    public static class Stats {
        public int resources;
        public int named;
        public int byres;
        final StringBuilder index = new StringBuilder();
    }

    public static Map<String, String> index() {
        Map<String, String> ret = new HashMap<>();
        File f = new File(FaytePaths.icons(), INDEX);
        if (f.exists()) {
            try {
                for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    int t = line.indexOf('\t');
                    if (t > 0) {
                        ret.put(line.substring(0, t), line.substring(t + 1).trim());
                    }
                }
            } catch (Exception e) {
                FayteLog.once("FayteIconLibrary.index", e);
            }
        }
        return ret;
    }

    private static class Parsed {
        byte[] png;
        String action;
        String tooltip;
    }

    public static File resdir() {
        return new File(FaytePaths.icons(), "res");
    }

    public static boolean built() {
        return new File(FaytePaths.icons(), MARKER).exists() && new File(FaytePaths.icons(), INDEX).exists();
    }

    private static int indexof(byte[] d, byte[] pat, int from, int to) {
        outer:
        for (int i = from; i <= to - pat.length; i++) {
            for (int k = 0; k < pat.length; k++) {
                if (d[i + k] != pat[k]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static String cstr(byte[] d, int[] pos, int end) {
        int s = pos[0];
        int e = s;
        while (e < end && d[e] != 0) {
            e++;
        }
        pos[0] = Math.min(end, e + 1);
        return new String(d, s, e - s, StandardCharsets.UTF_8);
    }

    private static FayteIconLibrary.Parsed parse(byte[] d) {
        if (d.length < SIG.length + 2 || indexof(d, SIG, 0, SIG.length) != 0) {
            return null;
        }
        FayteIconLibrary.Parsed p = new FayteIconLibrary.Parsed();
        int i = SIG.length + 2;

        while (i < d.length) {
            int ne = i;
            while (ne < d.length && d[ne] != 0) {
                ne++;
            }
            if (ne + 5 > d.length) {
                break;
            }
            String name = new String(d, i, ne - i, StandardCharsets.US_ASCII);
            int len =
                    (d[ne + 1] & 0xff) | (d[ne + 2] & 0xff) << 8 | (d[ne + 3] & 0xff) << 16 | (d[ne + 4] & 0xff) << 24;
            int ds = ne + 5;
            int de = ds + len;
            if (len < 0 || de > d.length) {
                break;
            }
            if (name.equals("image") && p.png == null) {
                int ps = indexof(d, PNG, ds, de);
                if (ps >= 0) {
                    p.png = Arrays.copyOfRange(d, ps, de);
                }
            } else if (name.equals("action") && p.action == null) {
                int[] pos = {ds};
                cstr(d, pos, de);
                pos[0] += 2;
                String an = cstr(d, pos, de);
                if (!an.isEmpty()) {
                    p.action = an;
                }
            } else if (name.equals("tooltip") && p.tooltip == null) {
                p.tooltip = new String(d, ds, len, StandardCharsets.UTF_8).trim();
            }
            i = de;
        }
        return p;
    }

    private static byte[] read(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[16384];

        int n;
        while ((n = in.read(b)) > 0) {
            buf.write(b, 0, n);
        }
        return buf.toByteArray();
    }

    private static boolean wanted(String res) {
        return res.startsWith("gfx/invobjs/") || res.startsWith("paginae/");
    }

    private static String norm(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static void write(File f, byte[] data) throws Exception {
        f.getParentFile().mkdirs();
        try (FileOutputStream os = new FileOutputStream(f)) {
            os.write(data);
        }
    }

    private static void handle(String res, byte[] data, Map<String, String> wiki, FayteIconLibrary.Stats st)
            throws Exception {
        FayteIconLibrary.Parsed p = parse(data);
        if (p == null || p.png == null) {
            return;
        }
        st.resources++;
        File rf = new File(resdir(), res + ".png");
        if (!rf.exists()) {
            write(rf, p.png);
            st.byres++;
        }
        String name = p.action != null ? p.action : p.tooltip;
        if (name == null && res.startsWith("gfx/invobjs/")) {
            String base = res.substring(res.lastIndexOf('/') + 1).replaceAll("\\d+$", "");
            name = wiki.get(norm(base));
        }
        if (name != null && !name.isEmpty()) {
            st.index.append(res).append('\t').append(name).append('\n');
            File nf = new File(FaytePaths.icons(), FaytePaths.safename(name) + ".png");
            if (!nf.exists()) {
                write(nf, p.png);
                st.named++;
            }
        }
    }

    public static File cacheroot() {
        String ad = System.getenv("APPDATA");
        return ad == null ? null : new File(new File(ad, "Salem"), "cache");
    }

    private static void walk(File dir, String prefix, Map<String, String> wiki, FayteIconLibrary.Stats st) {
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isDirectory()) {
                    walk(f, prefix + f.getName() + "/", wiki, st);
                } else if (f.getName().endsWith(".cached")) {
                    String res = prefix + f.getName().substring(0, f.getName().length() - 7);
                    if (wanted(res)) {
                        try {
                            handle(res, Files.readAllBytes(f.toPath()), wiki, st);
                        } catch (Exception e) {
                            FayteLog.once("FayteIconLibrary.walk", e);
                        }
                    }
                }
            }
        }
    }

    public static FayteIconLibrary.Stats build(Consumer<String> progress) {
        FayteIconLibrary.Stats st = new FayteIconLibrary.Stats();
        Map<String, String> wiki = new HashMap<>();
        FayteWikiData.Store s = FayteWikiData.get();
        if (s != null) {
            for (String t : s.redirects.keySet()) {
                wiki.put(norm(t), s.redirects.get(t));
            }
            for (String t : s.pages.keySet()) {
                wiki.put(norm(t), t);
            }
        }
        File bin = new File(FaytePaths.home(), "bin");
        for (String jn : new String[] {"salem-res.jar", "builtin-res.jar", "lclient-res.jar"}) {
            File jf = new File(bin, jn);
            if (jf.exists()) {
                progress.accept("Reading " + jn + "\u2026");
                try (JarFile jar = new JarFile(jf)) {
                    Enumeration<JarEntry> en = jar.entries();

                    while (en.hasMoreElements()) {
                        JarEntry je = en.nextElement();
                        String n = je.getName();
                        if (n.startsWith("res/") && n.endsWith(".res")) {
                            String res = n.substring(4, n.length() - 4);
                            if (wanted(res)) {
                                try (InputStream in = jar.getInputStream(je)) {
                                    handle(res, read(in), wiki, st);
                                } catch (Exception e) {
                                    FayteLog.once("FayteIconLibrary.build", e);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    progress.accept("Could not read " + jf + ": " + e.getMessage());
                }
            }
        }
        File cr = cacheroot();
        File[] caches = cr == null ? null : cr.listFiles();
        if (caches != null) {
            for (File c : caches) {
                File rd = new File(c, "res");
                if (rd.isDirectory()) {
                    progress.accept("Reading the game's download cache\u2026");
                    walk(rd, "", wiki, st);
                }
            }
        }
        try {
            write(new File(FaytePaths.icons(), INDEX), st.index.toString().getBytes(StandardCharsets.UTF_8));
            write(
                    new File(FaytePaths.icons(), MARKER),
                    (st.resources + " icons read\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.once("FayteIconLibrary.build", e);
        }
        progress.accept("Icon library: " + st.resources + " icons found, " + st.named + " new named icons, " + st.byres
                + " new by resource.");
        return st;
    }

    public static void main(String[] args) {
        FayteWikiData.use(FayteWikiData.loadbest());
        build(System.out::println);
    }
}
