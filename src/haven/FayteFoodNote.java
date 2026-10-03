package haven;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.List;

public class FayteFoodNote {
    private static final Text.Foundry f =
            new Text.Foundry(new Font("SansSerif", Font.PLAIN, 11), new Color(0xC8, 0xAA, 0x62)).aa(true);

    public static String groups(WItem w) {
        try {
            List<ItemInfo> info = w.item.info();
            if (!FayteCraving.groups(w.item).isEmpty()) {
                return null;
            }
            String ct = ItemInfo.getContent(info);
            String name = null;
            if (ct != null) {
                String[] p = ct.split(" ", 4);
                name = p.length == 4 && p[2].equalsIgnoreCase("of") ? p[3] : ct;
            }
            List<String> g = name == null ? null : FayteCraving.wikigroups(name);
            if (g == null || g.isEmpty()) {
                g = FayteCraving.wikigroups(FayteAlmanac.itemname(w.item));
            }
            return g == null || g.isEmpty() ? null : String.join(", ", g);
        } catch (Loading e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static BufferedImage tip(WItem w, BufferedImage tip) {
        if (tip == null || !FayteSkin.on()) {
            return tip;
        }
        String g = groups(w);
        if (g == null) {
            return tip;
        }
        return ItemInfo.catimgs(2, tip, f.render("Food group: " + g).img);
    }
}
