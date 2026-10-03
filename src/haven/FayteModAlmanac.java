package haven;

import java.util.List;
import java.util.Map;

public class FayteModAlmanac extends FayteModule {
    private boolean started = false;

    public FayteModAlmanac() {
        super(
                "almanac",
                "Almanac & Inspect",
                "The wiki snapshot, your entries, the Almanac journal and middle-click Inspect.",
                FayteConfig.moduleAlmanac);
    }

    @Override
    public void commands(Map<String, Console.Command> cmds) {
        cmds.put("almanac", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteAlmanacWnd.toggle(gui());
            }
        });
        cmds.put("pilgrims", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteAlmanacWnd.togglepilgrims(gui());
            }
        });
        cmds.put("skills", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteAlmanacWnd.toggleskills(gui());
            }
        });
        cmds.put("recipes", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteAlmanacWnd.show(gui(), FayteAlmanac.RECIPES, "");
            }
        });
        cmds.put("faytetemplates", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteTemplatePreview.toggle(gui());
            }
        });
    }

    @Override
    public void xtended(List<String> cmds) {
        cmds.add("almanac");
    }

    @Override
    public void tick(GameUI gui) {
        if (!started) {
            started = true;
            FayteWikiData.startup();
        }
        FayteEntries.check();
        FayteAlmanac.tick(gui);
        FayteGains.tick(gui);
        FayteAchieve.tick(gui);
        FayteAchievePins.sync(gui);
        FayteCraving.learn(gui);
        FayteBuffTimers.tick(gui);
        FayteKinReq.tick(gui);
        FayteShopList.sync(gui);
        FayteStash.tick(gui);
        FayteInspect.tick(gui);
        FayteRecipes.tick(gui);
        FayteSkillGoal.sync(gui);
        FayteProfPins.sync(gui);
        FaytePilgrims.tick(gui);
        FaytePhoto.sweep(gui);
    }

    @Override
    public void destroy(GameUI gui) {
        FayteAlmanac.flush();
    }
}
