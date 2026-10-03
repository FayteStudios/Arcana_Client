package haven;

public class FayteLayout {
    private static FayteMainBar bar = null;

    public static boolean custom() {
        return FayteModules.STYLE.on() && FayteModules.ALMANAC.on();
    }

    public static String profile() {
        return custom() ? "" : "classic_";
    }

    public static void tick(GameUI gui) {
        boolean c = custom();
        if (gui.mainmenu != null && gui.mainmenu.visible == c) {
            gui.mainmenu.show(!c);
        }
        if (gui.menu != null && gui.menu.visible == c) {
            gui.menu.show(!c);
        }
        if (c && (bar == null || bar.parent != gui)) {
            bar = FayteHud.reg(
                    new FayteMainBar(
                            new Coord(gui.sz.x - FayteMainBar.width() - 10, gui.sz.y - FayteMainBar.height() - 10),
                            gui),
                    "mainbar");
        } else if (!c && bar != null) {
            if (bar.attached()) {
                gui.ui.destroy(bar);
            }
            bar = null;
        }
    }
}
