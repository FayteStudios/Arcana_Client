package haven;

import java.util.List;
import java.util.Map;

public class FayteItemFacts {
    private static final String[] HUMOURS = {"Blood", "Phlegm", "Yellow Bile", "Black Bile"};

    private static String dec(int v) {
        return String.format("%.1f", v / 1000.0);
    }

    private static String name(List<ItemInfo> info) {
        ItemInfo.Name n = ItemInfo.find(ItemInfo.Name.class, info);
        return n == null ? null : n.str.text;
    }

    private static String basefood(FayteWikiData.Entry e, int i) {
        if (e == null) {
            return null;
        }
        for (Map.Entry<String, Map<String, String>> t : e.tpl.entrySet()) {
            String tn = t.getKey().split("#")[0].trim();
            if (tn.equals("SpecialFood") || tn.equals("Food")) {
                String lo = t.getValue().get("Min " + HUMOURS[i]);
                String hi = t.getValue().get("Max " + HUMOURS[i]);
                if (lo != null || hi != null) {
                    return (lo == null ? "?" : lo.trim()) + " \u2013 " + (hi == null ? "?" : hi.trim());
                }
            }
        }
        return null;
    }

    public static FayteWikiEntry entry(GItem item, String title, String icon, FayteWikiData.Entry base) {
        FayteWikiEntry g = new FayteWikiEntry();
        g.name = title;
        g.icon = icon;
        g.category = "this item";
        List<ItemInfo> info;
        try {
            info = item.info();
        } catch (Loading e) {
            g.summary = "Still loading this item's details\u2026";
            return g;
        }
        FayteWikiEntry.Section mine = new FayteWikiEntry.Section();
        mine.title = "This item";
        Alchemy a = ItemInfo.find(Alchemy.class, info);
        if (a != null) {
            mine.rows.add(new String[] {"Purity", String.format("%.2f%%", 100 * a.purity())});
        }
        for (ItemInfo ii : info) {
            if (ii instanceof ItemInfo.AdHoc) {
                String t = ((ItemInfo.AdHoc) ii).str.text;
                int c = t.indexOf(':');
                if (c > 0 && c < t.length() - 1) {
                    mine.rows.add(new String[] {
                        t.substring(0, c).trim(), t.substring(c + 1).trim()
                    });
                } else {
                    mine.rows.add(new String[] {"Note", t});
                }
            } else if (ii instanceof ItemInfo.Contents) {
                String n = name(((ItemInfo.Contents) ii).sub);
                if (n != null) {
                    mine.rows.add(new String[] {"Contains", n});
                }
            } else if (ii instanceof FoodInfo) {
                int[] t = ((FoodInfo) ii).tempers;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 4 && i < t.length; i++) {
                    sb.append(i > 0 ? ", " : "").append(HUMOURS[i]).append(' ').append(dec(t[i]));
                }
                mine.rows.add(new String[] {"Heals", sb.toString()});
            }
        }
        g.sections.add(mine);
        GobbleInfo gi = ItemInfo.find(GobbleInfo.class, info);
        if (gi != null) {
            FayteWikiEntry.Section food = new FayteWikiEntry.Section();
            food.title = base != null ? "Gobble points: this item vs. base entry" : "Gobble points";
            for (int i = 0; i < 4; i++) {
                String cur = dec(gi.l[i]) + " \u2013 " + dec(gi.h[i]);
                String b = basefood(base, i);
                food.rows.add(new String[] {HUMOURS[i], b != null ? cur + "   (base " + b + ")" : cur});
            }
            int min = (gi.ft + 30) / 60;
            food.rows.add(new String[] {"Full and fed up", String.format("%02d:%02d", min / 60, min % 60)});
            g.sections.add(food);
        }
        if (mine.rows.isEmpty() && gi == null) {
            g.summary = "This item has no details beyond its entry.";
        }
        return g;
    }
}
