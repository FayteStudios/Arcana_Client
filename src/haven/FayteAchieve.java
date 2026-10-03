package haven;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

public class FayteAchieve {
    public static final String[] CATS = {"All", "Proficiencies", "Humours", "Skills", "Activities", "Discovery"};
    private static final String[] HUMOURS = {"blood", "phlegm", "ybile", "bbile"};
    private static final String[] HUMOURNAMES = {"Blood", "Phlegm", "Yellow Bile", "Black Bile"};
    private static final int[] HTIERS = {25, 50, 75, 100, 125, 150, 175, 200};
    private static final Pattern BUG = Pattern.compile(
            "(?i).*\\b(cricket|grasshopper|locust|beetle|ladybug|ladybird|bug|moth|butterfly|dragonfly|firefly|fly|worm|earthworm|grub|caterpillar|cicada|ant|bee|larva|mosquito|cockroach|centipede|snail|slug)s?\\b.*");
    private static List<Line> lines = null;
    private static GameUI gui = null;
    private static long since = 0L;
    private static long last = 0L;
    private static String act = null;
    private static long actat = 0L;
    private static long actseen = 0L;
    private static boolean actprog = false;
    private static int peak = 0;
    private static int lastp = -1;

    public static class Line {
        public final String id;
        public final String cat;
        public final String name;
        public final String icon;
        public final int[] tiers;
        private final String goalf;
        private final String donef;
        private final ToIntFunction<GameUI> val;
        public int value = 0;

        Line(
                String id,
                String cat,
                String name,
                String icon,
                int[] tiers,
                String goalf,
                String donef,
                ToIntFunction<GameUI> val) {
            this.id = id;
            this.cat = cat;
            this.name = name;
            this.icon = icon;
            this.tiers = tiers;
            this.goalf = goalf;
            this.donef = donef;
            this.val = val;
        }

        public String tierid(int i) {
            return id + ":" + tiers[i];
        }

        public String goal(int i) {
            return goalf.replace("#", Integer.toString(tiers[i]));
        }

        public String done(int i) {
            return donef.replace("#", Integer.toString(tiers[i]));
        }

        public int earned() {
            int n = 0;
            for (int i = 0; i < tiers.length; i++) {
                if (FayteAlmanac.achieved(tierid(i)) != null) {
                    n++;
                }
            }
            return n;
        }

        public int next() {
            for (int i = 0; i < tiers.length; i++) {
                if (FayteAlmanac.achieved(tierid(i)) == null) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static int[] steps(int from, int step, int to) {
        int[] r = new int[(to - from) / step + 1];
        for (int i = 0; i < r.length; i++) {
            r[i] = from + i * step;
        }
        return r;
    }

    private static int attr(GameUI g, String key, boolean comp) {
        Map<String, Glob.CAttr> ca = g.ui.sess.glob.cattr;
        synchronized (ca) {
            Glob.CAttr a = ca.get(key);
            return a == null ? 0 : (comp ? a.getComp() : a.getBase());
        }
    }

    public static synchronized List<Line> lines() {
        if (lines == null) {
            List<Line> l = new ArrayList<>();
            for (Map.Entry<String, String> p : CharWnd.attrnm.entrySet()) {
                final String k = p.getKey();
                String n = p.getValue();
                l.add(new Line(
                        "prof:" + k,
                        "Proficiencies",
                        n,
                        "gfx/hud/skills/" + k,
                        steps(10, 10, 100),
                        "Reach level # in " + n,
                        "Reached level # in " + n,
                        (g) -> attr(g, k, false)));
            }
            for (int i = 0; i < HUMOURS.length; i++) {
                final String k = HUMOURS[i];
                String n = HUMOURNAMES[i];
                l.add(new Line(
                        "hum:" + k,
                        "Humours",
                        n,
                        null,
                        HTIERS,
                        "Raise " + n + " to #",
                        "Raised " + n + " to #",
                        (g) -> attr(g, k, true) / 1000));
            }
            l.add(new Line(
                    "hum:all",
                    "Humours",
                    "Balanced Temper",
                    null,
                    HTIERS,
                    "Have all four humours at # or more",
                    "Had all four humours at # or more",
                    (g) -> {
                        int m = Integer.MAX_VALUE;
                        for (String h : HUMOURS) {
                            m = Math.min(m, attr(g, h, true) / 1000);
                        }
                        return m;
                    }));
            l.add(new Line(
                    "skills",
                    "Skills",
                    "Learned",
                    null,
                    new int[] {1, 5, 10, 25, 50, 75, 100},
                    "Learn # skills",
                    "Learned # skills",
                    (g) -> FayteAlmanac.size(FayteAlmanac.SKILLS)));
            l.add(new Line(
                    "eat",
                    "Activities",
                    "Well Fed",
                    null,
                    new int[] {10, 50, 100, 500, 1000},
                    "Eat # things",
                    "Ate # things",
                    (g) -> FayteAlmanac.tallied("eat")));
            l.add(new Line(
                    "eat:bug",
                    "Activities",
                    "Bug Eater",
                    null,
                    new int[] {10, 50, 100},
                    "Eat # bugs",
                    "Ate # bugs",
                    (g) -> FayteAlmanac.tallied("eat:bug")));
            l.add(new Line(
                    "menu:branch",
                    "Activities",
                    "Branch Picker",
                    null,
                    new int[] {20, 100, 500},
                    "Take # branches",
                    "Took # branches",
                    (g) -> FayteAlmanac.tallysum("menu:", "branch")));
            l.add(new Line(
                    "menu:pick",
                    "Activities",
                    "Forager",
                    null,
                    new int[] {20, 100, 500, 1000},
                    "Forage # things from the wild",
                    "Foraged # things from the wild",
                    (g) -> FayteAlmanac.tallysum("forage:", "")));
            l.add(new Line(
                    "menu:chop",
                    "Activities",
                    "Woodcutter",
                    null,
                    new int[] {10, 50, 200},
                    "Chop # times",
                    "Chopped # times",
                    (g) -> FayteAlmanac.tallysum("menu:", "chop")));
            l.add(new Line(
                    "crafted",
                    "Activities",
                    "Maker",
                    null,
                    new int[] {1, 10, 100, 500, 1000},
                    "Craft # things",
                    "Crafted # things",
                    (g) -> FayteAlmanac.counted(FayteAlmanac.RECIPES, "Crafted")));
            l.add(new Line(
                    "found:items",
                    "Discovery",
                    "Collector",
                    null,
                    new int[] {25, 100, 250, 500},
                    "Find # different items",
                    "Found # different items",
                    (g) -> FayteAlmanac.size(FayteAlmanac.ITEMS)));
            l.add(new Line(
                    "found:creatures",
                    "Discovery",
                    "Naturalist",
                    null,
                    new int[] {5, 15, 30},
                    "Meet # kinds of creature",
                    "Met # kinds of creature",
                    (g) -> FayteAlmanac.size(FayteAlmanac.CREATURES)));
            l.add(new Line(
                    "found:biomes",
                    "Discovery",
                    "Wanderer",
                    null,
                    new int[] {3, 6, 10, 15},
                    "Walk through # biomes",
                    "Walked through # biomes",
                    (g) -> FayteAlmanac.size(FayteAlmanac.BIOMES)));
            l.add(new Line(
                    "found:recipes",
                    "Discovery",
                    "Recipe Keeper",
                    null,
                    new int[] {25, 100, 250},
                    "Know # recipes",
                    "Knew # recipes",
                    (g) -> FayteAlmanac.size(FayteAlmanac.RECIPES)));
            l.add(new Line(
                    "journal",
                    "Discovery",
                    "Chronicler",
                    null,
                    new int[] {10, 50, 100, 500},
                    "Have # journal entries",
                    "Had # journal entries",
                    (g) -> FayteAlmanac.size(FayteAlmanac.JOURNAL)));
            lines = l;
        }
        return lines;
    }

    public static int total() {
        int n = 0;
        for (Line l : lines()) {
            n += l.tiers.length;
        }
        return n;
    }

    public static int earned() {
        int n = 0;
        for (Line l : lines()) {
            n += l.earned();
        }
        return n;
    }

    public static void chose(String option, String eaten) {
        if (option == null || !FayteAlmanac.ready()) {
            return;
        }
        if (eaten != null) {
            FayteAlmanac.tally("menu:" + option.toLowerCase(), 1);
            FayteAlmanac.tally("eat", 1);
            if (BUG.matcher(eaten).matches()) {
                FayteAlmanac.tally("eat:bug", 1);
            }
        } else {
            String[] tg = gui == null ? null : FayteTools.menutarget(gui);
            String rn = tg == null ? "" : tg[0];
            if (rn.startsWith("gfx/terobjs/") && (rn.contains("/herbs/") || rn.contains("flower"))) {
                startact("forage:" + option.toLowerCase());
            } else {
                startact("menu:" + option.toLowerCase());
            }
        }
    }

    public static void startact(String key) {
        long now = System.currentTimeMillis();
        act = key;
        actat = now;
        actseen = now;
        actprog = false;
        peak = 0;
    }

    private static void actdone() {
        if (act == null || !FayteAlmanac.ready()) {
            return;
        }
        if (act.startsWith("craft:")) {
            FayteAlmanac.crafted(act.substring(6));
        } else {
            FayteAlmanac.tally(act, 1);
        }
    }

    private static void progtick(GameUI g) {
        long now = System.currentTimeMillis();
        int p = g.prog;
        if (act != null) {
            if (p >= 0) {
                actprog = true;
                actseen = now;
                if (lastp >= 0 && p < lastp - 40) {
                    if (peak >= 75) {
                        actdone();
                    }
                    peak = 0;
                }
                peak = Math.max(peak, p);
            } else if (lastp >= 0) {
                if (peak >= 75) {
                    actdone();
                }
                peak = 0;
            }
            if (!actprog && now - actat > 3000L) {
                actdone();
                act = null;
            } else if (actprog && p < 0 && now - actseen > 4000L) {
                act = null;
            }
        }
        lastp = p;
    }

    public static void tick(GameUI g) {
        long now = System.currentTimeMillis();
        if (g != gui) {
            gui = g;
            since = now;
            act = null;
        }
        progtick(g);

        if (now - last < 1000L || !FayteAlmanac.ready() || g.ui == null || g.ui.sess == null) {
            return;
        }
        last = now;
        boolean award = now - since > 15000L;
        boolean init = FayteAlmanac.achinit();
        int caught = 0;
        for (Line l : lines()) {
            try {
                l.value = l.val.applyAsInt(g);
            } catch (RuntimeException e) {
                continue;
            }
            if (!award) {
                continue;
            }
            for (int i = 0; i < l.tiers.length; i++) {
                if (l.value >= l.tiers[i] && FayteAlmanac.achieved(l.tierid(i)) == null) {
                    FayteAlmanac.achieve(l.tierid(i));
                    if (init) {
                        post(l, i);
                    } else {
                        caught++;
                    }
                }
            }
        }
        if (award && !init) {
            FayteAlmanac.setachinit();
            if (caught > 0) {
                List<String[]> rows = new ArrayList<>();
                rows.add(new String[] {"Earned before now", Integer.toString(caught)});
                rows.add(new String[] {"See them", "My Journey, Achievements tab"});
                FayteAlmanac.journal("Achievements caught up", rows);
            }
        }
    }

    private static void post(Line l, int i) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Achievement", l.name + " (" + l.cat + ")"});
        rows.add(new String[] {"Tier", (i + 1) + " of " + l.tiers.length});
        if (i + 1 < l.tiers.length) {
            rows.add(new String[] {"Next", l.goal(i + 1)});
        }
        FayteAlmanac.journal("Achievement: " + l.done(i), rows);
        FayteMsg.say("Achievement: " + l.done(i));
        FayteAchieveToast.show(gui, l.icon, l.name, l.done(i));
    }
}
