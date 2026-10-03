package haven;

import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FayteModUI extends FayteModule {
    public FayteModUI() {
        super(
                "fayte_ui",
                "Arcana UI",
                "The Arcana style, moving HUD pieces, Lock UI and window snapping.",
                FayteConfig.moduleStyle);
    }

    @Override
    public void commands(Map<String, Console.Command> cmds) {
        cmds.put("lockui", new Console.Command() {
            public void run(Console cons, String[] args) {
                boolean on = !FayteConfig.lockUi.get();
                FayteConfig.lockUi.set(on);
                FayteMsg.say(
                        on
                                ? "UI locked: windows and HUD pieces stay put"
                                : "UI unlocked: drag windows, or HUD pieces by their bars (right-click a bar to"
                                        + " reset). Lock again when done.");
            }
        });
        cmds.put("options", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteOptWnd.toggle(gui());
            }
        });
        cmds.put("actions", new Console.Command() {
            public void run(Console cons, String[] args) {
                FayteActionsWnd.toggle(gui());
            }
        });
    }

    private boolean swallow = false;

    @Override
    public boolean keydown(UI ui, KeyEvent ev) {
        if (FayteBars.keydown(ui, ev)) {
            swallow = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean type(UI ui, KeyEvent ev) {
        return swallow;
    }

    @Override
    public boolean keyup(UI ui, KeyEvent ev) {
        swallow = false;
        return false;
    }

    @Override
    public void xtended(List<String> cmds) {
        cmds.add("lockui");
    }

    private GameUI profilesfor = null;

    private static final Set<String> failed = new HashSet<>();

    private static void step(String name, Runnable r) {
        try {
            r.run();
        } catch (Loading e) {
        } catch (RuntimeException e) {
            if (failed.add(name + "|" + e)) {
                FayteLog.log("Fayte UI: " + name + " failed (logged once)", e);
            }
        }
    }

    @Override
    public void tick(GameUI gui) {
        step("layout", () -> {
            if (!FayteHud.dragging()) {
                FayteHud.applyall(gui);
            }
        });
        step("brightness", () -> FayteBright.tick(gui));
        if (profilesfor != gui) {
            profilesfor = gui;
            step("profiles", () -> FayteProfiles.onlogin(gui));
            step("trade login", FayteTrade::login);
        }
        step("autosave", FayteProfiles::autosave);
        step("map seen", () -> {
            FayteMapSeen.here(gui);
            FayteMapSeen.save(false);
        });
        step("trade", () -> FayteTrade.tick(gui));
        step("scale", () -> FayteScaleConfirm.follow(gui));
        step("diagnostics", () -> FayteDump.tick(gui));
        step("progress", () -> FayteProgress.tick(gui));
        step("stations", () -> FayteStations.tick(gui));
        step("auto timers", () -> FayteAuto.tick(gui));
        step("bars", () -> FayteBars.sync(gui));
        step("macros", () -> FayteMacros.tick(gui));
        step("actions", () -> FayteActs.tick(gui));
        step("season", () -> FayteSeason.tick(gui));
        step("landing", () -> FayteLanding.tick(gui));
        step("death", () -> FayteDeath.tick(gui));
        step("food slots", () -> {
            if (gui.foodslots != null) {
                gui.foodslots.update(gui.tm);
            }
        });
    }

    @Override
    public void draw(GameUI gui, GOut g) {
        FayteHud.draw(gui, g);
    }
}
