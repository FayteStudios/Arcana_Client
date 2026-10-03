package haven;

import java.util.List;
import java.util.Map;

public class FayteModTools extends FayteModule {
    public FayteModTools() {
        super(
                "tools",
                "Tools & Automation",
                "Fill containers, Ctrl+use to keep feeding, automatic tool swap, doors and gates, Smart interact.",
                FayteConfig.moduleTools);
    }

    @Override
    public void commands(Map<String, Console.Command> cmds) {
        cmds.put("tools", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteToolsWnd.toggle(gui());
            }
        });
        cmds.put("cancel", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteTools.cancel(gui());
            }
        });
        cmds.put("smart", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteTools.smart(gui());
            }
        });
    }

    @Override
    public void xtended(List<String> cmds) {
        cmds.add("tools");
    }

    @Override
    public void tick(GameUI gui) {
        FayteTools.tick(gui);
    }
}
