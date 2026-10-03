package haven;

import java.util.List;
import java.util.Map;

public class FayteModWorldMap extends FayteModule {
    public FayteModWorldMap() {
        super(
                "worldmap",
                "World Map",
                "The true map by grid ID, markers and the pointer arrow. Stops the game's session map folders.",
                FayteConfig.worldmapEnabled);
    }

    @Override
    public void commands(Map<String, Console.Command> cmds) {
        cmds.put("worldmap", new Console.Command() {
            public void run(Console cons, String[] args) {
                WorldMapWnd.toggle(gui());
            }
        });
    }

    @Override
    public void xtended(List<String> cmds) {
        cmds.add("worldmap");
    }

    @Override
    public void tick(GameUI gui) {
        WorldMapMarkers.tick(gui);
    }
}
