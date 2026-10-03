package haven;

import java.util.ArrayList;
import java.util.List;

public class FayteSkills {
    public static CharWnd cw(GameUI gui) {
        return gui == null ? null : gui.chrwdg;
    }

    public static String name(CharWnd.Skill sk) {
        try {
            Resource.AButton a = sk.res.get().layer(Resource.action);
            return a != null ? a.name : sk.nm;
        } catch (Loading e) {
            return null;
        }
    }

    public static String text(CharWnd.Skill sk) {
        try {
            Resource.Pagina p = sk.res.get().layer(Resource.pagina);
            return p == null ? null : plain(p.text);
        } catch (Loading e) {
            return null;
        }
    }

    public static String plain(String s) {
        if (s == null) {
            return null;
        }
        return s.replaceAll("\\$[a-zA-Z]+\\[[^\\]]*\\]\\{", "")
                .replaceAll("\\$[a-zA-Z]+\\[[^\\]]*\\]", "")
                .replaceAll("\\$[a-zA-Z]+\\{", "")
                .replace("}", "")
                .trim();
    }

    public static List<CharWnd.Skill> available(GameUI gui) {
        CharWnd c = cw(gui);
        List<CharWnd.Skill> l = new ArrayList<>();
        if (c != null) {
            for (CharWnd.Skill s : c.nsk.skills) {
                l.add(s);
            }
        }
        return l;
    }

    public static List<CharWnd.Skill> learned(GameUI gui) {
        CharWnd c = cw(gui);
        List<CharWnd.Skill> l = new ArrayList<>();
        if (c != null) {
            for (CharWnd.Skill s : c.csk.skills) {
                l.add(s);
            }
        }
        return l;
    }

    public static CharWnd.Skill find(GameUI gui, String nm, boolean learned) {
        if (nm == null) {
            return null;
        }
        for (CharWnd.Skill s : learned ? learned(gui) : available(gui)) {
            if (s.nm.equals(nm)) {
                return s;
            }
        }
        return null;
    }

    public static CharWnd.Attr attr(GameUI gui, String nm) {
        CharWnd c = cw(gui);
        return c == null ? null : c.attrs.get(nm);
    }

    public static int inspiration(GameUI gui) {
        CharWnd c = cw(gui);
        return c == null ? 0 : c.tmexp;
    }

    public static void buy(GameUI gui, CharWnd.Skill sk) {
        CharWnd c = cw(gui);
        if (c != null && sk != null) {
            c.wdgmsg("buy", sk.nm);
        }
    }

    public static void reset(GameUI gui) {
        CharWnd c = cw(gui);
        if (c != null) {
            c.wdgmsg("lreset");
        }
    }

    private static String goalkey(GameUI gui) {
        return "fayte_skill_goal_" + (gui.chrid == null ? "" : gui.chrid);
    }

    public static String goal(GameUI gui) {
        String g = Utils.getpref(goalkey(gui), "");
        return g.isEmpty() ? null : g;
    }

    public static void setgoal(GameUI gui, String nm) {
        Utils.setpref(goalkey(gui), nm == null ? "" : nm);
        FayteSkillGoal.reset();
    }
}
