package haven;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FayteTable {
    private static final Pattern BG =
            Pattern.compile("(?i)(?:background(?:-color)?\\s*:\\s*|bgcolor\\s*=\\s*\"?)(#[0-9a-f]{3,6}|[a-z]+)");
    private static final Pattern SPAN = Pattern.compile("(?i)colspan\\s*=\\s*\"?(\\d+)");

    public static class Cell {
        public boolean head;
        public Color bg;
        public int span = 1;
        public String raw = "";
    }

    public static class Table {
        public String caption;
        public final List<List<FayteTable.Cell>> rows = new ArrayList<>();

        public int columns() {
            int n = 0;

            for (List<FayteTable.Cell> r : rows) {
                int c = 0;

                for (FayteTable.Cell cell : r) {
                    c += cell.span;
                }
                n = Math.max(n, c);
            }
            return n;
        }

        public boolean data() {
            for (List<FayteTable.Cell> r : rows) {
                int filled = 0;

                for (FayteTable.Cell c : r) {
                    if (!FayteMarkup.plain(c.raw).trim().isEmpty()
                            || c.raw.contains("[[")
                            || c.raw.indexOf(FayteMarkup.IS) >= 0) {
                        filled++;
                    }
                }
                if (filled >= 2) {
                    return true;
                }
            }
            return false;
        }
    }

    static Color color(String s) {
        s = s.trim().toLowerCase();
        try {
            if (s.startsWith("#")) {
                String h = s.substring(1);
                if (h.length() == 3) {
                    h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
                }
                return new Color(Integer.parseInt(h, 16));
            }
        } catch (NumberFormatException e) {
        }
        switch (s) {
            case "red":
                return new Color(0xE6, 0xB8, 0xB8);
            case "blue":
                return new Color(0xB8, 0xC8, 0xE6);
            case "yellow":
                return new Color(0xFF, 0xFF, 0xB8);
            case "green":
                return new Color(0xB8, 0xE6, 0xB8);
            case "gray":
            case "grey":
                return new Color(0xCC, 0xCC, 0xCC);
            default:
                return null;
        }
    }

    private static int attrsplit(String s) {
        int sq = 0;
        int cu = 0;

        for (int i = 0; i < s.length(); i++) {
            if (s.startsWith("[[", i)) {
                sq++;
                i++;
            } else if (s.startsWith("]]", i)) {
                sq--;
                i++;
            } else if (s.startsWith("{{", i)) {
                cu++;
                i++;
            } else if (s.startsWith("}}", i)) {
                cu--;
                i++;
            } else if (s.charAt(i) == '|' && sq == 0 && cu == 0) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> splitcells(String s, String sep) {
        List<String> ret = new ArrayList<>();
        int sq = 0;
        int cu = 0;
        int start = 0;

        for (int i = 0; i < s.length(); i++) {
            if (s.startsWith("[[", i)) {
                sq++;
                i++;
            } else if (s.startsWith("]]", i)) {
                sq--;
                i++;
            } else if (s.startsWith("{{", i)) {
                cu++;
                i++;
            } else if (s.startsWith("}}", i)) {
                cu--;
                i++;
            } else if (sq == 0 && cu == 0 && (s.startsWith(sep, i) || s.startsWith("||", i))) {
                ret.add(s.substring(start, i));
                start = i + 2;
                i++;
            }
        }
        ret.add(s.substring(start));
        return ret;
    }

    private static FayteTable.Cell cell(String text, boolean head) {
        FayteTable.Cell c = new FayteTable.Cell();
        c.head = head;
        int a = attrsplit(text);
        String attrs = "";
        if (a >= 0
                && text.substring(0, a).matches("(?is).*\\b(style|class|bgcolor|colspan|rowspan|align|width)\\s*=.*")) {
            attrs = text.substring(0, a);
            text = text.substring(a + 1);
        }
        Matcher bm = BG.matcher(attrs);
        if (bm.find()) {
            c.bg = color(bm.group(1));
        }
        Matcher sm = SPAN.matcher(attrs);
        if (sm.find()) {
            c.span = Math.max(1, Math.min(12, Integer.parseInt(sm.group(1))));
        }
        c.raw = text.trim();
        return c;
    }

    public static FayteTable.Table parse(String raw) {
        FayteTable.Table t = new FayteTable.Table();
        List<FayteTable.Cell> row = new ArrayList<>();
        FayteTable.Cell last = null;

        for (String line : raw.split("\n")) {
            String l = line.trim();
            if (l.startsWith("{|")) {
                continue;
            } else if (l.startsWith("|}")) {
                break;
            } else if (l.startsWith("|+")) {
                t.caption = l.substring(2).trim();
            } else if (l.startsWith("|-")) {
                if (!row.isEmpty()) {
                    t.rows.add(row);
                }
                row = new ArrayList<>();
                last = null;
            } else if (l.startsWith("!") || l.startsWith("|")) {
                boolean head = l.startsWith("!");

                for (String c : splitcells(l.substring(1), head ? "!!" : "||")) {
                    last = cell(c, head);
                    row.add(last);
                }
            } else if (last != null && !l.isEmpty()) {
                last.raw = last.raw + "\n" + l;
            }
        }
        if (!row.isEmpty()) {
            t.rows.add(row);
        }
        return t;
    }

    public static String extract(String text) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        Deque<Integer> starts = new ArrayDeque<>();

        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i).trim();
            if (l.startsWith("{|")) {
                starts.push(i);
            } else if (l.startsWith("|}") && !starts.isEmpty()) {
                int s = starts.pop();
                StringBuilder raw = new StringBuilder();

                for (int k = s; k <= i; k++) {
                    raw.append(lines.get(k)).append('\n');
                }
                FayteTable.Table t = parse(raw.toString());
                if (t.data()) {
                    for (int k = i; k > s; k--) {
                        lines.remove(k);
                    }
                    lines.set(s, FayteMarkup.block(FayteMarkup.TABLE, FayteMarkup.enc(raw.toString())));
                    i = s;
                }
            }
        }
        return String.join("\n", lines);
    }
}
