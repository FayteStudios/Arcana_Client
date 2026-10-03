package haven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FayteWikiQuery {
    private static final Pattern VALUES =
            Pattern.compile("\\[\\[\\s*(Gives|Requires)\\s+([^:\\]]+?)\\s*::\\s*!0\\s*\\]\\]");
    private static final Pattern PLACE = Pattern.compile(
            "\\[\\[\\s*(?:In c|C)ategory:\\s*(Creatures|Forageables|Foraged)\\s*\\]\\]\\s*\\[\\[\\s*Locations?\\s*::\\s*(?:~\\*)?(?:\\{\\{#titleparts:)?([^|\\]{}]+)");
    private static final Pattern GROUP = Pattern.compile("\\[\\[\\s*FoodGroup\\s*::\\s*([^|\\]{}]+)");
    private static volatile Map<String, FayteWikiData.Raw> pages = null;
    private static Map<String, FayteWikiData.Raw> builtfor = null;
    private static final Map<String, Map<String, Integer>> gives = new HashMap<>();
    private static final Map<String, Map<String, Integer>> requires = new HashMap<>();
    private static final Map<String, TreeSet<String>> creatures = new HashMap<>();
    private static final Map<String, TreeSet<String>> foraged = new HashMap<>();
    private static final Map<String, TreeSet<String>> groups = new HashMap<>();

    static void use(Map<String, FayteWikiData.Raw> p) {
        pages = p;
    }

    public static String render(String query) {
        Matcher m = VALUES.matcher(query);
        if (m.find()) {
            return values(m.group(1), m.group(2).trim());
        }
        m = PLACE.matcher(query);
        while (m.find()) {
            String place = last(m.group(2));
            if (place.equalsIgnoreCase("any")) {
                continue;
            }
            built();
            return list((m.group(1).equals("Creatures") ? creatures : foraged).get(key(place)));
        }
        m = GROUP.matcher(query);
        if (m.find()) {
            built();
            return list(groups.get(key(m.group(1))));
        }
        return null;
    }

    private static String values(String kind, String prof) {
        built();
        Map<String, Integer> r = (kind.equals("Gives") ? gives : requires).get(key(prof));
        if (r == null || r.isEmpty()) {
            return null;
        }
        List<Map.Entry<String, Integer>> rows = new ArrayList<>(r.entrySet());
        rows.sort((a, b) -> b.getValue().intValue() != a.getValue().intValue()
                ? Integer.compare(b.getValue(), a.getValue())
                : a.getKey().compareToIgnoreCase(b.getKey()));
        List<String> cells = new ArrayList<>();
        cells.add("2");
        for (Map.Entry<String, Integer> e : rows) {
            cells.add(FayteMarkup.enc("[[" + e.getKey() + "]]"));
            cells.add(FayteMarkup.enc(String.format("%,d", e.getValue())));
        }
        return "\n\n" + FayteMarkup.block(FayteMarkup.GRID, cells.toArray(new String[0])) + "\n\n";
    }

    private static String list(TreeSet<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        List<String> cells = new ArrayList<>();
        cells.add("3");
        for (String n : names) {
            cells.add(FayteMarkup.enc("[[" + n + "]]"));
        }
        return "\n\n" + FayteMarkup.block(FayteMarkup.GRID, cells.toArray(new String[0])) + "\n\n";
    }

    private static String last(String s) {
        s = s.trim();
        int slash = s.lastIndexOf('/');
        return slash >= 0 ? s.substring(slash + 1).trim() : s;
    }

    private static String key(String s) {
        return s.replaceAll("[\\u200e\\u200f\\[\\]]", "").trim().toLowerCase();
    }

    private static synchronized void built() {
        Map<String, FayteWikiData.Raw> p = pages;
        if (p == null || builtfor == p) {
            return;
        }
        builtfor = p;
        gives.clear();
        requires.clear();
        creatures.clear();
        foraged.clear();
        groups.clear();
        for (Map.Entry<String, FayteWikiData.Raw> e : p.entrySet()) {
            String text = e.getValue().text;
            if (text == null) {
                continue;
            }
            String page = e.getKey();
            numbers(fields(text, "{{Inspirational"), page, gives);
            numbers(fields(text, "{{Skill"), page, requires);
            names(fields(text, "{{Creature").get("where found"), page, creatures);
            names(fields(text, "{{Foraged").get("where found"), page, foraged);
            String fg = null;
            int i = text.indexOf("Food Groups");
            if (i >= 0) {
                int eq = text.indexOf('=', i);
                if (eq >= 0 && text.substring(i + 11, eq).trim().isEmpty()) {
                    int end = eq + 1;
                    while (end < text.length() && "|}\n".indexOf(text.charAt(end)) < 0) {
                        end++;
                    }
                    fg = text.substring(eq + 1, end);
                }
            }
            names(fg, page, groups);
        }
    }

    private static void numbers(Map<String, String> f, String page, Map<String, Map<String, Integer>> into) {
        for (Map.Entry<String, String> e : f.entrySet()) {
            String v = e.getValue().replace(",", "");
            if (v.matches("\\d+") && !v.equals("0")) {
                into.computeIfAbsent(e.getKey(), x -> new HashMap<>()).put(page, Integer.parseInt(v));
            }
        }
    }

    private static void names(String v, String page, Map<String, TreeSet<String>> into) {
        if (v == null) {
            return;
        }
        for (String part : v.split(",")) {
            String k = key(part);
            if (!k.isEmpty()) {
                into.computeIfAbsent(k, x -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)).add(page);
            }
        }
    }

    private static Map<String, String> fields(String text, String open) {
        Map<String, String> out = new HashMap<>();
        int i = text.indexOf(open);
        if (i < 0) {
            return out;
        }
        int after = i + open.length();
        if (after < text.length() && Character.isLetterOrDigit(text.charAt(after))) {
            return out;
        }
        int end = text.indexOf("}}", after);
        if (end < 0) {
            return out;
        }
        for (String part : text.substring(after, end).split("\\|")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                out.put(part.substring(0, eq).trim().toLowerCase(), part.substring(eq + 1).trim());
            }
        }
        return out;
    }
}
