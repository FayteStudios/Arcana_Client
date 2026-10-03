package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FayteContents {
    private static final Pattern QTY = Pattern.compile(
            "^([0-9]+(?:[.,][0-9]+)?)\\s*(l|kg|g|oz)?\\.?\\s+(?:of\\s+)?(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMT =
            Pattern.compile("^([0-9]+(?:[.,][0-9]+)?)\\s*(l|kg|g|oz)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Tex> cache = new HashMap<>();
    private static final Object[][] PALETTE = {
        {"water", new Color(0x3A, 0x8E, 0xE8)},
        {"brine", new Color(0x7A, 0xB8, 0xD8)},
        {"wine", new Color(0x9A, 0x22, 0x5A)},
        {"milk", new Color(0xF4, 0xF2, 0xEA)},
        {"cream", new Color(0xF4, 0xE8, 0xB8)},
        {"butter", new Color(0xF2, 0xDA, 0x70)},
        {"honey", new Color(0xE8, 0xA0, 0x20)},
        {"syrup", new Color(0xC0, 0x70, 0x20)},
        {"oil", new Color(0xE0, 0xC8, 0x40)},
        {"vinegar", new Color(0xC8, 0xA8, 0x70)},
        {"beer", new Color(0xD8, 0x98, 0x30)},
        {"ale", new Color(0xD8, 0x98, 0x30)},
        {"cider", new Color(0xE0, 0xB0, 0x40)},
        {"rum", new Color(0x9A, 0x58, 0x20)},
        {"whisk", new Color(0xB0, 0x68, 0x20)},
        {"blood", new Color(0xA8, 0x10, 0x10)},
        {"ink", new Color(0x20, 0x20, 0x40)},
        {"tar", new Color(0x28, 0x22, 0x1C)},
        {"lye", new Color(0xD8, 0xE8, 0xC8)},
        {"tea", new Color(0x7A, 0x9A, 0x40)},
        {"coffee", new Color(0x5A, 0x38, 0x20)},
        {"flour", new Color(0xEE, 0xE4, 0xCC)},
        {"meal", new Color(0xE8, 0xD0, 0x80)},
        {"oat", new Color(0xD8, 0xC8, 0x98)},
        {"sugar", new Color(0xFA, 0xFA, 0xFA)},
        {"salt", new Color(0xE4, 0xEC, 0xF4)},
        {"pepper", new Color(0x40, 0x38, 0x30)},
        {"gold", new Color(0xF0, 0xC8, 0x30)},
        {"silver", new Color(0xC8, 0xD0, 0xD8)},
        {"cotton", new Color(0xF4, 0xF4, 0xF0)},
        {"cabbage", new Color(0x6C, 0xB0, 0x4A)},
        {"pumpkin", new Color(0xE8, 0x80, 0x20)},
        {"cereal", new Color(0xD8, 0xB8, 0x60)},
        {"wheat", new Color(0xD8, 0xB8, 0x60)},
        {"barley", new Color(0xC8, 0xB0, 0x68)},
        {"rye", new Color(0xB0, 0x98, 0x60)},
        {"potato", new Color(0xA8, 0x80, 0x50)},
        {"tobacco", new Color(0x8A, 0x70, 0x30)},
        {"rose", new Color(0xE8, 0x68, 0x98)},
        {"lavender", new Color(0x9A, 0x78, 0xD0)},
        {"mint", new Color(0x50, 0xC8, 0x90)},
        {"red", new Color(0xD0, 0x30, 0x30)},
        {"blue", new Color(0x30, 0x60, 0xD0)},
        {"green", new Color(0x40, 0xA8, 0x40)},
        {"yellow", new Color(0xE8, 0xD0, 0x30)},
        {"purple", new Color(0x88, 0x40, 0xB8)},
        {"black", new Color(0x30, 0x30, 0x30)},
        {"white", new Color(0xF0, 0xF0, 0xF0)},
        {"brown", new Color(0x80, 0x58, 0x30)},
        {"orange", new Color(0xE8, 0x80, 0x20)},
        {"pink", new Color(0xF0, 0x90, 0xB0)},
        {"corn", new Color(0xF0, 0xD0, 0x40)},
        {"maize", new Color(0xF0, 0xD0, 0x40)},
    };

    public static boolean on() {
        return FayteSkin.on();
    }

    public static String vessel(List<ItemInfo> infos) {
        for (ItemInfo ii : infos) {
            if (!(ii instanceof ItemInfo.Contents)) {
                continue;
            }
            String name = null;
            int names = 0;
            String amt = null;
            for (ItemInfo sub : ((ItemInfo.Contents) ii).sub) {
                if (sub instanceof ItemInfo.Name) {
                    names++;
                    name = ((ItemInfo.Name) sub).str.text;
                } else if (sub instanceof ItemInfo.AdHoc) {
                    Matcher m = AMT.matcher(((ItemInfo.AdHoc) sub).str.text.trim());
                    if (m.matches()) {
                        amt = m.group(1) + " " + m.group(2);
                    }
                }
            }
            if (names != 1 || name == null) {
                return null;
            }
            if (QTY.matcher(name.trim()).matches()) {
                return name;
            }
            return amt != null ? amt + " of " + name : null;
        }
        return null;
    }

    public static String what(String raw) {
        Matcher m = QTY.matcher(raw.trim());
        String n = m.matches() ? m.group(3) : raw.trim();
        return n.isEmpty() ? n : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    public static Color color(String raw) {
        String n = what(raw).toLowerCase();
        String best = null;
        Color bc = null;
        for (Object[] p : PALETTE) {
            String k = (String) p[0];
            if (n.contains(k) && (best == null || k.length() > best.length())) {
                best = k;
                bc = (Color) p[1];
            }
        }
        if (bc != null) {
            return bc;
        }
        float h = Math.floorMod(n.hashCode(), 360) / 360f;
        return Color.getHSBColor(h, 0.55f, 0.85f);
    }

    public static Tex tint(Resource res, String raw) {
        Color c = color(raw);
        String key = res.name + "|" + c.getRGB();
        synchronized (cache) {
            Tex t = cache.get(key);
            if (t != null) {
                return t;
            }
        }
        Resource.Image li = res.layer(Resource.imgc);
        if (li == null || li.img == null) {
            return null;
        }
        BufferedImage src = li.img;
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = TexI.mkbuf(new Coord(w, h));
        float cr = c.getRed() / 255f, cg = c.getGreen() / 255f, cb = c.getBlue() / 255f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xff;
                if (a == 0) {
                    continue;
                }
                float r = ((argb >> 16) & 0xff) / 255f, g = ((argb >> 8) & 0xff) / 255f, b = (argb & 0xff) / 255f;
                float lum = 0.3f * r + 0.59f * g + 0.11f * b;
                float k = 0.35f + 0.85f * lum;
                float tr = Math.min(1f, cr * k), tg = Math.min(1f, cg * k), tb = Math.min(1f, cb * k);
                float mix = 0.55f;
                int or = (int) ((r * (1 - mix) + tr * mix) * 255),
                        og = (int) ((g * (1 - mix) + tg * mix) * 255),
                        ob = (int) ((b * (1 - mix) + tb * mix) * 255);
                out.setRGB(x, y, (a << 24) | (or << 16) | (og << 8) | ob);
            }
        }
        Tex t = new TexI(out);
        synchronized (cache) {
            if (cache.size() > 300) {
                cache.clear();
            }
            cache.put(key, t);
        }
        return t;
    }
}
