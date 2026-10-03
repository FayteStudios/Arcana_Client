package haven;

import java.util.List;
import java.util.Map;

public class FayteModSelections extends FayteModule {
    public FayteModSelections() {
        super(
                "selections",
                "Selections",
                "Paint sets of tiles on the ground, keep a list of them, and show several at once.",
                FayteConfig.moduleSelections);
    }

    @Override
    public void commands(Map<String, Console.Command> cmds) {
        cmds.put("selections", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteSelectionsWnd.toggle(gui());
            }
        });
    }

    @Override
    public void xtended(List<String> cmds) {
        cmds.add("selections");
    }

    @Override
    public void tick(GameUI gui) {
        FayteSelections.tick(gui);
        FayteSelList.sync(gui);
    }
}
