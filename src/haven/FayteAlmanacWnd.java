package haven;

import java.awt.Color;
import java.awt.Desktop;
import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FayteAlmanacWnd extends Window {
    public static final String TITLE = "Almanac";
    public static final Coord SZ = new Coord(820, 540);
    public static final Coord MINSZ = new Coord(480, 340);
    private int LISTW = prefint("fayte_alm_split_world", 240);
    private final FayteSplit split;
    private final FayteAlmanacWnd.Grip grip;
    private static int ROW2 = FayteTitleButton.H + 8;
    private static int TOP = ROW2 * 2 - 2;
    private static final Map<String, String> ICONS = new HashMap<>();

    static {
        ICONS.put("World", "paginae/skills/coltrade");
        ICONS.put("Recipes", "paginae/add/craft");
        ICONS.put("Build", "paginae/skills/carpentry");
        ICONS.put("Skills & Proficiencies", "gfx/hud/chrup");
        ICONS.put("Pilgrims", "paginae/craft/pilgrimshat");
        ICONS.put("My Journey", "paginae/act/tracking");
        ICONS.put("History", "gfx/invobjs/book");
        ICONS.put(FayteAlmanac.ITEMS, "paginae/bld/containers");
        ICONS.put(FayteAlmanac.CREATURES, "paginae/skills/biggame");
        ICONS.put(FayteAlmanac.BIOMES, "paginae/skills/experiencedtraveler");
        ICONS.put("Achievements", "paginae/craft/huntingtrophy");
        ICONS.put("Notes", "paginae/act/adv");
    }

    public static final String WORLD = "World";
    public static final String RECIPESEC = "Recipes";
    public static final String BUILDSEC = "Build";
    public static final String SKILLSEC = "Skills & Proficiencies";
    public static final String PILGRIMS = "Pilgrims";
    public static final String JOURNEY = "My Journey";
    public static final String SAVED = "History";
    private static final String[] SECTIONS = {WORLD, RECIPESEC, BUILDSEC, SKILLSEC, PILGRIMS, JOURNEY, SAVED};
    private static final String[] SAVEDCATS = {FayteAlmanac.HISTORY, FayteAlmanac.FAVORITES};
    private static final String[] SKINDS = {
        "All",
        FayteAlmanac.ITEMS,
        FayteAlmanac.CREATURES,
        FayteAlmanac.BIOMES,
        FayteAlmanac.RECIPES,
        FayteAlmanac.SKILLS,
        FayteAlmanac.PROFICIENCIES,
        FayteAlmanac.JOURNAL
    };
    private int sfilter = 0;
    private final FayteTitleButton sfilterbtn;
    private final FayteTitleButton favbtn;
    private static long anyclick = 0L;
    private static long almclick = -1L;

    public static void clicked() {
        anyclick = System.currentTimeMillis();
    }

    public static boolean enter() {
        if (!isopen() || almclick != anyclick) {
            return false;
        }
        return instance.armsearch();
    }

    private boolean armsearch() {
        if (lists()) {
            search.arm();
            return true;
        } else if (RECIPESEC.equals(section)) {
            return recipes.armsearch();
        } else if (BUILDSEC.equals(section)) {
            return builds.armsearch();
        } else if (SKILLSEC.equals(section)) {
            return skills.armsearch();
        } else if (PILGRIMS.equals(section)) {
            return pilgrims.armsearch();
        }
        return false;
    }

    private static boolean saved(String cat) {
        return FayteAlmanac.HISTORY.equals(cat) || FayteAlmanac.FAVORITES.equals(cat);
    }

    private FayteAlmanac.Rec favtarget() {
        if (lists()) {
            return cur;
        } else if (RECIPESEC.equals(section) || BUILDSEC.equals(section)) {
            String n = (RECIPESEC.equals(section) ? recipes : builds).curname();
            return n == null ? null : FayteAlmanac.get(FayteAlmanac.RECIPES, n);
        }
        return null;
    }

    private void togglefav() {
        FayteAlmanac.Rec r = favtarget();
        if (r != null) {
            boolean on = FayteAlmanac.togglefav(r);
            FayteMsg.say(
                    on
                            ? "Added " + r.name + " to History \u2192 Favorites."
                            : "Removed " + r.name + " from Favorites.");
            updbuttons();
            if (saved(cat)) {
                refilter();
            }
        }
    }

    private void picksfilter() {
        new FaytePopup(sfilterbtn.c.add(0, FayteTitleButton.H + 2), this, SKINDS, sfilter, (i) -> {
            sfilter = i;
            sfilterbtn.setlabel("Show: " + SKINDS[i]);
            cur = null;
            curnone = null;
            refilter();
        });
    }

    private static final String[] FILTERS = {"All", "New", "Craftable", "Highlighted"};
    private final Map<String, FayteTitleButton> secbtns = new HashMap<>();
    private final FayteTitleButton filterbtn;
    private final FayteRecipesPanel recipes;
    private final FayteRecipesPanel builds;
    private final FayteSkillsPanel skills;
    private final FaytePilgrimsPanel pilgrims;
    private final FayteNotesPanel notes;
    private final FayteTitleButton notesbtn;
    private boolean notesmode = false;
    private final FayteAchievePanel achs;
    private final FayteTitleButton achbtn;
    private boolean achmode = false;
    private final FayteTitleButton notebtn;
    private final FayteTitleButton shopbtn;
    private final FayteTitleButton renamebtn;
    private final FayteTitleButton jfilterbtn;
    private int jfilter = 0;
    private String section = WORLD;
    private int filter = 0;
    private static FayteAlmanacWnd instance;

    public static boolean recipeslive() {
        return isopen() && (RECIPESEC.equals(instance.section) || BUILDSEC.equals(instance.section));
    }

    public static boolean isopen() {
        return instance != null && instance.attached() && instance.visible;
    }

    private final GameUI gui;
    private final Map<String, FayteTitleButton> tabs = new HashMap<>();
    private final FayteTitleButton craftbtn;
    private final FayteTitleButton wikibtn;
    private final FayteTitleButton backbtn;
    private final FayteTitleButton photobtn;
    private final TextEntry search;
    private final FayteAlmanacWnd.RecList list;
    private final FayteWikiEntry.View view;
    private final Deque<Object> history = new ArrayDeque<>();
    private String cat = FayteAlmanac.ITEMS;
    private FayteAlmanac.Rec cur;
    private String curnone;
    private FayteWikiData.Entry curwiki;
    private Glob.Pagina curpag;
    private long lastrefresh = 0L;
    private int curtotal = -1;

    public FayteAlmanacWnd(GameUI gui) {
        super(new Coord(80, 60), savedsz(gui), gui, TITLE);
        ROW2 = FayteTitleButton.rowh() + 8;
        TOP = ROW2 * 2 - 2;
        this.gui = gui;
        justclose = true;
        addtwdg(new FayteTitleButton(
                        this, "Mark all read", "Clear the new-entry dots from everything in the Almanac", () -> {
                            int n = FayteAlmanac.seenall();
                            FayteMsg.say(
                                    n == 0
                                            ? "Nothing new to clear."
                                            : "Marked " + n + (n == 1 ? " entry" : " entries") + " as read.");
                            refilter();
                        })
                .iconly("paginae/skills/greenthumb"));
        wikibtn = new FayteTitleButton(
                        this, "Wiki page", "Open this entry on the Salem wiki in your browser", this::openwiki)
                .iconly("paginae/add/wiki");
        craftbtn = new FayteTitleButton(this, "Craft", "Open the crafting window for this", this::craft);
        photobtn = new FayteTitleButton(this, "Photo", "Take your own photo for this entry", this::photo)
                .iconly("paginae/craft/memorabilia");
        notebtn = new FayteTitleButton(this, "Note", "Write your own note on this journal entry", this::writenote);
        shopbtn = new FayteTitleButton(
                this,
                "Shopping list",
                "Add this recipe (at the amount in Make) to your shopping list pop-out",
                this::shoplist);
        renamebtn = new FayteTitleButton(this, "Rename", "Give this journal entry your own title", this::renameentry);
        backbtn = new FayteTitleButton(this, "\u2190", "Back to the previous entry", this::back);
        addtwdg(backbtn);
        hlbtns = FayteHighlights.buttons(this, this::markname);

        int sx = 0;
        for (String sec : SECTIONS) {
            final String fs = sec;
            FayteTitleButton b = new FayteTitleButton(this, sec, "Open " + sec, () -> setsection(fs))
                    .icon(ICONS.get(sec), FayteTitleButton.iconmode());
            b.c = new Coord(sx, 0);
            sx += b.sz.x + 6;
            secbtns.put(sec, b);
        }
        for (String c : FayteAlmanac.CATS) {
            if (FayteAlmanac.RECIPES.equals(c)) {
                continue;
            }
            final String fc = c;
            FayteTitleButton b = new FayteTitleButton(this, c, "Show " + c.toLowerCase(), () -> setcat(fc, true))
                    .icon(ICONS.get(c), FayteTitleButton.iconmode());
            tabs.put(c, b);
        }
        for (String c : SAVEDCATS) {
            final String fc = c;
            tabs.put(
                    c,
                    new FayteTitleButton(
                                    this,
                                    c,
                                    FayteAlmanac.HISTORY.equals(c)
                                            ? "Entries you've looked at, newest first"
                                            : "Entries you starred with \u2605 Favorite",
                                    () -> setcat(fc, true))
                            .icon(ICONS.get(c), FayteTitleButton.iconmode()));
        }
        sfilterbtn = new FayteTitleButton(this, "Show: All", "Show only one kind of saved entry", this::picksfilter);
        favbtn = new FayteTitleButton(
                this, "\u2606", "Star this entry so it shows in History \u2192 Favorites", this::togglefav);

        filterbtn = new FayteTitleButton(this, "Show: All", "Filter the item list", this::pickfilter);
        jfilterbtn = new FayteTitleButton(this, "Show: All", "Show only one kind of journal entry", this::pickjfilter);
        search = new TextEntry(new Coord(0, TOP), new Coord(LISTW, 20), this, "") {
            @Override
            protected void changed() {
                FayteAlmanacWnd.this.refilter();
            }
        };
        search.clicktotype = true;
        Coord ws = asz;
        list = new FayteAlmanacWnd.RecList(new Coord(0, TOP + 26), new Coord(LISTW, ws.y - TOP - 26));
        view = new FayteWikiEntry.View(new Coord(LISTW + 8, TOP), new Coord(ws.x - LISTW - 8, ws.y - TOP), this, null);
        view.onlink = this::follow;
        recipes = new FayteRecipesPanel(new Coord(0, ROW2), new Coord(ws.x, ws.y - ROW2), this, gui);
        builds = new FayteRecipesPanel(new Coord(0, ROW2), new Coord(ws.x, ws.y - ROW2), this, gui, true);
        skills = new FayteSkillsPanel(new Coord(0, ROW2), new Coord(ws.x, ws.y - ROW2), this, gui);
        pilgrims = new FaytePilgrimsPanel(new Coord(0, ROW2), new Coord(ws.x, ws.y - ROW2), this, gui);
        notes = new FayteNotesPanel(new Coord(0, TOP), new Coord(ws.x, ws.y - TOP), this);
        notesbtn = new FayteTitleButton(this, "Notes", "Your notepad", () -> {
                    notesmode = true;
                    achmode = false;
                    setsection(JOURNEY);
                })
                .icon(ICONS.get("Notes"), FayteTitleButton.iconmode());
        achs = new FayteAchievePanel(new Coord(0, TOP), new Coord(ws.x, ws.y - TOP), this);
        achbtn = new FayteTitleButton(
                        this,
                        "Achievements",
                        "Everything you've achieved, and how close you are to the next tier",
                        () -> {
                            achmode = true;
                            notesmode = false;
                            setsection(JOURNEY);
                        })
                .icon(ICONS.get("Achievements"), FayteTitleButton.iconmode());
        split = new FayteSplit(this, true, (p) -> {
            LISTW = p - 1;
            layoutall();
            Utils.setpref("fayte_alm_split_world", Integer.toString(LISTW));
        });
        grip = new FayteAlmanacWnd.Grip();
        layoutall();
        setcat(cat, true);
        setsection(WORLD);
        updbuttons();
        restorepage();
    }

    private static String lastsec = null;
    private static String lastcat = null;
    private static String lastrec = null;
    private static boolean lastnotes = false;
    private static boolean lastach = false;

    private void restorepage() {
        if (lastsec == null) {
            return;
        }
        try {
            if (JOURNEY.equals(lastsec) && (lastnotes || lastach)) {
                notesmode = lastnotes;
                achmode = lastach;
                setsection(JOURNEY);
            } else if (lists(lastsec) && lastcat != null) {
                setcat(lastcat, true);
                FayteAlmanac.Rec r = lastrec == null ? null : FayteAlmanac.get(lastcat, lastrec);
                if (r != null) {
                    select(r);
                }
                list.scroll = 0;
            } else {
                setsection(lastsec);
                if (lastpanel != null && RECIPESEC.equals(lastsec)) {
                    recipes.select(lastpanel);
                } else if (lastpanel != null && BUILDSEC.equals(lastsec)) {
                    builds.select(lastpanel);
                }
            }
        } catch (RuntimeException e) {
            FayteLog.log("Almanac: could not reopen the last page", e);
        }
    }

    private static boolean lists(String sec) {
        return WORLD.equals(sec) || JOURNEY.equals(sec);
    }

    private static String lastpanel = null;
    private static boolean closing = false;

    public static void closelater() {
        closing = true;
    }

    private void rememberpage() {
        lastsec = section;
        lastpanel =
                RECIPESEC.equals(section) ? recipes.curname() : (BUILDSEC.equals(section) ? builds.curname() : null);
        lastcat = cat;
        lastrec = cur == null ? null : cur.name;
        lastnotes = notesmode;
        lastach = achmode;
    }

    private static Coord savedsz(GameUI gui) {
        Coord area = gui.sz;
        Coord def = new Coord(
                Math.max(MINSZ.x, Math.min(SZ.x, area.x * 45 / 100)),
                Math.max(MINSZ.y, Math.min(SZ.y, area.y * 60 / 100)));
        String[] p = Utils.getpref("fayte_alm_size2", "").split(",");
        try {
            if (p.length == 2) {
                return new Coord(
                        Math.max(MINSZ.x, Math.min(area.x - 20, Integer.parseInt(p[0]))),
                        Math.max(MINSZ.y, Math.min(area.y - 20, Integer.parseInt(p[1]))));
            }
        } catch (NumberFormatException e) {
        }
        return def;
    }

    private void layoutall() {
        Coord ws = asz;
        if (split == null || grip == null) {
            return;
        }
        LISTW = Math.max(160, Math.min(ws.x - 300, LISTW));
        search.sz = new Coord(LISTW, 20);
        list.c = new Coord(0, TOP + 26);
        list.sz = new Coord(LISTW, ws.y - TOP - 26);
        view.c = new Coord(LISTW + 8, TOP);
        view.resize(new Coord(ws.x - LISTW - 8, ws.y - TOP));
        split.place(LISTW + 1, TOP, ws.y - TOP);
        split.visible = list.visible;
        recipes.resize(new Coord(ws.x, ws.y - ROW2));
        builds.resize(new Coord(ws.x, ws.y - ROW2));
        skills.resize(new Coord(ws.x, ws.y - ROW2));
        pilgrims.resize(new Coord(ws.x, ws.y - ROW2));
        notes.resize(new Coord(ws.x, ws.y - TOP));
        achs.resize(new Coord(ws.x, ws.y - TOP));
        grip.c = ws.sub(grip.sz);
        grip.raise();
        relabel();
        if (hlbtns != null) {
            placeentrybar();
        }
    }

    private class Grip extends Widget {
        private Coord start = null;
        private Coord startsz = null;

        Grip() {
            super(Coord.z, new Coord(12, 12), FayteAlmanacWnd.this);
            tooltip = "Drag to resize the Almanac";
        }

        @Override
        public void draw(GOut g) {
            g.chcolor(FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.6));
            for (int i = 0; i < 3; i++) {
                int o = 3 + i * 4;
                g.line(new Coord(sz.x - 1, o), new Coord(o, sz.y - 1), 1);
            }
            g.chcolor();
        }

        @Override
        public boolean mousedown(Coord c, int button) {
            if (button != 1) {
                return false;
            }
            start = rootpos().add(c);
            startsz = FayteAlmanacWnd.this.asz;
            ui.grabmouse(this);
            return true;
        }

        @Override
        public void mousemove(Coord c) {
            if (start != null) {
                Coord d = rootpos().add(c).sub(start);
                Coord nsz = new Coord(Math.max(MINSZ.x, startsz.x + d.x), Math.max(MINSZ.y, startsz.y + d.y));
                if (!nsz.equals(FayteAlmanacWnd.this.asz)) {
                    FayteAlmanacWnd.this.resize(nsz);
                    FayteAlmanacWnd.this.layoutall();
                }
            }
        }

        @Override
        public boolean mouseup(Coord c, int button) {
            if (start != null && button == 1) {
                start = null;
                ui.grabmouse(null);
                Coord s = FayteAlmanacWnd.this.asz;
                Utils.setpref("fayte_alm_size2", s.x + "," + s.y);
                return true;
            }
            return false;
        }
    }

    public static int prefint(String key, int def) {
        try {
            return Integer.parseInt(Utils.getpref(key, Integer.toString(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static FayteAlmanacWnd get(GameUI gui) {
        if (instance == null || !instance.attached()) {
            instance = new FayteAlmanacWnd(gui);
        }
        instance.raise();
        almclick = anyclick;
        return instance;
    }

    public static void toggle(GameUI gui) {
        if (instance != null && instance.attached() && !instance.visible) {
            instance.show();
            instance.raise();
            almclick = anyclick;
        } else if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
            instance = null;
        } else {
            get(gui);
        }
    }

    public static void show(GameUI gui, String cat, String name) {
        FayteAlmanacWnd w = get(gui);
        if (FayteAlmanac.RECIPES.equals(cat)) {
            w.setsection(RECIPESEC);
            w.recipes.select(name);
            return;
        } else if (FayteAlmanac.SKILLS.equals(cat) || FayteAlmanac.PROFICIENCIES.equals(cat)) {
            w.setsection(SKILLSEC);
            return;
        }
        FayteAlmanac.Rec r = FayteAlmanac.get(cat, name);
        if (r != null) {
            w.remember();
            w.setcat(r.cat, false);
            w.select(r);
        }
    }

    public static void skills(GameUI gui, boolean goal) {
        FayteAlmanacWnd w = get(gui);
        w.setsection(SKILLSEC);
        if (goal) {
            w.skills.selectgoal();
        }
    }

    public static void pilgrims(GameUI gui) {
        get(gui).setsection(PILGRIMS);
    }

    public static void togglepilgrims(GameUI gui) {
        if (instance != null && instance.attached() && PILGRIMS.equals(instance.section)) {
            instance.ui.destroy(instance);
            instance = null;
        } else {
            get(gui).setsection(PILGRIMS);
        }
    }

    public static void toggleskills(GameUI gui) {
        if (instance != null && instance.attached() && SKILLSEC.equals(instance.section)) {
            instance.ui.destroy(instance);
            instance = null;
        } else {
            skills(gui, false);
        }
    }

    public static void stationrecipes(GameUI gui, List<String> st) {
        FayteAlmanacWnd w = get(gui);
        w.setsection(RECIPESEC);
        w.recipes.station(st);
    }

    public static void achievements(GameUI gui) {
        FayteAlmanacWnd w = get(gui);
        w.achmode = true;
        w.notesmode = false;
        w.setsection(JOURNEY);
    }

    private void pickjfilter() {
        new FaytePopup(jfilterbtn.c.add(0, FayteTitleButton.H + 2), this, FayteAlmanac.JKINDS, jfilter, (i) -> {
            jfilter = i;
            jfilterbtn.setlabel("Show: " + FayteAlmanac.JKINDS[i]);
            cur = null;
            curnone = null;
            refilter();
        });
    }

    private void renameentry() {
        if (cur == null || !FayteAlmanac.JOURNAL.equals(cur.cat)) {
            return;
        }
        final FayteAlmanac.Rec r = cur;
        new FayteAsk(
                gui,
                "Rename entry",
                "New title for this entry. Enter to save, leave empty to go back to the original.",
                r.label == null ? FayteAlmanac.jtitle(r) : r.label,
                (t) -> {
                    FayteAlmanac.relabel(r, t.equals(FayteAlmanac.jtitle(r)) ? null : t);
                    list.cache.clear();
                    select(r);
                });
    }

    private void shoplist() {
        (BUILDSEC.equals(section) ? builds : recipes).shoplist();
    }

    private void writenote() {
        if (cur == null) {
            return;
        }
        final FayteAlmanac.Rec r = cur;
        new FayteAsk(
                gui,
                "Note for this entry",
                "Write anything you want to remember. Enter to save, leave empty to remove.",
                r.note == null ? "" : r.note,
                (t) -> {
                    FayteAlmanac.note(r, t);
                    select(r);
                });
    }

    public static void showlink(GameUI gui, String target) {
        get(gui).follow(target);
    }

    private void remember() {
        if (cur != null) {
            history.push(cur);
        } else if (curnone != null) {
            history.push(curnone);
        }
        while (history.size() > 50) {
            history.removeLast();
        }
    }

    private void photo() {
        if (cur == null) {
            return;
        }
        FayteAlmanac.Rec r = cur;
        hide();
        FaytePhoto.start(gui, r.name, () -> {
            show();
            raise();
            select(r);
        });
    }

    private boolean photolink(String target) {
        if (target.startsWith("photo-open:")) {
            try {
                Desktop.getDesktop().open(new File(FaytePaths.images(), target.substring(11)));
            } catch (Exception e) {
                FayteMsg.say("Could not open the photo: " + e.getMessage(), GameUI.MsgType.BAD);
            }
            return true;
        } else if (target.startsWith("photo-del:")) {
            final File f = new File(FaytePaths.images(), target.substring(10));
            new FaytePopup(ui.mc, ui.root, new String[] {"Delete this photo", "Keep it"}, -1, (i) -> {
                if (i == 0) {
                    FayteEntries.forget(f.getName());
                    if (f.delete()) {
                        FayteMsg.say("Photo deleted.");
                    } else {
                        FayteMsg.say("Could not delete " + f, GameUI.MsgType.BAD);
                    }
                    if (cur != null) {
                        select(cur);
                    }
                }
            });
            return true;
        }
        return false;
    }

    private void follow(String target) {
        if (photolink(target)) {
            return;
        }
        remember();
        FayteAlmanac.Rec r = FayteAlmanac.match(target);
        if (r != null && FayteAlmanac.RECIPES.equals(r.cat)) {
            setsection(RECIPESEC);
            recipes.select(r.name);
        } else if (r != null && sectionof(r.cat).equals(SKILLSEC)) {
            setsection(SKILLSEC);
        } else if (r != null) {
            setcat(r.cat, false);
            select(r);
        } else {
            FayteWikiData.Entry e = FayteWikiData.find(target);
            none(e != null ? e.title : target);
        }
        updbuttons();
    }

    private void back() {
        if (!history.isEmpty()) {
            Object o = history.pop();
            if (o instanceof FayteAlmanac.Rec) {
                FayteAlmanac.Rec r = (FayteAlmanac.Rec) o;
                setcat(r.cat, false);
                select(r);
            } else {
                none((String) o);
            }
        }
        updbuttons();
    }

    private static String sectionof(String cat) {
        if (FayteAlmanac.RECIPES.equals(cat)) {
            return RECIPESEC;
        } else if (FayteAlmanac.SKILLS.equals(cat) || FayteAlmanac.PROFICIENCIES.equals(cat)) {
            return SKILLSEC;
        } else if (FayteAlmanac.JOURNAL.equals(cat)) {
            return JOURNEY;
        } else if (saved(cat)) {
            return SAVED;
        }
        return WORLD;
    }

    private static String firstcat(String sec) {
        if (JOURNEY.equals(sec)) {
            return FayteAlmanac.JOURNAL;
        } else if (SAVED.equals(sec)) {
            return FayteAlmanac.HISTORY;
        }
        return FayteAlmanac.ITEMS;
    }

    private void setsection(String sec) {
        boolean changed = !sec.equals(section);
        section = sec;
        for (Map.Entry<String, FayteTitleButton> b : secbtns.entrySet()) {
            b.getValue().sel = b.getKey().equals(sec);
        }
        boolean lists = this.lists();
        notes.visible = JOURNEY.equals(sec) && notesmode;
        notesbtn.visible = JOURNEY.equals(sec);
        notesbtn.sel = notes.visible;
        achs.visible = JOURNEY.equals(sec) && achmode;
        achbtn.visible = JOURNEY.equals(sec);
        achbtn.sel = achs.visible;
        FayteTitleButton jt = tabs.get(FayteAlmanac.JOURNAL);
        if (jt != null && JOURNEY.equals(sec)) {
            jt.sel = !notesmode && !achmode;
        }
        recipes.visible = RECIPESEC.equals(sec);
        if (!recipes.visible) {
            FayteRecipes.endpreview(gui);
        } else {
            recipes.previewcur();
        }
        skills.visible = SKILLSEC.equals(sec);
        builds.visible = BUILDSEC.equals(sec);
        pilgrims.visible = PILGRIMS.equals(sec);
        search.visible = lists;
        list.visible = lists;
        view.visible = lists;
        if (split != null) {
            split.visible = lists;
        }
        if (lists && changed && !sec.equals(sectionof(cat))) {
            setcat(firstcat(sec), !nopick);
        }
        relabel();
        updbuttons();
    }

    private void pickfilter() {
        new FaytePopup(filterbtn.c.add(0, FayteTitleButton.H + 2), this, FILTERS, filter, (i) -> {
            filter = i;
            filterbtn.setlabel("Show: " + FILTERS[i]);
            cur = null;
            curnone = null;
            refilter();
        });
    }

    private boolean passes(FayteAlmanac.Rec r, Set<String> craft) {
        switch (filter) {
            case 1:
                return r.fresh;
            case 2:
                return craft.contains(r.name.toLowerCase());
            case 3:
                return FayteHighlights.color(r.name) > 0 || FayteHighlights.alert(r.name);
            default:
                return true;
        }
    }

    private boolean lists() {
        return !RECIPESEC.equals(section)
                && !BUILDSEC.equals(section)
                && !PILGRIMS.equals(section)
                && !SKILLSEC.equals(section)
                && !(JOURNEY.equals(section) && (notesmode || achmode));
    }

    private void setcat(String c, boolean pickfirst) {
        if ((notesmode || achmode) && FayteAlmanac.JOURNAL.equals(c)) {
            notesmode = false;
            achmode = false;
            cat = c;
            setsection(JOURNEY);
        }
        cat = c;
        if (!sectionof(c).equals(section)) {
            setsection(sectionof(c));
        }
        for (Map.Entry<String, FayteTitleButton> t : tabs.entrySet()) {
            t.getValue().sel = t.getKey().equals(c);
        }
        list.scroll = 0;
        if (pickfirst) {
            cur = null;
            curnone = null;
        }
        refilter();
    }

    private void relabel() {
        int x = 0;

        for (String c : FayteAlmanac.CATS) {
            FayteTitleButton b = tabs.get(c);
            if (b == null) {
                continue;
            }
            b.visible = sectionof(c).equals(section) && !SKILLSEC.equals(section);
            if (!b.visible) {
                continue;
            }
            int nf = FayteAlmanac.fresh(c);
            b.setlabel(c + " (" + FayteAlmanac.size(c) + ")" + (nf > 0 ? " \u2022" + nf : ""));
            b.c = new Coord(x, ROW2);
            x += b.sz.x + 4;
        }
        for (String c : SAVEDCATS) {
            FayteTitleButton b = tabs.get(c);
            b.visible = SAVED.equals(section);
            if (b.visible) {
                b.setlabel(c + " (" + FayteAlmanac.saved(c).size() + ")");
                b.c = new Coord(x, ROW2);
                x += b.sz.x + 4;
            }
        }
        sfilterbtn.visible = SAVED.equals(section);
        sfilterbtn.c = new Coord(Math.max(x + 12, LISTW + 8), ROW2);

        filterbtn.visible = WORLD.equals(section) && FayteAlmanac.ITEMS.equals(cat);
        jfilterbtn.visible = JOURNEY.equals(section) && lists() && FayteAlmanac.JOURNAL.equals(cat);
        if (achbtn != null) {
            achbtn.c = new Coord(x, ROW2);
            x += achbtn.sz.x + 4;
        }
        if (notesbtn != null) {
            notesbtn.c = new Coord(x, ROW2);
        }
        filterbtn.c = new Coord(Math.max(x + 12, LISTW + 8), ROW2);
        int jx = x + (notesbtn != null ? notesbtn.sz.x : 0) + 12;
        jfilterbtn.c = new Coord(Math.max(jx, LISTW + 8), ROW2);
        for (Map.Entry<String, FayteTitleButton> b : secbtns.entrySet()) {
            int nf = 0;
            for (String c : FayteAlmanac.CATS) {
                if (sectionof(c).equals(b.getKey())
                        && !FayteAlmanac.RECIPES.equals(c)
                        && !SKILLSEC.equals(b.getKey())) {
                    nf += FayteAlmanac.fresh(c);
                }
            }
            b.getValue().setlabel(b.getKey() + (nf > 0 ? " \u2022" + nf : ""));
        }
        int sx = 0;
        for (String sec : SECTIONS) {
            FayteTitleButton b = secbtns.get(sec);
            b.c = new Coord(sx, 0);
            sx += b.sz.x + 6;
        }
    }

    private void refilter() {
        String q = search.text == null ? "" : search.text.trim().toLowerCase();
        List<FayteAlmanac.Rec> recs = new ArrayList<>();
        Set<String> craft = new HashSet<>();
        if (filter == 2) {
            for (FayteRecipes.Recipe rc : FayteRecipes.list(gui)) {
                craft.add(rc.name.toLowerCase());
            }
        }
        boolean filt = FayteAlmanac.ITEMS.equals(cat);
        boolean jf = FayteAlmanac.JOURNAL.equals(cat) && jfilter > 0;
        boolean sv = saved(cat);
        for (FayteAlmanac.Rec r : sv ? FayteAlmanac.saved(cat) : FayteAlmanac.list(cat)) {
            if (sv) {
                if ((q.isEmpty() || FayteAlmanac.shown(r).toLowerCase().contains(q))
                        && (sfilter == 0 || SKINDS[sfilter].equals(r.cat))) {
                    recs.add(r);
                }
                continue;
            }
            if ((q.isEmpty() || FayteAlmanac.shown(r).toLowerCase().contains(q))
                    && (!filt || passes(r, craft))
                    && (!jf || FayteAlmanac.JKINDS[jfilter].equals(FayteAlmanac.jkind(r)))) {
                recs.add(r);
            }
        }
        if (sv) {
        } else if (FayteAlmanac.JOURNAL.equals(cat)) {
            Collections.sort(recs, (a, b) -> b.name.compareTo(a.name));
        } else {
            Collections.sort(
                    recs, (a, b) -> a.fresh != b.fresh ? (a.fresh ? -1 : 1) : a.name.compareToIgnoreCase(b.name));
        }
        list.recs = recs;
        relabel();
        if (cur == null && curnone == null) {
            select(recs.isEmpty() ? null : recs.get(0));
        }
    }

    private Glob.Pagina craftable(String name) {
        Glob glob = gui.ui.sess.glob;
        List<Glob.Pagina> pags;
        synchronized (glob.paginae) {
            pags = new ArrayList<>(glob.paginae);
        }
        for (Glob.Pagina p : pags) {
            try {
                Resource r = p.res();
                if (r != null && (r.name.startsWith("paginae/craft/") || r.name.startsWith("paginae/bld/"))) {
                    Resource.AButton act = r.layer(Resource.action);
                    if (act != null && act.name != null && act.name.equalsIgnoreCase(name)) {
                        return p;
                    }
                }
            } catch (Loading l) {
            }
        }
        return null;
    }

    private final FayteTitleButton[] hlbtns;

    private void updbuttons() {
        boolean lists = this.lists();
        String hn = markname();
        boolean hl = lists && FayteHighlights.on() && hn != null;
        hlbtns[0].visible = hl;
        hlbtns[1].visible = hl;
        if (hl) {
            FayteHighlights.relabel(hlbtns, hn);
        }
        craftbtn.visible = lists && curpag != null;
        FayteAlmanac.Rec ft = favtarget();
        favbtn.visible = ft != null;
        if (ft != null) {
            favbtn.setlabel(FayteAlmanac.isfav(ft) ? "\u2605" : "\u2606");
            favbtn.sel = FayteAlmanac.isfav(ft);
            favbtn.selcol = new Color(0x9A, 0x7A, 0x20);
        }
        shopbtn.visible = RECIPESEC.equals(section) || BUILDSEC.equals(section);
        photobtn.visible = lists && cur != null;
        notebtn.visible = lists && cur != null;
        renamebtn.visible = lists && cur != null && FayteAlmanac.JOURNAL.equals(cur.cat);
        if (notebtn.visible) {
            notebtn.setlabel(cur.note == null ? "Note" : "Edit note");
        }
        backbtn.visible = !history.isEmpty();
        wikibtn.visible = lists && (cur != null || curwiki != null);
        placetwdgs();
        placeentrybar();
    }

    private void placeentrybar() {
        boolean rec = RECIPESEC.equals(section) || BUILDSEC.equals(section);
        FayteTitleButton[] bar = {shopbtn, renamebtn, notebtn, craftbtn, wikibtn, photobtn, hlbtns[1], hlbtns[0], favbtn
        };
        int x = asz.x - (rec ? 8 : 2);
        int y = rec ? ROW2 + 6 : TOP - FayteTitleButton.H - 4;
        for (FayteTitleButton b : bar) {
            if (!b.visible) {
                continue;
            }
            x -= b.sz.x;
            b.c = new Coord(x, y);
            b.raise();
            x -= 4;
        }
        int w = asz.x - x;
        recipes.reserve = rec ? w : 0;
        builds.reserve = rec ? w : 0;
    }

    private String markname() {
        if (cur != null) {
            String c = cur.cat;
            if (FayteAlmanac.JOURNAL.equals(c)
                    || FayteAlmanac.RECIPES.equals(c)
                    || FayteAlmanac.SKILLS.equals(c)
                    || FayteAlmanac.PROFICIENCIES.equals(c)) {
                return null;
            }
            return cur.name;
        }
        return curnone;
    }

    private void craft() {
        if (cur != null) {
            setsection(RECIPESEC);
            recipes.select(cur.name);
        }
    }

    private boolean nopick = false;

    private void none(String name) {
        if (!lists()) {
            nopick = true;
            try {
                setsection(WORLD);
            } finally {
                nopick = false;
            }
        }
        cur = null;
        curnone = name;
        curwiki = null;
        curpag = null;

        FayteWikiData.Entry w = FayteWikiData.find(name);
        FayteWikiEntry g;
        if (w != null) {
            curwiki = w;
            g = FayteWikiGlance.full(w, null);
            g.name = w.title;
            FayteWikiEntry.Section note = new FayteWikiEntry.Section();
            note.title = "Your record";
            note.text = "Not in your records yet. It is added once you hold it, meet it, learn it or visit it.";
            g.sections.add(0, note);
        } else {
            g = new FayteWikiEntry();
            g.name = name;
            g.category = "Not in your Almanac";
            g.summary =
                    "No entry found. You haven't come across this yet. It will appear here once you hold it, meet it,"
                            + " learn it or visit it.";
        }
        view.set(g);
        updbuttons();
    }

    private void select(FayteAlmanac.Rec r) {
        cur = r;
        curnone = null;
        curtotal = r == null ? -1 : r.total();
        curwiki = r == null ? null : FayteWikiData.find(r.name);
        curpag = r == null ? null : craftable(r.name);
        if (r == null) {
            FayteWikiEntry empty = new FayteWikiEntry();
            empty.name = "Nothing here yet";
            empty.summary =
                    "Entries appear as you hold items, meet creatures, learn recipes and skills, and walk into new"
                            + " biomes.";
            view.set(empty);
            updbuttons();
            return;
        }
        FayteAlmanac.seen(r);
        if (!FayteAlmanac.HISTORY.equals(cat)) {
            FayteAlmanac.visited(r);
        }
        if (FayteAlmanac.JOURNAL.equals(r.cat)) {
            FayteWikiEntry j = new FayteWikiEntry();
            j.name = r.label != null ? r.label : FayteAlmanac.jtitle(r);
            String st = FayteAlmanac.jstamp(r);
            j.category =
                    (st.isEmpty() ? "journal" : st) + (r.label != null ? "  (" + FayteAlmanac.jtitle(r) + ")" : "");
            if (r.note != null) {
                FayteWikiEntry.Section ns = new FayteWikiEntry.Section();
                ns.title = "Your note";
                ns.text = r.note;
                j.sections.add(ns);
            }
            FayteWikiEntry.Section jp = photosection(r);
            if (jp != null) {
                j.sections.add(jp);
            }
            FayteWikiEntry.Section s = new FayteWikiEntry.Section();
            if (r.rows != null) {
                s.rows.addAll(r.rows);
            }
            j.sections.add(s);

            view.set(j);
            list.ensurevisible(r);
            updbuttons();
            return;
        }
        FayteWikiEntry g;
        if (curwiki != null) {
            g = FayteWikiGlance.full(curwiki, r.icon);
            g.name = r.name;
        } else {
            g = new FayteWikiEntry();
            g.name = r.name;
            g.icon = r.icon;
            g.summary = "No wiki page found for this entry.";
        }
        if (g.category == null) {
            g.category = FayteAlmanac.singular(r.cat);
        }
        FayteWikiEntry.Section mine = new FayteWikiEntry.Section();
        mine.title = "Your record";
        mine.rows.add(new String[] {"First found", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(r.first))});

        for (Map.Entry<String, Integer> c : r.counts.entrySet()) {
            mine.rows.add(new String[] {c.getKey(), c.getValue() + (c.getValue() == 1 ? " time" : " times")});
        }
        g.sections.add(0, mine);
        if (r.note != null) {
            FayteWikiEntry.Section ns = new FayteWikiEntry.Section();
            ns.title = "Your note";
            ns.text = r.note;
            g.sections.add(1, ns);
        }
        FayteWikiEntry.Section ps = photosection(r);
        if (ps != null) {
            if (FayteAlmanac.CREATURES.equals(r.cat) || FayteAlmanac.BIOMES.equals(r.cat)) {
                g.sections.add(1, ps);
            } else {
                g.sections.add(ps);
            }
        }
        view.set(g);
        list.ensurevisible(r);
        updbuttons();
    }

    private FayteWikiEntry.Section photosection(FayteAlmanac.Rec r) {
        List<File> ph = FaytePhoto.photos(r.name);
        if (ph.isEmpty()) {
            return null;
        }
        FayteWikiEntry.Section ps = new FayteWikiEntry.Section();
        ps.title = "Your photos";
        StringBuilder sb = new StringBuilder();
        for (File f : ph) {
            String rel = FaytePhoto.relpath(r.name, f);
            String n = f.getName().replace(".png", "");
            String date = n.length() >= 13
                    ? n.substring(0, 4) + "-" + n.substring(4, 6) + "-" + n.substring(6, 8) + " " + n.substring(9, 11)
                            + ":" + n.substring(11, 13)
                    : n;
            sb.append(FayteMarkup.block(FayteMarkup.PICTURE, rel, date, "360"));
            sb.append(FayteMarkup.link("photo-open:" + rel, "Open"))
                    .append("   ")
                    .append(FayteMarkup.link("photo-del:" + rel, "Delete"))
                    .append("\n\n");
        }
        ps.text = sb.toString();
        return ps;
    }

    private void openwiki() {
        String url = null;
        if (curwiki != null) {
            url = curwiki.url();
        } else if (curnone != null && FayteWikiData.find(curnone) != null) {
            url = FayteWikiData.find(curnone).url();
        }
        if (url != null) {
            try {
                WebBrowser.sshow(new URL(url));
            } catch (Exception e) {
                FayteMsg.say("Could not open the wiki: " + e.getMessage(), GameUI.MsgType.BAD);
            }
        }
    }

    @Override
    public void tick(double dt) {
        if (closing) {
            closing = false;
            ui.destroy(this);
            return;
        }
        super.tick(dt);
        long now = System.currentTimeMillis();
        if (now - lastrefresh > 2000L) {
            lastrefresh = now;
            refilter();
            if (cur != null && cur.total() != curtotal) {
                select(cur);
            }
        }
    }

    private int edge = 0;
    private Coord estart = null;
    private Coord ec = null;
    private Coord esz = null;

    @Override
    public boolean mousedown(Coord c, int button) {
        almclick = anyclick;
        int e = FayteEdges.at(c, sz, FayteSkin.s(5));
        if (e != 0 && button == 1 && !FayteHud.locked(ui)) {
            edge = e;
            estart = rootpos().add(c);
            ec = this.c;
            esz = sz;
            ui.grabmouse(this);
            return true;
        }
        return super.mousedown(c, button);
    }

    @Override
    public void mousemove(Coord c) {
        if (edge != 0) {
            Coord frame = sz.sub(asz);
            Coord d = rootpos().add(c).sub(estart);
            Coord[] r = FayteEdges.apply(edge, ec, esz, d, MINSZ.add(frame));
            this.c = r[0];
            Coord nasz = r[1].sub(frame);
            if (!nasz.equals(asz)) {
                resize(nasz);
                layoutall();
            }
            return;
        }
        super.mousemove(c);
    }

    @Override
    public boolean mouseup(Coord c, int button) {
        if (edge != 0 && button == 1) {
            edge = 0;
            ui.grabmouse(null);
            Utils.setpref("fayte_alm_size2", asz.x + "," + asz.y);
            storeOpt("_pos", this.c);
            return true;
        }
        return super.mouseup(c, button);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        if (FayteEdges.at(c, sz, FayteSkin.s(5)) != 0) {
            return "Drag to resize";
        }
        return super.tooltip(c, prev);
    }

    @Override
    public void destroy() {
        rememberpage();
        FayteNotesPanel.save(true);
        FayteRecipes.endpreview(gui);
        if (instance == this) {
            instance = null;
        }
        super.destroy();
    }

    private class RecList extends Widget {
        List<FayteAlmanac.Rec> recs = new ArrayList<>();
        int scroll = 0;
        private final Map<String, Text> cache = new HashMap<>();

        RecList(Coord c, Coord sz) {
            super(c, sz, FayteAlmanacWnd.this);
        }

        private int rowh() {
            return FayteText.LIST.line();
        }

        private Text label(FayteAlmanac.Rec r) {
            String nm = FayteAlmanac.shown(r);
            Text t = cache.get(nm);
            if (t == null) {
                t = FayteText.LIST.render(nm);
                cache.put(nm, t);
            }
            return t;
        }

        void ensurevisible(FayteAlmanac.Rec r) {
            int i = recs.indexOf(r);
            if (i >= 0) {
                int rows = (sz.y - 8) / rowh();
                if (i < scroll) {
                    scroll = i;
                } else if (i >= scroll + rows) {
                    scroll = i - rows + 1;
                }
            }
        }

        @Override
        public void draw(GOut g) {
            FayteSkin.box(g, Coord.z, sz, FayteSkin.PANEL, FayteSkin.BORDER);
            int rh = rowh();
            int rows = (sz.y - 8) / rh;
            scroll = Math.max(0, Math.min(scroll, Math.max(0, recs.size() - rows)));

            for (int i = 0; i < rows && scroll + i < recs.size(); i++) {
                FayteAlmanac.Rec r = recs.get(scroll + i);
                Coord rc = new Coord(4, 4 + i * rh);
                if (r == FayteAlmanacWnd.this.cur) {
                    FayteSkin.box(g, rc.sub(2, 0), new Coord(sz.x - 4, rh), FayteSkin.BORDER, null);
                }
                if (r.fresh) {
                    g.chcolor(FayteText.LINK);
                    g.frect(rc.add(4, rh / 2 - 2), new Coord(5, 5));
                    g.chcolor();
                }
                Text t = label(r);
                g.image(t.tex(), rc.add(14, (rh - t.sz().y) / 2));
            }
        }

        @Override
        public boolean mousedown(Coord c, int button) {
            if (button == 1) {
                int i = scroll + (c.y - 4) / rowh();
                if (c.y >= 4 && i >= 0 && i < recs.size()) {
                    FayteAlmanacWnd.this.remember();
                    FayteAlmanacWnd.this.select(recs.get(i));
                }
            }
            return true;
        }

        @Override
        public boolean mousewheel(Coord c, int amount) {
            scroll += amount * 3;
            return true;
        }
    }

    @Override
    protected boolean compactstyle() {
        return true;
    }
}
