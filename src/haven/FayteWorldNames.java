package haven;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FayteWorldNames {
    private static final Pattern MARKER = Pattern.compile("<marker\\s+match=\"([^\"]+)\"[^>]*?text=\"([^\"]+)\"");
    private static final Pattern DIGITS = Pattern.compile("\\d+$");
    private static final Map<String, String> ALIAS = new HashMap<>();
    private static Map<String, String> radar = null;
    private static Map<String, String> index = null;
    private static long indexmtime = -1L;

    private static synchronized Map<String, String> index() {
        File f = new File(FaytePaths.icons(), FayteIconLibrary.INDEX);
        long m = f.exists() ? f.lastModified() : 0L;
        if (index == null || m != indexmtime) {
            index = FayteIconLibrary.index();
            indexmtime = m;
        }
        return index;
    }

    static {
        ALIAS.put("rattler", "Timber Rattler");
    }

    private static synchronized Map<String, String> radar() {
        if (radar == null) {
            radar = new HashMap<>();

            try (InputStream in = FayteWorldNames.class.getResourceAsStream("/radar.xml")) {
                if (in != null) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] b = new byte[4096];

                    int n;
                    while ((n = in.read(b)) > 0) {
                        buf.write(b, 0, n);
                    }
                    Matcher m = MARKER.matcher(new String(buf.toByteArray(), StandardCharsets.UTF_8));

                    while (m.find()) {
                        radar.put(m.group(1).toLowerCase(), m.group(2));
                    }
                }
            } catch (Exception e) {
                FayteLog.log("World names: could not read radar.xml: " + e);
            }
        }
        return radar;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static List<String> candidates(String resname) {
        List<String> c = new ArrayList<>();
        if (resname == null || resname.isEmpty()) {
            return c;
        }
        c.add(resname);
        String r = radar().get(resname.toLowerCase());
        if (r != null) {
            c.add(r);
        }
        String[] parts = resname.split("/");
        if (resname.startsWith("gfx/kritter/") && parts.length > 2) {
            String sp = parts[2];
            if (ALIAS.containsKey(sp)) {
                c.add(ALIAS.get(sp));
            }
            c.add(cap(sp));
            return c;
        }
        String last = DIGITS.matcher(parts[parts.length - 1]).replaceAll("");
        String kind = parts.length > 2 ? parts[parts.length - 2] : "";
        String built = index().get("paginae/bld/" + parts[parts.length - 1]);
        if (built == null) {
            built = index().get("paginae/bld/" + last);
        }
        if (built != null) {
            c.add(built);
        }
        if (kind.equals("trees")) {
            c.add(cap(last) + " Tree");
        } else if (kind.equals("bushes")) {
            c.add(cap(last) + " Bush");
        } else if (kind.equals("logs")) {
            c.add(cap(last) + " Log");
        } else if (kind.equals("stumps")) {
            c.add(cap(last) + " Stump");
        }
        if (last.endsWith("bush") && last.length() > 4) {
            c.add(cap(last.substring(0, last.length() - 4)) + " Bush");
        }
        if (last.endsWith("tree") && last.length() > 4) {
            c.add(cap(last.substring(0, last.length() - 4)) + " Tree");
        }
        c.add(cap(last));
        return c;
    }

    public static String display(String resname) {
        for (String n : candidates(resname)) {
            FayteWikiData.Entry e = FayteWikiData.find(n);
            if (e != null) {
                return e.title;
            }
        }
        List<String> c = candidates(resname);
        return c.size() < 2 ? resname : c.get(1);
    }

    public static FayteWikiData.Entry lookup(String resname) {
        for (String n : candidates(resname)) {
            FayteWikiData.Entry e = FayteWikiData.find(n);
            if (e != null) {
                return e;
            }
        }
        return null;
    }
}
