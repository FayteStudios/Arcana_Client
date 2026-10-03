package haven;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.imageio.ImageIO;

public class FayteEntries {
    public static final String EXT = ".wiki";
    public static final String ALIASES = "aliases.txt";
    public static final String HIDDEN = "hidden_entries.txt";
    private static volatile Set<String> hidden = new HashSet<>();
    private static final long CHECK_MS = 5000L;
    private static volatile Map<String, FayteWikiData.Entry> custom = new HashMap<>();
    private static volatile Map<String, String> aliases = new HashMap<>();
    private static final Map<String, BufferedImage> bufs = new HashMap<>();
    private static String sig = null;
    private static long lastcheck = 0L;
    private static boolean loaded = false;

    public static File dir() {
        return FaytePaths.entries();
    }

    public static File aliasfile() {
        return new File(FaytePaths.fayte(), ALIASES);
    }

    public static String alias(String key) {
        return key == null ? null : aliases.get(FayteWikiData.norm(key));
    }

    public static File hiddenfile() {
        return new File(FaytePaths.fayte(), HIDDEN);
    }

    public static boolean hidden(String title) {
        return title != null && hidden.contains(FayteWikiData.norm(title));
    }

    public static synchronized void hide(String title, boolean hide) {
        Set<String> nh = new TreeSet<>(hidden);
        if (hide) {
            nh.add(FayteWikiData.norm(title));
        } else {
            nh.remove(FayteWikiData.norm(title));
        }
        File f = hiddenfile();
        f.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            for (String t : nh) {
                w.write(t);
                w.write("\n");
            }
        } catch (Exception e) {
            FayteLog.log("Entries: could not write " + f + ": " + e);
        }
        hidden = nh;
    }

    private static Set<String> readhidden() {
        Set<String> nh = new HashSet<>();
        File f = hiddenfile();
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                for (String line : read(in).split("\n")) {
                    String l = line.trim();
                    if (!l.isEmpty() && !l.startsWith("#")) {
                        nh.add(FayteWikiData.norm(l));
                    }
                }
            } catch (Exception e) {
                FayteLog.log("Entries: could not read " + f + ": " + e);
            }
        }
        return nh;
    }

    public static FayteWikiData.Entry custom(String title) {
        return title == null ? null : custom.get(FayteWikiData.norm(title));
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[4096];

        int n;
        while ((n = in.read(b)) > 0) {
            buf.write(b, 0, n);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void addaliases(Map<String, String> into, String text) {
        for (String line : text.split("\n")) {
            String l = line.trim();
            int eq = l.indexOf('=');
            if (!l.isEmpty() && !l.startsWith("#") && eq > 0) {
                String k = l.substring(0, eq).trim();
                String v = l.substring(eq + 1).trim();
                if (!k.isEmpty() && !v.isEmpty()) {
                    into.put(FayteWikiData.norm(k), v);
                }
            }
        }
    }

    private static void addentry(Map<String, FayteWikiData.Entry> into, String filename, String text) {
        String base = filename.substring(filename.lastIndexOf('/') + 1);
        if (base.startsWith("_") || !base.endsWith(EXT)) {
            return;
        }
        String title = base.substring(0, base.length() - EXT.length());

        try {
            FayteWikiData.Entry e = FayteWikiText.parse(title, text, null);
            e.custom = true;
            into.put(FayteWikiData.norm(title), e);
        } catch (RuntimeException ex) {
            FayteLog.log("Entries: could not read " + filename + ": " + ex);
        }
    }

    private static JarFile ownjar() {
        try {
            URL u = FayteEntries.class.getProtectionDomain().getCodeSource().getLocation();
            File f = new File(u.toURI());
            return f.isFile() ? new JarFile(f) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static synchronized void load() {
        Map<String, FayteWikiData.Entry> nc = new HashMap<>();
        Map<String, String> na = new HashMap<>();

        File sd = shareddir;
        if (sd != null) {
            File[] sfs = sd.listFiles();
            if (sfs != null) {
                for (File f : sfs) {
                    if (f.isFile()) {
                        try (InputStream in = new FileInputStream(f)) {
                            addentry(nc, f.getName(), read(in));
                        } catch (Exception ex) {
                            FayteLog.log("Entries: could not read " + f + ": " + ex);
                        }
                    }
                }
            }
            File sa = new File(sd.getParentFile(), ALIASES);
            if (sa.exists()) {
                try (InputStream in = new FileInputStream(sa)) {
                    addaliases(na, read(in));
                } catch (Exception ex) {
                    FayteLog.log("Entries: could not read " + sa + ": " + ex);
                }
            }
        }
        try (JarFile jar = sd != null ? null : ownjar()) {
            if (jar != null) {
                Enumeration<JarEntry> en = jar.entries();

                while (en.hasMoreElements()) {
                    JarEntry je = en.nextElement();
                    String n = je.getName();
                    if (n.startsWith("fayte/entries/") && n.endsWith(EXT)) {
                        try (InputStream in = jar.getInputStream(je)) {
                            addentry(nc, n, read(in));
                        }
                    } else if (n.equals("fayte/" + ALIASES)) {
                        try (InputStream in = jar.getInputStream(je)) {
                            addaliases(na, read(in));
                        }
                    }
                }
            }
        } catch (Exception e) {
            FayteLog.log("Entries: could not read bundled entries: " + e);
        }
        int bundled = nc.size();
        File d = dir();
        File[] fs = d.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isFile()) {
                    try (InputStream in = new FileInputStream(f)) {
                        addentry(nc, f.getName(), read(in));
                    } catch (Exception e) {
                        FayteLog.log("Entries: could not read " + f + ": " + e);
                    }
                }
            }
        }
        File af = aliasfile();
        if (af.exists()) {
            try (InputStream in = new FileInputStream(af)) {
                addaliases(na, read(in));
            } catch (Exception e) {
                FayteLog.log("Entries: could not read " + af + ": " + e);
            }
        }
        custom = nc;
        aliases = na;
        hidden = readhidden();
        synchronized (bufs) {
            bufs.clear();
        }
        FayteLog.log(
                "Entries: " + bundled + " bundled, " + (nc.size() - bundled) + " of yours, " + na.size() + " aliases");
    }

    private static String signature() {
        StringBuilder sb = new StringBuilder();
        File[] fs = dir().listFiles();
        if (fs != null) {
            for (File f : fs) {
                sb.append(f.getName())
                        .append(':')
                        .append(f.lastModified())
                        .append(':')
                        .append(f.length())
                        .append(';');
            }
            File id = new File(dir(), "images");
            File[] is = id.listFiles();
            if (is != null) {
                for (File f : is) {
                    sb.append(f.getName()).append(':').append(f.lastModified()).append(';');
                }
            }
        }
        File af = aliasfile();
        sb.append(af.exists() ? af.lastModified() : 0L);
        File hf = hiddenfile();
        sb.append(';').append(hf.exists() ? hf.lastModified() : 0L);
        return sb.toString();
    }

    private static void starter() {
        File d = dir();
        File af = aliasfile();
        if (!af.exists()) {
            d.mkdirs();
            new File(d, "images").mkdirs();

            try (Writer w = new OutputStreamWriter(new FileOutputStream(af), StandardCharsets.UTF_8)) {
                w.write("# Fayte aliases: point a game name or resource name at an entry.\n");
                w.write("# One per line:  name or resource = Entry Title\n");
                w.write("# Resource names are listed in missing_entries.txt next to this file.\n");
                w.write("# Example:\n");
                w.write("# gfx/terobjs/cheapscarecrow = Questionably Effective Scarecrow\n");
            } catch (Exception e) {
                FayteLog.log("Entries: could not create " + af + ": " + e);
            }
        }
    }

    public static void forget(String name) {
        synchronized (bufs) {
            bufs.remove("icon:" + name.trim().toLowerCase());
        }
    }

    public static void check() {
        ingame = true;
        long now = System.currentTimeMillis();
        if (!loaded) {
            loaded = true;
            starter();
            sig = signature();
            load();
            lastcheck = now;
        } else if (now - lastcheck > CHECK_MS) {
            lastcheck = now;
            String s = signature();
            if (!s.equals(sig)) {
                sig = s;
                load();
            }
        }
    }

    public static volatile boolean ingame = false;
    public static volatile File shareddir = null;

    private static BufferedImage readimg(File f) {
        try {
            return f.exists() ? ImageIO.read(f) : null;
        } catch (Exception ex) {
            FayteLog.log("Entries: could not read image " + f + ": " + ex);
            return null;
        }
    }

    private static BufferedImage bundled(String path) {
        try (InputStream in = FayteEntries.class.getResourceAsStream(path)) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception ex) {
            return null;
        }
    }

    public static BufferedImage imagebuf(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String key = "img:" + name.trim().replace(' ', '_');
        synchronized (bufs) {
            if (bufs.containsKey(key)) {
                return bufs.get(key);
            }
            String n = name.trim();
            BufferedImage img = null;

            for (String cand : new String[] {n, n.replace(' ', '_'), n.replace('_', ' ')}) {
                if (img == null) {
                    img = readimg(new File(FaytePaths.images(), cand));
                }
                if (img == null && shareddir != null) {
                    img = readimg(new File(new File(shareddir, "images"), cand));
                }
                if (img == null) {
                    img = bundled("/fayte/entries/images/" + cand);
                }
                if (img == null) {
                    img = readimg(new File(FaytePaths.wikiimages(), cand));
                }
                if (img == null && !cand.isEmpty()) {
                    img = readimg(new File(
                            FaytePaths.wikiimages(), Character.toUpperCase(cand.charAt(0)) + cand.substring(1)));
                }
            }
            bufs.put(key, img);
            return img;
        }
    }

    private static int picking = 0;

    public static String picked(String name) {
        FayteWikiData.Entry e = custom(name);
        if (e == null) {
            return null;
        }
        for (Map.Entry<String, Map<String, String>> t : e.tpl.entrySet()) {
            if (t.getKey().split("#")[0].trim().equalsIgnoreCase("Icons")) {
                for (Map.Entry<String, String> a : t.getValue().entrySet()) {
                    if (a.getKey().matches("\\d+")) {
                        String n = FayteMarkup.plain(a.getValue()).trim();
                        if (!n.isEmpty()) {
                            return n;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static BufferedImage iconbuf(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String n = name.trim();
        String key = "icon:" + n.toLowerCase();
        synchronized (bufs) {
            if (bufs.containsKey(key)) {
                return bufs.get(key);
            }
            BufferedImage img = null;
            String pick = picking < 4 ? picked(n) : null;
            if (pick != null && !pick.equalsIgnoreCase(n)) {
                picking++;
                try {
                    img = iconbuf(pick);
                } finally {
                    picking--;
                }
            }
            if (img == null && (n.startsWith("gfx/") || n.startsWith("paginae/"))) {
                img = readimg(new File(new File(FaytePaths.icons(), "res"), n + ".png"));
                if (img == null && ingame) {
                    try {
                        img = Resource.load(n).layer(Resource.imgc).img;
                    } catch (Loading l) {
                        return null;
                    } catch (RuntimeException ex) {
                        img = null;
                    }
                }
            } else if (img == null) {
                String file = FaytePaths.safename(n) + ".png";
                img = readimg(new File(FaytePaths.icons(), file));
                if (img == null && shareddir != null) {
                    img = readimg(new File(new File(shareddir.getParentFile(), "icons"), file));
                }
                if (img == null) {
                    img = bundled("/fayte/icons/" + file);
                }
            }
            bufs.put(key, img);
            return img;
        }
    }
}
