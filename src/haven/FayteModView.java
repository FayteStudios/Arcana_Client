package haven;

import java.util.List;
import java.util.Map;

public class FayteModView extends FayteModule {
    public FayteModView() {
        super(
                "view",
                "View & Performance",
                "Terrain and object distance, grass, hiding object groups, footprint outlines and frame-rate caps.",
                FayteConfig.moduleView);
    }

    @Override
    public void commands(Map<String, Console.Command> cmds) {
        cmds.put("view", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteViewWnd.toggle(gui());
            }
        });
    }

    @Override
    public void xtended(List<String> cmds) {
        cmds.add("view");
    }
}
