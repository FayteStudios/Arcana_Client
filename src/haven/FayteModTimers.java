package haven;

import java.util.List;
import java.util.Map;

public class FayteModTimers extends FayteModule {
    public FayteModTimers() {
        super(
                "timers",
                "Timers & Alerts",
                "Server-clock timers you can pop out onto the screen, and alerts when chosen effects run out.",
                FayteConfig.moduleTimers);
    }

    @Override
    public void commands(Map<String, Console.Command> cmds) {
        cmds.put("ftimers", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteTimersWnd.toggle(gui());
            }
        });
    }

    @Override
    public void xtended(List<String> cmds) {
        cmds.add("ftimers");
    }

    @Override
    public void tick(GameUI gui) {
        FayteTimers.tick(gui);
        FayteHighlights.tick(gui);
    }
}
