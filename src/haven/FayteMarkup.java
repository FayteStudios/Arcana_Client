package haven;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class FayteMarkup {
    public static final char LS = '\uE001';
    public static final char LM = '\uE002';
    public static final char LE = '\uE003';
    public static final char IS = '\uE004';
    public static final char IE = '\uE005';
    public static final char BS = '\uE006';
    public static final char BF = '\uE007';
    public static final char BE = '\uE008';
    public static final String PICTURE = "picture";
    public static final String NOTE = "note";
    public static final String GRID = "grid";
    public static final String TABLE = "table";

    public static String enc(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static String dec(String s) {
        try {
            return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    public static class Block {
        public final String kind;
        public final List<String> fields;

        Block(String kind, List<String> fields) {
            this.kind = kind;
            this.fields = fields;
        }

        public String field(int i) {
            return i < fields.size() ? fields.get(i) : "";
        }
    }

    public static boolean ismark(char c) {
        return c >= LS && c <= BE;
    }

    public static String link(String target, String label) {
        return "" + LS + target + LM + label + LE;
    }

    public static String icon(String name) {
        return "" + IS + name + IE;
    }

    public static String block(String kind, String... fields) {
        StringBuilder sb = new StringBuilder().append(BS).append(kind);

        for (String f : fields) {
            sb.append(BF).append(f == null ? "" : f);
        }
        return sb.append(BE).toString();
    }

    public static List<Object> split(String s) {
        List<Object> ret = new ArrayList<>();
        if (s == null) {
            return ret;
        }
        int i = 0;

        while (i < s.length()) {
            int b = s.indexOf(BS, i);
            if (b < 0) {
                ret.add(s.substring(i));
                break;
            }
            int e = s.indexOf(BE, b);
            if (e < 0) {
                ret.add(s.substring(i, b) + s.substring(b + 1));
                break;
            }
            if (b > i) {
                ret.add(s.substring(i, b));
            }
            List<String> parts =
                    new ArrayList<>(Arrays.asList(s.substring(b + 1, e).split(String.valueOf(BF), -1)));
            String kind = parts.remove(0);
            ret.add(new FayteMarkup.Block(kind, parts));
            i = e + 1;
        }
        return ret;
    }

    public static boolean blockonly(String s) {
        List<Object> parts = split(s);
        boolean block = false;

        for (Object o : parts) {
            if (o instanceof FayteMarkup.Block) {
                block = true;
            } else if (!plain((String) o).trim().isEmpty()) {
                return false;
            }
        }
        return block;
    }

    public static String plain(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        int i = 0;

        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == LS) {
                int m = s.indexOf(LM, i);
                int e = s.indexOf(LE, i);
                if (m > i && e > m) {
                    out.append(s, m + 1, e);
                    i = e + 1;
                    continue;
                }
            } else if (c == IS) {
                int e = s.indexOf(IE, i);
                if (e > i) {
                    i = e + 1;
                    continue;
                }
            } else if (c == BS) {
                int e = s.indexOf(BE, i);
                if (e > i) {
                    String[] parts = s.substring(i + 1, e).split(String.valueOf(BF), -1);
                    for (int k = parts[0].equals(GRID) || parts[0].equals(TABLE) ? parts.length : 1;
                            k < parts.length;
                            k++) {
                        if (!parts[k].isEmpty()) {
                            out.append(' ').append(plain(parts[k]));
                        }
                    }
                    i = e + 1;
                    continue;
                }
            } else if (c != LM && c != LE && c != IE && c != BF && c != BE) {
                out.append(c);
            }
            i++;
        }
        return out.toString();
    }
}
