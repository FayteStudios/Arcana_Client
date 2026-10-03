package haven;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FayteFont {
    public static final String[] FAMILIES = {"Segoe UI", "Verdana", "Tahoma", "Arial"};
    public static final String[] SPACINGS = {"Normal", "Wide", "Wider"};
    private static final double[] TRACK = {0.0, 0.04, 0.08};
    private static String family = null;

    public static synchronized String family() {
        if (family == null) {
            Set<String> have = new HashSet<>();
            try {
                have.addAll(Arrays.asList(
                        GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            } catch (Exception e) {
                FayteLog.once("FayteFont.family", e);
            }
            String want = Utils.getpref("fayte_font", "");
            if (!want.isEmpty() && have.contains(want)) {
                family = want;
            } else {
                family = "SansSerif";
                for (String f : FAMILIES) {
                    if (have.contains(f)) {
                        family = f;
                        break;
                    }
                }
            }
        }
        return family;
    }

    public static int spacing() {
        try {
            return Math.max(0, Math.min(TRACK.length - 1, Integer.parseInt(Utils.getpref("fayte_font_spacing", "0"))));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static Font font(int style, int size) {
        Font f = new Font(family(), style, size);
        double t = TRACK[spacing()];
        return t == 0.0 ? f : f.deriveFont(Collections.singletonMap(TextAttribute.TRACKING, t));
    }
}
