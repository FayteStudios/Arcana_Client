package haven;

public class FayteConfirm extends Window {
    private static FayteConfirm current = null;
    private final Runnable yes;
    private final String key;

    public static void ask(GameUI gui, String key, String title, String msg, Runnable yes) {
        String saved = Utils.getpref("fayte_ask_" + key, "");
        if (saved.equals("yes")) {
            yes.run();
            return;
        } else if (saved.equals("no")) {
            return;
        }
        if (current != null && current.attached()) {
            current.ui.destroy(current);
        }
        current = new FayteConfirm(gui, key, title, msg, yes);
    }

    public static void warn(GameUI gui, String title, String msg, Runnable yes) {
        if (current != null && current.attached()) {
            current.ui.destroy(current);
        }
        current = new FayteConfirm(gui, null, title, msg, yes);
    }

    public static void forget(String key) {
        Utils.setpref("fayte_ask_" + key, "");
    }

    private FayteConfirm(GameUI gui, String key, String title, String msg, Runnable yes) {
        super(Coord.z, new Coord(FayteSkin.s(420), FayteSkin.s(150)), gui, title);
        this.yes = yes;
        this.key = key;
        justclose = true;
        Text t = FayteSkin.labelf.renderwrap(msg, FayteSkin.TEXT, asz.x);
        new Img(Coord.z, t.tex(), this);
        int y = t.sz().y + FayteSkin.s(12);
        int w = (asz.x - FayteSkin.s(6)) / 2;
        new Button(new Coord(0, y), w, this, "Yes") {
            @Override
            public void click() {
                FayteConfirm.this.answer(true, false);
            }
        };
        new Button(new Coord(w + FayteSkin.s(6), y), w, this, "No") {
            @Override
            public void click() {
                FayteConfirm.this.answer(false, false);
            }
        };
        if (key == null) {
            resize(new Coord(asz.x, y + Button.bh() + 2));
            c = gui.sz.sub(sz).div(2);
            return;
        }
        y += Button.bh() + FayteSkin.s(6);
        new Button(new Coord(0, y), w, this, "Yes, don't ask again") {
            @Override
            public void click() {
                FayteConfirm.this.answer(true, true);
            }
        };
        new Button(new Coord(w + FayteSkin.s(6), y), w, this, "No, don't ask again") {
            @Override
            public void click() {
                FayteConfirm.this.answer(false, true);
            }
        };
        resize(new Coord(asz.x, y + Button.bh() + 2));
        c = gui.sz.sub(sz).div(2);
    }

    private void answer(boolean ok, boolean remember) {
        if (remember) {
            Utils.setpref("fayte_ask_" + key, ok ? "yes" : "no");
            FayteMsg.say("Won't ask again. You can turn the question back on in Options \u2192 Help & feedback.");
        }
        ui.destroy(this);
        if (ok) {
            yes.run();
        }
    }

    @Override
    public void destroy() {
        if (current == this) {
            current = null;
        }
        super.destroy();
    }

    @Override
    protected boolean compactstyle() {
        return true;
    }
}
