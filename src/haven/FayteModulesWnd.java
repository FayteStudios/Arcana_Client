package haven;

import java.util.ArrayList;
import java.util.List;

public class FayteModulesWnd extends Window {
    private static final int W = 420;
    private static final int ROW = FayteSkin.s(44);
    private static FayteModulesWnd instance = null;
    private final Label status;

    private FayteModulesWnd(Widget parent) {
        super(
                new Coord(FayteSkin.s(220), FayteSkin.s(120)),
                new Coord(W, 60 + FayteModules.all().size() * ROW),
                parent,
                "Arcana Modules");
        justclose = true;
        int y = FayteSkin.s(5);

        for (final FayteModule m : FayteModules.all()) {
            if (m.required()) {
                continue;
            }
            CheckBox cb = new CheckBox(new Coord(FayteSkin.s(5), y), this, m.name) {
                @Override
                public void changed(boolean val) {
                    super.changed(val);
                    m.setting.set(val);
                    FayteModulesWnd.this.update();
                }
            };
            cb.a = m.setting.get();
            new Label(new Coord(FayteSkin.s(25), y + FayteSkin.s(20)), this, m.desc, W - 30);
            y += ROW;
        }
        status = new Label(new Coord(FayteSkin.s(5), y + FayteSkin.s(8)), this, "", W - 10);
        addtwdg(new FayteTitleButton(
                this, "Text size", "Make Arcana text bigger or smaller", () -> FayteTextSize.Wnd.toggle(ui.gui)));
        addtwdg(new FayteTitleButton(
                this,
                "Movable while locked",
                "Choose which windows can still be moved while the UI is locked",
                () -> FayteLock.Wnd.toggle(ui.gui)));
        update();
    }

    public static void toggle(GameUI gui) {
        if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
        } else if (gui != null) {
            instance = new FayteModulesWnd(gui);
        }
    }

    private void update() {
        List<String> p = new ArrayList<>();

        for (FayteModule m : FayteModules.all()) {
            if (m.pending()) {
                p.add(m.name);
            }
        }
        status.settext(
                p.isEmpty()
                        ? "Changes apply after you restart the client."
                        : "Restart the client to apply: " + String.join(", ", p));
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
