package haven;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;

public class FayteModKeys extends FayteModule {
    public FayteModKeys() {
        super(
                "keybinds",
                "Key Bindings",
                "Rebind the game's keys and give keys to Arcana windows and action-grid entries.",
                FayteConfig.moduleKeys);
    }

    @Override
    public void commands(Map<String, Console.Command> cmds) {
        cmds.put("keybinds", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteKeysWnd.toggle(gui());
            }
        });
    }

    @Override
    public void xtended(List<String> cmds) {
        cmds.add("keybinds");
    }

    @Override
    public boolean keydown(UI ui, KeyEvent ev) {
        return FayteKeys.keydown(ui, ev);
    }

    @Override
    public boolean type(UI ui, KeyEvent ev) {
        return FayteKeys.type(ui, ev);
    }

    @Override
    public boolean keyup(UI ui, KeyEvent ev) {
        return FayteKeys.keyup(ui, ev);
    }
}
