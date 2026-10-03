package haven;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FayteTip {
    private static final Pattern QTY = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)\\s*(?:x\\s+|of\\s+)?(.+)$");

    public static boolean on() {
        return FayteSkin.on();
    }

    static double weightof(List<ItemInfo> info) {
        for (ItemInfo ii : info) {
            if (ii instanceof ItemInfo.AdHoc) {
                String t = ((ItemInfo.AdHoc) ii).str.text;
                if (t.startsWith("Weight")) {
                    String num = t.replaceAll("[^0-9.]", "");
                    try {
                        return num.isEmpty() ? -1 : Double.parseDouble(num);
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                }
            }
        }
        return -1;
    }

    private static double wikiweight(String name) {
        FayteWikiData.Entry e = name == null ? null : FayteWikiData.find(name);
        if (e == null || e.tpl == null) {
            return -1;
        }
        for (Map<String, String> t : e.tpl.values()) {
            String w = t.get("Weight");
            if (w != null) {
                try {
                    return Double.parseDouble(w.trim());
                } catch (NumberFormatException ex) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static double contentsweight(ItemInfo.Contents c) {
        double w = weightof(c.sub);
        if (w >= 0) {
            return w;
        }
        String n = null;
        for (ItemInfo ii : c.sub) {
            if (ii instanceof ItemInfo.Name) {
                n = ((ItemInfo.Name) ii).str.text;
            }
        }
        if (n == null) {
            return -1;
        }
        Matcher m = QTY.matcher(n.trim());
        if (!m.matches()) {
            return -1;
        }
        String what = m.group(2).trim();
        if (what.matches("(?i)(l|kg|g|oz|lb)\\b.*")) {
            return -1;
        }
        double each = wikiweight(what);
        return each < 0 ? -1 : each * Double.parseDouble(m.group(1));
    }

    private static final java.awt.Color LABEL = new java.awt.Color(0xF0, 0xDA, 0x9A);

    public static BufferedImage label(String l) {
        return l == null ? null : FayteSkin.labelf.render(l, LABEL).img;
    }

    public static BufferedImage longtip(List<ItemInfo> info) {
        return longtip(info, null);
    }

    public static BufferedImage longtip(List<ItemInfo> info, String label) {
        List<BufferedImage> top = new ArrayList<>();
        List<BufferedImage> rest = new ArrayList<>();
        double base = -1;
        double inner = -1;
        for (ItemInfo ii : info) {
            if (!(ii instanceof ItemInfo.Tip)) {
                continue;
            }
            if (ii instanceof ItemInfo.AdHoc && ((ItemInfo.AdHoc) ii).str.text.startsWith("Weight")) {
                base = weightof(Collections.singletonList(ii));
                continue;
            }
            if (ii instanceof ItemInfo.Contents) {
                inner = contentsweight((ItemInfo.Contents) ii);
            }
            try {
                BufferedImage img = ((ItemInfo.Tip) ii).longtip();
                if (img == null) {
                    continue;
                }
                if (ii instanceof ItemInfo.Name || ii instanceof Alchemy) {
                    top.add(img);
                    if (ii instanceof ItemInfo.Name && label != null) {
                        top.add(label(label));
                        label = null;
                    }
                } else {
                    rest.add(img);
                }
            } catch (IllegalArgumentException e) {
            }
        }
        if (base >= 0) {
            String s;
            if (inner > 0) {
                s = String.format("Weight: %.2f (%.2f empty + %.2f contents)", base + inner, base, inner);
            } else {
                s = String.format("Weight: %.2f", base);
            }
            rest.add(Text.render(s).img);
        }
        top.addAll(rest);
        if (top.isEmpty()) {
            return null;
        }
        return ItemInfo.catimgs(3, top.toArray(new BufferedImage[0]));
    }
}
