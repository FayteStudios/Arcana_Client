package haven;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FayteModules {
    public static final FayteModUI STYLE = new FayteModUI();
    public static final FayteModKeys KEYS = new FayteModKeys();
    public static final FayteModWorldMap WORLDMAP = new FayteModWorldMap();
    public static final FayteModChat CHAT = new FayteModChat();
    public static final FayteModAlmanac ALMANAC = new FayteModAlmanac();
    public static final FayteModContainers CONTAINERS = new FayteModContainers();
    public static final FayteModView VIEW = new FayteModView();
    public static final FayteModTools TOOLS = new FayteModTools();
    public static final FayteModTimers TIMERS = new FayteModTimers();
    public static final FayteModSelections SELECTIONS = new FayteModSelections();
    private static final List<FayteModule> all = Collections.unmodifiableList(Arrays.<FayteModule>asList(
            STYLE, KEYS, CONTAINERS, VIEW, TOOLS, TIMERS, SELECTIONS, WORLDMAP, CHAT, ALMANAC));

    public static List<FayteModule> all() {
        return all;
    }

    private static List<FayteModule> active() {
        List<FayteModule> ret = new ArrayList<>();

        for (FayteModule m : all) {
            if (m.on()) {
                ret.add(m);
            }
        }
        return ret;
    }

    public static boolean classicchat() {
        return !CHAT.on() || (FayteConfig.classicChat.get() && !FayteLayout.custom());
    }

    public static void commands(Map<String, Console.Command> cmds) {
        for (FayteModule m : active()) {
            m.commands(cmds);
        }
    }

    public static List<String> xtended() {
        List<String> ret = new ArrayList<>();

        for (FayteModule m : active()) {
            m.xtended(ret);
        }
        return ret;
    }

    private static final Set<String> tickfailed = new HashSet<>();

    public static void tick(GameUI gui) {
        try {
            FayteSession.tick(gui);
            FayteLayout.tick(gui);
            FayteFeedbackWnd.tick(gui);
        } catch (Loading e) {
        } catch (RuntimeException e) {
            FayteLog.once("FayteLayout.tick", e);
        }
        for (FayteModule m : active()) {
            try {
                m.tick(gui);
            } catch (Loading e) {
            } catch (RuntimeException e) {
                if (tickfailed.add(m.name + "|" + e)) {
                    FayteLog.log(m.name + ": tick failed (logged once)", e);
                }
            }
        }
    }

    public static void draw(GameUI gui, GOut g) {
        for (FayteModule m : active()) {
            m.draw(gui, g);
        }
    }

    public static void destroy(GameUI gui) {
        for (FayteModule m : active()) {
            try {
                m.destroy(gui);
            } catch (RuntimeException e) {
                FayteLog.log(m.name + ": close failed", e);
            }
        }
    }

    public static boolean keydown(UI ui, KeyEvent ev) {
        for (FayteModule m : active()) {
            if (m.keydown(ui, ev)) {
                return true;
            }
        }
        return false;
    }

    public static boolean type(UI ui, KeyEvent ev) {
        for (FayteModule m : active()) {
            if (m.type(ui, ev)) {
                return true;
            }
        }
        return false;
    }

    public static boolean keyup(UI ui, KeyEvent ev) {
        for (FayteModule m : active()) {
            if (m.keyup(ui, ev)) {
                return true;
            }
        }
        return false;
    }
}
