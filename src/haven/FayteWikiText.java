package haven;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FayteWikiText {
    private static final Pattern HEADING = Pattern.compile("^(={2,6})\\s*(.*?)\\s*\\1\\s*$");
    private static final Pattern CELL = Pattern.compile("^\\|\\s*[a-zA-Z-]+\\s*=[^|\\[{]*\\|(.*)$");
    private static final Pattern CATEGORY =
            Pattern.compile("\\[\\[\\s*Category\\s*:\\s*([^\\]|]+)(\\|[^\\]]*)?\\]\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILELINK = Pattern.compile(
            "\\[\\[\\s*(File|Image|Media)\\s*:[^\\[\\]]*(\\[\\[[^\\]]*\\]\\][^\\[\\]]*)*\\]\\]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK = Pattern.compile("\\[\\[([^\\]|]*)(\\|([^\\]]*))?\\]\\]");
    private static final Pattern EXTLINK = Pattern.compile("\\[(https?://\\S+)(\\s+([^\\]]*))?\\]");
    private static final Pattern TAGS =
            Pattern.compile("<ref[^>]*/>|<ref[^>]*>.*?</ref>|<!--.*?-->|<[^>]+>", Pattern.DOTALL);
    private static final Pattern QUOTES = Pattern.compile("'{2,5}");
    private static final Pattern SPACES = Pattern.compile("[ \\t]+");
    private static final Pattern PICOPT = Pattern.compile(
            "(?i)^(thumb|thumbnail|frame|frameless|border|left|right|center|centre|none|baseline|middle|top|bottom|upright.*|\\d*x?\\d+px|link=.*|alt=.*|class=.*)$");
    private static final Pattern SIZE = Pattern.compile("(?i)^(\\d+)?(?:x\\d+)?px$");
    private static final Pattern GRIDOPT = Pattern.compile("(?i)^(?:columns|cols)\\s*=\\s*(\\d+)$");
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern REDIRECT =
            Pattern.compile("^\\s*#REDIRECT\\s*\\[\\[([^\\]|#]+)", Pattern.CASE_INSENSITIVE);

    public static String fragment(String raw, String title) {
        String s = params(raw.replace("\r\n", "\n").replace('\r', '\n'));
        if (title != null) {
            s = s.replace("{{PAGENAME}}", title);
        }
        s = inline(s, title);
        StringBuilder out = new StringBuilder();

        for (String line : s.split("\n")) {
            String c = clean(line.trim(), title);
            if (!c.isEmpty()) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(c);
            }
        }
        return out.toString();
    }

    public static String redirect(String text) {
        Matcher m = REDIRECT.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    public static FayteWikiData.Entry parse(String title, String text, String ts) {
        FayteWikiData.Entry e = new FayteWikiData.Entry();
        e.title = title;
        e.ts = ts;
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        text = COMMENT.matcher(text).replaceAll("");

        text = text.replace("[[:+]] [[Category:", "[[:+]] [[In category:");
        Matcher cm = CATEGORY.matcher(text);

        while (cm.find()) {
            e.cats.add(cm.group(1).trim());
        }
        text = CATEGORY.matcher(text).replaceAll("");
        text = params(text);
        StringBuilder body = new StringBuilder();
        int i = 0;

        while (i < text.length()) {
            if (text.startsWith("{{", i) && atLineStart(text, i)) {
                int end = close(text, i);
                if (end > 0) {
                    String inner = text.substring(i + 2, end - 2);
                    int nl = end;
                    while (nl < text.length() && (text.charAt(nl) == ' ' || text.charAt(nl) == '\t')) {
                        nl++;
                    }
                    if (nl >= text.length() || text.charAt(nl) == '\n') {
                        String tn = inner.split("\\|", 2)[0].trim().toLowerCase();
                        if (tn.equals("notice")
                                || tn.equals("note")
                                || tn.equals("tip")
                                || tn.equals("row")
                                || tn.equals("grid")
                                || tn.startsWith("#ask:")) {
                            body.append("\n\n").append(text, i, end).append("\n\n");
                        } else {
                            addtemplate(e, inner, title);
                        }
                        i = nl;
                        continue;
                    }
                }
            }
            body.append(text.charAt(i));
            i++;
        }
        FayteWikiData.Section cur = new FayteWikiData.Section();
        cur.h = "";
        StringBuilder para = new StringBuilder();
        String btext =
                FayteTable.extract(inline(unwraplayout(body.toString()).replace("{{PAGENAME}}", title), title));

        for (String line : btext.split("\n")) {
            Matcher hm = HEADING.matcher(line.trim());
            if (hm.matches()) {
                flush(cur, para);
                if (!cur.paras.isEmpty() || !cur.h.isEmpty()) {
                    e.sections.add(cur);
                }
                cur = new FayteWikiData.Section();
                cur.h = FayteMarkup.plain(clean(hm.group(2), title));
                continue;
            }
            String t = line.trim();
            if (t.startsWith("{|") || t.startsWith("|}") || t.startsWith("|-") || t.equals("|") || t.equals("!")) {
                flush(cur, para);
                continue;
            }
            if (t.startsWith("|") || t.startsWith("!")) {
                t = t.substring(1).trim();
                t = t.replace("||", " \u00b7 ").replace("!!", " \u00b7 ");
            }
            if (t.matches(
                    "(?i)^(class|style|border|cellpadding|cellspacing|width|align|valign|bgcolor|colspan|rowspan)\\s*=.*")) {
                continue;
            }
            String tl = t.toLowerCase();
            if (tl.startsWith("[[file:") || tl.startsWith("[[image:") || t.indexOf(FayteMarkup.BS) == 0) {
                flush(cur, para);
                String pic = clean(t, title);
                if (!pic.isEmpty()) {
                    cur.paras.add(pic);
                }
                continue;
            }
            if (t.startsWith("*") || t.startsWith("#") || t.startsWith(":") || t.startsWith(";")) {
                flush(cur, para);
                String item = clean(t.replaceFirst("^[*#:;]+", ""), title);
                if (!item.isEmpty()) {
                    cur.paras.add("\u2022 " + item);
                }
                continue;
            }
            if (t.isEmpty()) {
                flush(cur, para);
            } else {
                if (para.length() > 0) {
                    para.append(' ');
                }
                para.append(t);
            }
        }
        flush(cur, para);
        if (!cur.paras.isEmpty() || !cur.h.isEmpty()) {
            e.sections.add(cur);
        }
        if (!e.sections.isEmpty()
                && e.sections.get(0).h.isEmpty()
                && !e.sections.get(0).paras.isEmpty()) {
            List<Object> first = FayteMarkup.split(e.sections.get(0).paras.get(0));
            if (first.size() == 1
                    && first.get(0) instanceof FayteMarkup.Block
                    && ((FayteMarkup.Block) first.get(0)).kind.equals(FayteMarkup.PICTURE)) {
                e.image = ((FayteMarkup.Block) first.get(0)).field(0);
                e.imagesize = ((FayteMarkup.Block) first.get(0)).field(2);
                e.sections.get(0).paras.remove(0);
            }
        }
        outer:
        for (FayteWikiData.Section s : e.sections) {
            for (String p : s.paras) {
                if (!FayteMarkup.blockonly(p)) {
                    e.summary = p;
                    break outer;
                }
            }
        }
        return e;
    }

    private static String params(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;

        while (i < text.length()) {
            if (text.startsWith("{{{!}}", i)) {
                out.append('{');
                i++;
            } else if (text.startsWith("{{{", i)) {
                int depth = 1;
                int j = i + 3;

                while (j < text.length() && depth > 0) {
                    if (text.startsWith("{{{", j)) {
                        depth++;
                        j += 3;
                    } else if (text.startsWith("}}}", j)) {
                        depth--;
                        j += 3;
                    } else {
                        j++;
                    }
                }
                String inner = text.substring(i + 3, Math.max(i + 3, j - 3));
                int bar = inner.indexOf('|');
                out.append(bar >= 0 ? params(inner.substring(bar + 1)) : "");
                i = j;
            } else {
                out.append(text.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    private static boolean atLineStart(String text, int i) {
        int j = i - 1;
        while (j >= 0 && (text.charAt(j) == ' ' || text.charAt(j) == '\t')) {
            j--;
        }
        return j < 0 || text.charAt(j) == '\n';
    }

    private static void flush(FayteWikiData.Section s, StringBuilder para) {
        if (para.length() > 0) {
            String p = clean(para.toString(), null);
            if (!p.isEmpty()) {
                s.paras.add(p);
            }
            para.setLength(0);
        }
    }

    private static String unwraplayout(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            if (!lines[i].trim().startsWith("{|")) {
                out.append(lines[i]).append('\n');
                i++;
                continue;
            }
            int depth = 0;
            int nest = 0;
            int end = -1;
            boolean heads = false;
            for (int k = i; k < lines.length; k++) {
                String t = lines[k].trim();
                if (depth == 0) {
                    if (t.startsWith("{|")) {
                        nest++;
                    } else if (t.startsWith("|}")) {
                        nest--;
                        if (nest == 0) {
                            end = k;
                            break;
                        }
                    } else if (HEADING.matcher(t).matches()) {
                        heads = true;
                    }
                }
                depth += count(lines[k], "{{") - count(lines[k], "}}");
            }
            if (end < 0 || !heads) {
                out.append(lines[i]).append('\n');
                i++;
                continue;
            }
            depth = 0;
            nest = 0;
            for (int k = i; k <= end; k++) {
                String t = lines[k].trim();
                String keep = lines[k];
                if (depth == 0) {
                    if (t.startsWith("{|")) {
                        nest++;
                        if (nest == 1) {
                            keep = null;
                        }
                    } else if (t.startsWith("|}")) {
                        if (nest == 1) {
                            keep = null;
                        }
                        nest--;
                    } else if (nest == 1 && (t.startsWith("|-") || t.equals("|"))) {
                        keep = null;
                    } else if (nest == 1 && t.startsWith("|")) {
                        Matcher cm = CELL.matcher(t);
                        if (cm.matches()) {
                            keep = cm.group(1).trim().isEmpty() ? null : cm.group(1);
                        }
                    }
                }
                if (keep != null) {
                    out.append(keep).append('\n');
                }
                depth += count(lines[k], "{{") - count(lines[k], "}}");
            }
            i = end + 1;
        }
        return out.toString();
    }

    private static int count(String s, String sub) {
        int n = 0;
        for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + sub.length())) {
            n++;
        }
        return n;
    }

    private static int close(String text, int start) {
        int depth = 0;
        int i = start;

        while (i < text.length() - 1) {
            if (text.startsWith("{|", i) || text.startsWith("|}", i)) {
                i += 2;
            } else if (text.startsWith("{{", i)) {
                depth++;
                i += 2;
            } else if (text.startsWith("}}", i)) {
                depth--;
                i += 2;
                if (depth == 0) {
                    return i;
                }
            } else {
                i++;
            }
        }
        return -1;
    }

    private static List<String> splitargs(String inner) {
        List<String> ret = new ArrayList<>();
        int depth = 0;
        int ldepth = 0;
        StringBuilder cur = new StringBuilder();

        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (inner.startsWith("{|", i) || inner.startsWith("|}", i)) {
                cur.append(inner, i, i + 2);
                i++;
            } else if (inner.startsWith("{{", i)) {
                depth++;
                cur.append("{{");
                i++;
            } else if (inner.startsWith("}}", i)) {
                depth--;
                cur.append("}}");
                i++;
            } else if (inner.startsWith("[[", i)) {
                ldepth++;
                cur.append("[[");
                i++;
            } else if (inner.startsWith("]]", i)) {
                ldepth--;
                cur.append("]]");
                i++;
            } else if (c == '|' && depth == 0 && ldepth == 0) {
                ret.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        ret.add(cur.toString());
        return ret;
    }

    private static void addtemplate(FayteWikiData.Entry e, String inner, String title) {
        List<String> parts = splitargs(inner);
        String name = parts.get(0).trim();
        if (name.isEmpty()) {
            return;
        }
        Map<String, String> args = new LinkedHashMap<>();
        int pos = 1;

        for (int i = 1; i < parts.size(); i++) {
            String p = parts.get(i);
            int eq = p.indexOf('=');
            String k;
            String v;
            if (eq > 0 && p.lastIndexOf("{{", eq) < 0 && p.lastIndexOf("[[", eq) < 0) {
                k = p.substring(0, eq).trim();
                v = p.substring(eq + 1);
            } else {
                k = Integer.toString(pos++);
                v = p;
            }
            v = clean(v, title);
            if (!v.isEmpty()) {
                args.put(k, v);
            }
        }
        String key = name;
        int n = 2;
        while (e.tpl.containsKey(key)) {
            key = name + "#" + n++;
        }
        e.tpl.put(key, args);
    }

    public static String clean(String s, String title) {
        if (title != null) {
            s = s.replace("{{PAGENAME}}", title);
        }
        s = inline(s, title);
        s = TAGS.matcher(s).replaceAll(" ");
        Matcher fm = FILELINK.matcher(s);
        StringBuffer fb = new StringBuffer();

        while (fm.find()) {
            String whole = fm.group(0);
            List<String> parts = splitargs(whole.substring(2, whole.length() - 2));
            String name = parts.get(0);
            name = name.substring(name.indexOf(':') + 1).trim();
            String caption = "";
            String size = "";

            for (int k = parts.size() - 1; k >= 1; k--) {
                String p = parts.get(k).trim();
                Matcher sm = SIZE.matcher(p);
                if (sm.matches() && size.isEmpty()) {
                    size = sm.group(1) != null ? sm.group(1) : "";
                } else if (caption.isEmpty()
                        && !p.isEmpty()
                        && !PICOPT.matcher(p).matches()) {
                    caption = p;
                }
            }
            boolean valid = name.matches(".*[A-Za-z0-9].*\\.[A-Za-z]{3,4}") && !name.startsWith(".");
            fm.appendReplacement(
                    fb,
                    Matcher.quoteReplacement(valid ? FayteMarkup.block(FayteMarkup.PICTURE, name, caption, size) : ""));
        }
        fm.appendTail(fb);
        s = fb.toString();
        Matcher m = LINK.matcher(s);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            String target = m.group(1);
            String label = m.group(3) != null ? m.group(3) : target;
            if (target.startsWith(":")) {
                target = target.substring(1);
            }
            int hash = target.indexOf('#');
            if (hash >= 0) {
                target = target.substring(0, hash);
            }
            target = FayteMarkup.plain(target).trim();
            label = label.trim();
            m.appendReplacement(
                    sb,
                    Matcher.quoteReplacement(
                            target.isEmpty() || label.indexOf(FayteMarkup.LS) >= 0
                                    ? label
                                    : FayteMarkup.link(target, FayteMarkup.plain(label))));
        }
        m.appendTail(sb);
        s = sb.toString();
        m = EXTLINK.matcher(s);
        sb = new StringBuffer();

        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(3) != null ? m.group(3) : ""));
        }
        m.appendTail(sb);
        s = QUOTES.matcher(sb.toString()).replaceAll("");
        s = s.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("__NOTOC__", "")
                .replace("__TOC__", "");
        s = s.replace("{{", "")
                .replace("}}", "")
                .replace("[[", "")
                .replace("]]", "")
                .replace("{|", "")
                .replace("|}", "");
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    private static String inline(String s, String title) {
        StringBuilder out = new StringBuilder();
        int i = 0;

        while (i < s.length()) {
            if (s.startsWith("{{", i)) {
                int end = close(s, i);
                if (end < 0) {
                    out.append(s.substring(i));
                    break;
                }
                List<String> parts = splitargs(s.substring(i + 2, end - 2));
                String name = parts.get(0).trim().toLowerCase();
                if (name.equals("pagename") && title != null) {
                    out.append(title);
                } else if (name.equals("!")) {
                    out.append('|');
                } else if (name.equals("row") || name.equals("grid")) {
                    List<String> fields = new ArrayList<>();
                    String cols = "0";

                    for (int k = 1; k < parts.size(); k++) {
                        String cell = parts.get(k);
                        String ct = cell.trim();
                        Matcher cm = GRIDOPT.matcher(ct);
                        if (cm.matches()) {
                            cols = cm.group(1);
                        } else {
                            fields.add(FayteMarkup.enc(ct));
                        }
                    }
                    if (name.equals("row")) {
                        cols = "0";
                    }
                    fields.add(0, cols);
                    out.append("\n\n")
                            .append(FayteMarkup.block(FayteMarkup.GRID, fields.toArray(new String[0])))
                            .append("\n\n");
                } else if (name.startsWith("#replace:")) {
                    String src = parts.get(0).trim().substring(9);
                    String from = parts.size() > 1 ? parts.get(1) : "";
                    String to = parts.size() > 2 ? parts.get(2) : "";
                    out.append(from.isEmpty() ? src : src.replace(from, to));
                } else if (name.equals("icon")) {
                    if (parts.size() > 1) {
                        String ic =
                                FayteMarkup.plain(inline(parts.get(1), title)).trim();
                        String isz = parts.size() > 2 ? parts.get(2).trim().replaceAll("(?i)px$", "") : "";
                        if (!ic.isEmpty()) {
                            out.append(FayteMarkup.icon(isz.matches("\\d+") ? ic + FayteMarkup.BF + isz : ic));
                        }
                    }
                } else if (name.equals("notice") || name.equals("note") || name.equals("tip")) {
                    String a1 = parts.size() > 1 ? inline(parts.get(1), title).trim() : "";
                    String a2 = parts.size() > 2 ? inline(parts.get(2), title).trim() : "";
                    String nt = a2.isEmpty() ? "" : a1;
                    String nb = a2.isEmpty() ? a1 : a2;
                    if (nt.isEmpty() && !name.equals("notice")) {
                        nt = name.equals("tip") ? "Tip" : "Note";
                    }
                    out.append(FayteMarkup.block(FayteMarkup.NOTE, nt, nb));
                } else if (name.startsWith("#ifeq:")) {
                    if (parts.size() > 2) {
                        String a = name.substring(6).trim();
                        String b = parts.get(1).trim().toLowerCase();
                        String pick = a.equals(b) ? parts.get(2) : (parts.size() > 3 ? parts.get(3) : "");
                        out.append(inline(pick, title));
                    }
                } else if (name.startsWith("#if:")) {
                    if (!name.substring(4).trim().isEmpty() && parts.size() > 1) {
                        out.append(inline(parts.get(1), title));
                    } else if (parts.size() > 2) {
                        out.append(inline(parts.get(2), title));
                    }
                } else if (name.startsWith("#ask:")) {
                    String q = s.substring(i + 2, end - 2);
                    if (title != null) {
                        q = q.replace("{{PAGENAME}}", title);
                    }
                    String r = FayteWikiQuery.render(q);
                    if (r != null) {
                        out.append(r);
                    }
                } else if (name.startsWith("#")) {
                    out.append("");
                } else if (parts.size() > 1) {
                    String a = parts.get(1);
                    if (name.equals("i")
                            || name.equals("item")
                            || name.equals("l")
                            || name.equals("link")
                            || name.equals("s")
                            || name.equals("skill")) {
                        String label = FayteMarkup.plain(inline(a, title)).trim();
                        out.append(label.isEmpty() ? "" : FayteMarkup.link(label, label));
                    } else if (!a.contains("=")) {
                        out.append(inline(a, title));
                    }
                }
                i = end;
            } else {
                out.append(s.charAt(i));
                i++;
            }
        }
        return out.toString();
    }
}
