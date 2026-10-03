package haven;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FayteGuide {
    private static final Pattern LINK = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");
    private static Map<String, Guide> guides = null;

    public static class Guide {
        public String key;
        public List<String> words = new ArrayList<>();
        public String title;
        public List<String> lines = new ArrayList<>();
    }

    private static void parse(String text, Map<String, Guide> into) {
        Guide cur = null;
        for (String raw : text.split("\n")) {
            String l = raw.replace("\r", "");
            if (l.startsWith("#") && !l.startsWith("##")) {
                continue;
            }
            if (l.startsWith("@")) {
                String h = l.substring(1).trim();
                int bar = h.indexOf('|');
                cur = new Guide();
                cur.title = bar >= 0 ? h.substring(bar + 1).trim() : h;
                for (String w : (bar >= 0 ? h.substring(0, bar) : h).split(",")) {
                    if (!w.trim().isEmpty()) {
                        cur.words.add(w.trim().toLowerCase());
                    }
                }
                cur.key = cur.words.isEmpty() ? cur.title.toLowerCase() : cur.words.get(0);
                into.put(cur.key, cur);
            } else if (cur != null && !l.trim().isEmpty()) {
                cur.lines.add(l.trim());
            }
        }
    }

    private static synchronized Map<String, Guide> all() {
        if (guides == null) {
            Map<String, Guide> m = new LinkedHashMap<>();
            try (InputStream in = FayteGuide.class.getResourceAsStream("/fayte/guides.txt")) {
                if (in != null) {
                    StringBuilder sb = new StringBuilder();
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String l;
                    while ((l = r.readLine()) != null) {
                        sb.append(l).append('\n');
                    }
                    parse(sb.toString(), m);
                }
            } catch (Exception e) {
                FayteLog.log("Guides: could not read bundled guides: " + e);
            }
            File uf = new File(FaytePaths.fayte(), "guides.txt");
            if (uf.exists()) {
                try {
                    parse(new String(Files.readAllBytes(uf.toPath()), StandardCharsets.UTF_8), m);
                } catch (Exception e) {
                    FayteLog.log("Guides: could not read " + uf + ": " + e);
                }
            }
            guides = m;
        }
        return guides;
    }

    public static Guide find(String rn) {
        if (rn == null) {
            return null;
        }
        String low = rn.toLowerCase();
        for (Guide g : all().values()) {
            for (String w : g.words) {
                if (low.contains(w)) {
                    return g;
                }
            }
        }
        return null;
    }

    private static String links(String s) {
        Matcher m = LINK.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(
                    sb,
                    Matcher.quoteReplacement(
                            FayteMarkup.link(m.group(1).trim(), m.group(1).trim())));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static FayteWikiEntry entry(Guide g) {
        FayteWikiEntry e = new FayteWikiEntry();
        FayteWikiEntry.Section cur = null;
        for (String l : g.lines) {
            if (l.startsWith("##")) {
                cur = new FayteWikiEntry.Section();
                cur.title = l.substring(2).trim();
                e.sections.add(cur);
            } else if (l.startsWith("-")) {
                if (cur == null) {
                    cur = new FayteWikiEntry.Section();
                    e.sections.add(cur);
                }
                cur.rows.add(new String[] {"\u2022", links(l.substring(1).trim())});
            } else if (e.summary == null && cur == null) {
                e.summary = links(l);
            } else {
                if (cur == null) {
                    cur = new FayteWikiEntry.Section();
                    e.sections.add(cur);
                }
                cur.rows.add(new String[] {"", links(l)});
            }
        }
        return e;
    }
}
