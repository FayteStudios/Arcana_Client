package haven;

import java.util.List;
import java.util.Map;

public class FayteWikiTidy {
    public static String unmark(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int i = 0;

        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == FayteMarkup.LS) {
                int m = s.indexOf(FayteMarkup.LM, i);
                int e = s.indexOf(FayteMarkup.LE, i);
                if (m > i && e > m) {
                    String target = s.substring(i + 1, m);
                    String label = s.substring(m + 1, e);
                    out.append(label.equals(target) ? "[[" + target + "]]" : "[[" + target + "|" + label + "]]");
                    i = e + 1;
                    continue;
                }
            } else if (c == FayteMarkup.IS) {
                int e = s.indexOf(FayteMarkup.IE, i);
                if (e > i) {
                    out.append("{{icon|")
                            .append(s.substring(i + 1, e).replace(FayteMarkup.BF, '|'))
                            .append("}}");
                    i = e + 1;
                    continue;
                }
            } else if (c == FayteMarkup.BS) {
                int e = s.indexOf(FayteMarkup.BE, i);
                if (e > i) {
                    for (Object o : FayteMarkup.split(s.substring(i, e + 1))) {
                        if (o instanceof FayteMarkup.Block) {
                            out.append(block((FayteMarkup.Block) o));
                        }
                    }
                    i = e + 1;
                    continue;
                }
            }
            if (!FayteMarkup.ismark(c)) {
                out.append(c);
            }
            i++;
        }
        return out.toString();
    }

    private static String block(FayteMarkup.Block b) {
        if (b.kind.equals(FayteMarkup.PICTURE)) {
            String cap = unmark(b.field(1));
            String sz = b.field(2);
            return "[[File:" + b.field(0) + (sz.isEmpty() ? "" : "|" + sz + "px") + (cap.isEmpty() ? "" : "|" + cap)
                    + "]]";
        } else if (b.kind.equals(FayteMarkup.TABLE)) {
            return FayteMarkup.dec(b.field(0)).trim();
        } else if (b.kind.equals(FayteMarkup.GRID)) {
            StringBuilder sb =
                    new StringBuilder(b.field(0).equals("0") ? "{{Row\n" : "{{Grid\n| columns=" + b.field(0) + "\n");

            for (int i = 1; i < b.fields.size(); i++) {
                sb.append("| ").append(FayteMarkup.dec(b.field(i))).append('\n');
            }
            return sb.append("}}").toString();
        } else if (b.kind.equals(FayteMarkup.NOTE)) {
            String t = unmark(b.field(0));
            return "{{Notice|" + (t.isEmpty() ? "" : t + "|") + unmark(b.field(1)) + "}}";
        } else {
            return "";
        }
    }

    private static String para(String p) {
        String t = p.startsWith("\u2022 ") ? "* " + unmark(p.substring(2)) : unmark(p);
        List<Object> parts = FayteMarkup.split(p);
        boolean blockfirst = !parts.isEmpty() && parts.get(0) instanceof FayteMarkup.Block;
        return blockfirst ? t.trim() : t;
    }

    public static String basics(String weight) {
        return "{{Facts\n| Title=Basics\n| Type=\n| Weight=" + (weight == null ? "" : " " + weight) + "\n}}\n\n";
    }

    public static String weight(FayteWikiData.Entry e) {
        if (e == null) {
            return null;
        }
        for (Map<String, String> a : e.tpl.values()) {
            for (Map.Entry<String, String> kv : a.entrySet()) {
                if (kv.getKey().trim().equalsIgnoreCase("Weight")) {
                    String v = unmark(kv.getValue()).trim();
                    if (!v.isEmpty() && v.matches("[0-9.,]+")) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasbasics(FayteWikiData.Entry e) {
        for (Map.Entry<String, Map<String, String>> t : e.tpl.entrySet()) {
            if (t.getKey().split("#")[0].trim().equalsIgnoreCase("Facts")) {
                String ti = t.getValue().get("Title");
                if (ti != null && ti.trim().equalsIgnoreCase("Basics")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String tidy(FayteWikiData.Entry e) {
        StringBuilder sb = new StringBuilder();
        if (!hasbasics(e)) {
            sb.append(basics(weight(e)));
        }
        for (Map.Entry<String, Map<String, String>> t : e.tpl.entrySet()) {
            String name = t.getKey().split("#")[0];
            if (FayteWikiGlance.shown(name) || name.equalsIgnoreCase("Icons")) {
                sb.append("{{").append(name).append('\n');

                for (Map.Entry<String, String> a : t.getValue().entrySet()) {
                    sb.append("| ")
                            .append(a.getKey())
                            .append('=')
                            .append(unmark(a.getValue()))
                            .append('\n');
                }
                sb.append("}}\n");
            }
        }
        if (e.image != null) {
            sb.append("[[File:")
                    .append(e.image)
                    .append(e.imagesize != null && !e.imagesize.isEmpty() ? "|" + e.imagesize + "px" : "")
                    .append("]]\n");
        }
        for (FayteWikiData.Section s : e.sections) {
            if (s.paras.isEmpty() && (s.h == null || s.h.isEmpty())) {
                continue;
            }
            sb.append('\n');
            if (s.h != null && !s.h.isEmpty()) {
                sb.append("==").append(s.h).append("==\n");
            }
            boolean lastbullet = false;

            for (String p : s.paras) {
                boolean bullet = p.startsWith("\u2022 ");
                if (!bullet && lastbullet) {
                    sb.append('\n');
                }
                sb.append(para(p)).append('\n');
                if (!bullet) {
                    sb.append('\n');
                }
                lastbullet = bullet;
            }
        }
        if (!e.cats.isEmpty()) {
            sb.append('\n');

            for (String c : e.cats) {
                sb.append("[[Category:").append(c).append("]]\n");
            }
        }
        return sb.toString().replaceAll("\n{3,}", "\n\n").trim() + "\n";
    }
}
