package haven;

public class FayteTextSize {
    public static final String ALMANAC = "fayte_text_almanac";
    public static final String NOTES = "fayte_notes_font";
    public static final String UI = "fayte_text_ui";

    public static int pct(String key, int def) {
        try {
            return Math.max(80, Math.min(200, Integer.parseInt(Utils.getpref(key, Integer.toString(def)))));
        } catch (Exception e) {
            return def;
        }
    }

    public static int pct(String key) {
        try {
            return Math.max(80, Math.min(200, Integer.parseInt(Utils.getpref(key, "100"))));
        } catch (Exception e) {
            return 100;
        }
    }

    public static class Wnd extends Window {
        private static Wnd instance = null;
        private final Label alm;
        private final Label notes;

        private Wnd(Widget parent) {
            super(
                    new Coord(FayteSkin.s(260), FayteSkin.s(160)),
                    new Coord(FayteSkin.s(330), FayteSkin.s(110)),
                    parent,
                    "Text size");
            justclose = true;
            new Label(new Coord(0, 0), this, "Almanac pages and lists (applies after restart):");
            alm = new Label(new Coord(FayteSkin.s(80), FayteSkin.s(24)), this, "");
            new Button(new Coord(0, FayteSkin.s(20)), FayteSkin.s(34), this, "\u2212") {
                public void click() {
                    Wnd.this.step(ALMANAC, -10);
                }
            };
            new Button(new Coord(FayteSkin.s(38), FayteSkin.s(20)), FayteSkin.s(34), this, "+") {
                public void click() {
                    Wnd.this.step(ALMANAC, 10);
                }
            };
            new Label(
                    new Coord(0, FayteSkin.s(54)),
                    this,
                    "Notes (applies when Notes reopens; also A\u2212 / A+ there):");
            notes = new Label(new Coord(FayteSkin.s(80), FayteSkin.s(78)), this, "");
            new Button(new Coord(0, FayteSkin.s(74)), FayteSkin.s(34), this, "\u2212") {
                public void click() {
                    Wnd.this.notes(-2);
                }
            };
            new Button(new Coord(FayteSkin.s(38), FayteSkin.s(74)), FayteSkin.s(34), this, "+") {
                public void click() {
                    Wnd.this.notes(2);
                }
            };
            update();
            pack();
        }

        private void step(String key, int d) {
            Utils.setpref(key, Integer.toString(Math.max(80, Math.min(200, pct(key) + d))));
            update();
        }

        private void notes(int d) {
            int pt = FayteAlmanacWnd.prefint(NOTES, 20);
            Utils.setpref(NOTES, Integer.toString(Math.max(10, Math.min(40, pt + d))));
            update();
        }

        private void update() {
            alm.settext(pct(ALMANAC) + "%");
            notes.settext(FayteAlmanacWnd.prefint(NOTES, 20) + " pt");
        }

        public static void toggle(GameUI gui) {
            if (instance != null && instance.attached()) {
                instance.ui.destroy(instance);
                instance = null;
            } else if (gui != null) {
                instance = new Wnd(gui);
            }
        }

        @Override
        public void destroy() {
            if (instance == this) {
                instance = null;
            }
            super.destroy();
        }

        @Override
        protected boolean compactstyle() {
            return true;
        }
    }
}
