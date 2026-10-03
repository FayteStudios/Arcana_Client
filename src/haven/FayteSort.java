package haven;

import java.util.Comparator;

public class FayteSort {
    public static final String[] NAMES = {
        "None",
        "Name",
        "Purity",
        "Weight",
        "Type",
        "Gobble: Blood",
        "Gobble: Phlegm",
        "Gobble: Yellow Bile",
        "Gobble: Black Bile"
    };

    public static String name(WItem w) {
        try {
            ItemInfo.Name n = ItemInfo.find(ItemInfo.Name.class, w.item.info());
            return n != null ? n.str.text : w.item.resname();
        } catch (Loading e) {
            return "";
        }
    }

    public static double purity(WItem w) {
        try {
            Alchemy a = ItemInfo.find(Alchemy.class, w.item.info());
            return a == null ? -1 : a.purity();
        } catch (Loading e) {
            return -1;
        }
    }

    private static double weight(WItem w) {
        try {
            for (ItemInfo ii : w.item.info()) {
                if (ii instanceof ItemInfo.AdHoc) {
                    String t = ((ItemInfo.AdHoc) ii).str.text;
                    if (t.startsWith("Weight")) {
                        String num = t.replaceAll("[^0-9.]", "");
                        return num.isEmpty() ? -1 : Double.parseDouble(num);
                    }
                }
            }
        } catch (Loading | NumberFormatException e) {
        }
        return -1;
    }

    private static String type(WItem w) {
        try {
            String r = w.item.resname();
            int i = r.lastIndexOf('/');
            return i > 0 ? r.substring(0, i) : r;
        } catch (Loading e) {
            return "";
        }
    }

    private static int gobble(WItem w, int t) {
        try {
            GobbleInfo g = ItemInfo.find(GobbleInfo.class, w.item.info());
            return g == null ? Integer.MIN_VALUE : g.h[t];
        } catch (Loading e) {
            return Integer.MIN_VALUE;
        }
    }

    private static final Comparator<WItem> BYNAME = (a, b) -> name(a).compareToIgnoreCase(name(b));

    public static Comparator<WItem> get(int i) {
        switch (i) {
            case 1:
                return BYNAME;
            case 2:
                return (a, b) -> {
                    int c = Double.compare(purity(b), purity(a));
                    return c != 0 ? c : BYNAME.compare(a, b);
                };
            case 3:
                return (a, b) -> {
                    int c = Double.compare(weight(b), weight(a));
                    return c != 0 ? c : BYNAME.compare(a, b);
                };
            case 4:
                return (a, b) -> {
                    int c = type(a).compareTo(type(b));
                    return c != 0 ? c : BYNAME.compare(a, b);
                };
            case 5:
            case 6:
            case 7:
            case 8:
                final int t = i - 5;
                return (a, b) -> {
                    int c = Integer.compare(gobble(b, t), gobble(a, t));
                    return c != 0 ? c : BYNAME.compare(a, b);
                };
            default:
                return null;
        }
    }
}
