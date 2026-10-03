package haven;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FayteWikiGlance {
    private static final Pattern COUNTED = Pattern.compile("^\\s*([^;]+?)\\s*;\\s*(\\d+)\\s*$");
    private static final String[] HUMOURS = {"Blood", "Phlegm", "Yellow Bile", "Black Bile"};
    private static final Map<String, String> SECTIONS = new LinkedHashMap<>();

    static {
        SECTIONS.put("Facts", "At a glance");
        SECTIONS.put("Foraged", "Foraging");
        SECTIONS.put("Creature", "Creature");
        SECTIONS.put("Crafted", "Crafting");
        SECTIONS.put("AddCraft", "Also made with");
        SECTIONS.put("Action", "Action");
        SECTIONS.put("SpecialFood", "Food");
        SECTIONS.put("Food", "Food");
        SECTIONS.put("VariableFoodIngredients", "Food");
        SECTIONS.put("VariableFoodPumpkins", "Food");
        SECTIONS.put("Inspirational", "Inspiration");
        SECTIONS.put("Inspirational Gear", "Inspiration");
        SECTIONS.put("Inspirational Artifact", "Inspiration");
        SECTIONS.put("Clothing", "Clothing");
        SECTIONS.put("Artifact", "Artifact");
        SECTIONS.put("Structure", "Structure");
        SECTIONS.put("Skill", "Skill");
    }

    public static String title(Map<String, String> a) {
        for (Map.Entry<String, String> p : a.entrySet()) {
            if (p.getKey().trim().equalsIgnoreCase("Title")) {
                String t = FayteMarkup.plain(p.getValue()).trim();
                return t.isEmpty() ? null : t;
            }
        }
        return null;
    }

    public static boolean shown(String template) {
        return SECTIONS.containsKey(template);
    }

    public static String linkify(String name) {
        String n = name.trim();
        if (n.indexOf(FayteMarkup.IS) == 0) {
            int ie = n.indexOf(FayteMarkup.IE);
            if (ie > 0) {
                String rest = n.substring(ie + 1).trim();
                return n.substring(0, ie + 1) + (rest.isEmpty() ? "" : " " + linkify(rest));
            }
        }
        if (n.isEmpty() || n.indexOf(FayteMarkup.LS) >= 0) {
            return n;
        } else {
            return FayteWikiData.find(n) != null ? FayteMarkup.link(n, n) : n;
        }
    }

    public static String counted(String v) {
        String[] parts = v.split(",");
        List<String> out = new ArrayList<>();

        for (String p : parts) {
            if (!p.trim().isEmpty()) {
                Matcher m = COUNTED.matcher(p);
                if (m.matches()) {
                    out.add(linkify(m.group(1)) + " \u00d7" + m.group(2));
                } else {
                    out.add(linkify(p));
                }
            }
        }
        return out.isEmpty() ? v.trim() : String.join(", ", out);
    }

    private static String humoursrow(Map<String, String> a) {
        String h = a.get("Heals");
        if (h == null) {
            return null;
        } else {
            String[] v = h.split(",");
            if (v.length != 4) {
                return h;
            } else {
                StringBuilder sb = new StringBuilder();

                for (int i = 0; i < 4; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(HUMOURS[i]).append(' ').append(v[i].trim());
                }
                return sb.toString();
            }
        }
    }

    private static String foodtable(Map<String, String> a) {
        StringBuilder t = new StringBuilder("{|\n! !! Blood !! Phlegm !! Yellow !! Black\n");
        boolean any = false;
        String h = a.get("Heals");
        if (h != null && h.split(",").length == 4) {
            String[] v = h.split(",");
            t.append("|-\n! Heals\n| ")
                    .append(v[0].trim())
                    .append(" || ")
                    .append(v[1].trim())
                    .append(" || ")
                    .append(v[2].trim())
                    .append(" || ")
                    .append(v[3].trim())
                    .append('\n');
            any = true;
        }
        for (String kind : new String[] {"Min", "Max"}) {
            StringBuilder r = new StringBuilder("|-\n! Gluttony " + kind.toLowerCase() + "\n| ");
            boolean has = false;

            for (int i = 0; i < 4; i++) {
                String v = a.get(kind + " " + HUMOURS[i]);
                has |= v != null;
                r.append(i > 0 ? " || " : "").append(v == null ? "" : v.trim());
            }
            if (has) {
                t.append(r).append('\n');
                any = true;
            }
        }
        return any
                ? FayteMarkup.block(
                        FayteMarkup.TABLE, FayteMarkup.enc(t.append("|}\n").toString()))
                : null;
    }

    private static void foodrows(Map<String, String> a, List<String[]> rows) {
        if (foodtable(a) == null) {
            String heals = humoursrow(a);
            if (heals != null) {
                rows.add(new String[] {"Heals", heals});
            }
        }
        for (String k : new String[] {"Uses", "Gluttony Time", "Food Groups"}) {
            if (a.containsKey(k)) {
                rows.add(new String[] {
                    k.equals("Gluttony Time") ? "Full and fed for" : k,
                    k.equals("Food Groups") ? counted(a.get(k)) : a.get(k)
                });
            }
        }
        for (String kind : new String[] {"Reduce", "Restore"}) {
            for (int i = 1; i <= 3; i++) {
                String grp = a.get("Food" + kind + i);
                if (grp != null) {
                    String pct = a.get("%" + kind + i);
                    String ch = a.get("%Chance" + kind + i);
                    String v = linkify(grp)
                            + (pct != null ? " by " + pct + "%" : "")
                            + (ch != null ? " (" + ch + "% chance)" : "");
                    rows.add(new String[] {kind.equals("Reduce") ? "Reduces" : "Restores", v});
                }
            }
        }
    }

    public static FayteWikiEntry full(FayteWikiData.Entry e, String icon) {
        FayteWikiEntry g = glance(e, icon);
        boolean skipped = false;

        for (FayteWikiData.Section s : e.sections) {
            boolean first = true;

            for (String p : s.paras) {
                if (!skipped && p.equals(e.summary)) {
                    skipped = true;
                    continue;
                }
                FayteWikiEntry.Section ns = new FayteWikiEntry.Section();
                ns.title = first && s.h != null && !s.h.isEmpty() ? s.h : null;
                ns.text = p;
                g.sections.add(ns);
                first = false;
            }
        }
        return g;
    }

    public static FayteWikiEntry glance(FayteWikiData.Entry e, String icon) {
        FayteWikiEntry g = new FayteWikiEntry();
        g.name = e.title;
        g.image = e.image;
        g.imagesize = e.imagesize;

        for (Map.Entry<String, Map<String, String>> t : e.tpl.entrySet()) {
            if (t.getKey().split("#")[0].trim().equalsIgnoreCase("Icons")) {
                for (Map.Entry<String, String> a : t.getValue().entrySet()) {
                    if (a.getKey().matches("\\d+")) {
                        String n = FayteMarkup.plain(a.getValue()).trim();
                        if (!n.isEmpty()) {
                            g.icons.add(n);
                        }
                    }
                }
            }
        }
        g.icon = icon == null || icon.isEmpty() ? null : icon;
        g.summary = e.summary;
        List<String> kinds = new ArrayList<>();
        Map<String, FayteWikiEntry.Section> bytitle = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, String>> t : e.tpl.entrySet()) {
            String tname = t.getKey().split("#")[0].trim();
            String deftitle = SECTIONS.get(tname);
            if (deftitle != null) {
                Map<String, String> a = t.getValue();
                String own = title(a);
                String st = own != null ? own : deftitle;
                FayteWikiEntry.Section s = bytitle.get(st);
                if (s == null) {
                    s = new FayteWikiEntry.Section();
                    s.title = st;
                    bytitle.put(s.title, s);
                }
                if (own == null && !kinds.contains(deftitle)) {
                    kinds.add(deftitle);
                }
                if (deftitle.equals("Food")) {
                    if (s.lead == null) {
                        s.lead = foodtable(a);
                    }
                    foodrows(a, s.rows);
                } else {
                    for (Map.Entry<String, String> p : a.entrySet()) {
                        String k = p.getKey();
                        if (tname.equals("Skill") && k.equals("Description")) {
                            if (g.summary == null) {
                                g.summary = p.getValue();
                            }
                        } else if (!k.matches("\\d+") && !k.trim().equalsIgnoreCase("Title")) {
                            s.rows.add(new String[] {k, counted(p.getValue())});
                        }
                    }
                }
            }
        }
        for (FayteWikiEntry.Section s : bytitle.values()) {
            if (!s.rows.isEmpty() || s.lead != null) {
                g.sections.add(s);
            }
        }
        List<String> chips = new ArrayList<>();

        for (String k : kinds) {
            if (!chips.contains(k) && !k.equals("Also made with") && !k.equals("At a glance")) {
                chips.add(k);
            }
        }
        if (chips.isEmpty() && !e.cats.isEmpty()) {
            chips.add(e.cats.get(0));
        }
        g.category = chips.isEmpty() ? null : String.join(" \u00b7 ", chips);
        return g;
    }
}
