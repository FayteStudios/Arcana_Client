package haven;

import java.util.Map;

public class FayteModContainers extends FayteModule {
    public FayteModContainers() {
        super(
                "containers",
                "Containers",
                "Ctrl+scroll moves items one after another, Alt+click moves all of a kind with the same purity, and"
                        + " dropping onto a worn slot swaps, putting the old item in your belt when it fits.",
                FayteConfig.moduleContainers);
    }

    @Override
    public void tick(GameUI gui) {
        boolean diag = FayteConfig.diag();
        if (diag) {
            FayteWinWatch.tick(gui);
        }
        FaytePlacer.tick(gui);
        if (diag) {
            FayteWinWatch.after(gui);
        }
    }

    @Override
    public void commands(Map<String, Console.Command> cmds) {
        cmds.put("invpack", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteXfer.invpack(gui());
            }
        });
    }
}
