package haven;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;

public abstract class FayteModule {
    public final String id;
    public final String name;
    public final String desc;
    public final FayteConfig.BoolSetting setting;
    private Boolean active = null;

    protected FayteModule(String id, String name, String desc, FayteConfig.BoolSetting setting) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.setting = setting;
    }

    public boolean required() {
        return this == FayteModules.STYLE || this == FayteModules.ALMANAC;
    }

    public boolean on() {
        if (required()) {
            return true;
        }
        if (active == null) {
            active = setting.get();
        }
        return active;
    }

    public boolean pending() {
        return !required() && setting.get() != on();
    }

    public void commands(Map<String, Console.Command> cmds) {}

    public void xtended(List<String> cmds) {}

    public void tick(GameUI gui) {}

    public void draw(GameUI gui, GOut g) {}

    public void destroy(GameUI gui) {}

    public boolean keydown(UI ui, KeyEvent ev) {
        return false;
    }

    public boolean type(UI ui, KeyEvent ev) {
        return false;
    }

    public boolean keyup(UI ui, KeyEvent ev) {
        return false;
    }

    protected static GameUI gui() {
        return UI.instance == null ? null : UI.instance.gui;
    }
}
