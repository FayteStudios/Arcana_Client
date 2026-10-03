package haven;

import java.awt.Color;
import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

public class FayteOptWnd extends Window {
    private static final Color GOLD = new Color(0xE3, 0xA8, 0x4A);
    private static final int PAD = FayteSkin.s(10);
    private static final int ROWH = FayteSkin.s(30);
    private static final Coord SZ = new Coord(FayteSkin.s(600), FayteSkin.s(440));
    private static final String[][] TILES = {
        {
            "interface",
            "Interface",
            "Scale, text, tooltips, brightness",
            "How big and bright everything is: interface scale, tooltip and text sizes, surface and cave brightness,"
                    + " season pictures."
        },
        {
            "windows",
            "Layout & windows",
            "Layout, locking, window reset",
            "Lock the UI in place, choose what stays movable, and gather lost windows back on screen."
        },
        {
            "controls",
            "Key bindings",
            "See and change every key",
            "Every key with a short description; click one to change it."
        },
        {"chat", "Chat", "Chat windows and text size", "Open extra chat windows and pick the chat text size."},
        {
            "audio",
            "Audio",
            "Volume per kind of sound",
            "Master volume, soundtrack, and separate volumes for effects, ambience and more."
        },
        {
            "tools",
            "Tools & automation",
            "Swaps, feeding, attacks, Smart",
            "Helpers that save clicks: tool swapping, repeat feeding, attacks at the mouse, gates, Smart interact,"
                    + " station recipes and timers."
        },
        {
            "view",
            "View & performance",
            "Draw distance, hiding, footprints",
            "Choose how far things are drawn, hide trees or crops, and show footprints under objects."
        },
        {"shots", "Screenshots", "PrintScreen options", "What PrintScreen saves and where."},
        {
            "profiles",
            "Profiles",
            "Save and load your setups",
            "Save your window layout, action bars and markers as named profiles, load them any time, and keep a"
                    + " separate set per character."
        },
        {
            "modules",
            "Modules",
            "Turn whole features on or off",
            "Every Arcana feature group can be switched off. Takes effect after a restart."
        },
        {
            "help",
            "Help & feedback",
            "Report bugs, suggestions, session",
            "Send a bug report or suggestion straight to the Arcana developer, switch character or log out."
        },
    };
    private static FayteOptWnd instance = null;
    private final GameUI gui;
    private final List<Widget> content = new ArrayList<>();
    private String page = "home";
    private int y;
    private int hover = -1;
    private double pendscale = 1.0;

    private FayteOptWnd(GameUI gui) {
        super(new Coord(200, 100), SZ, gui, "Options");
        this.gui = gui;
        justclose = true;
        build();
    }

    public static void toggle(GameUI gui) {
        if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
            instance = null;
        } else if (gui != null) {
            instance = new FayteOptWnd(gui);
        }
    }

    private <T extends Widget> T add(T w) {
        content.add(w);
        return w;
    }

    private static final Color DESC = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.6);

    private void desc(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Label l = add(new Label(
                new Coord(PAD + FayteSkin.s(22), y - FayteSkin.s(4)),
                this,
                text,
                asz.x - PAD * 2 - FayteSkin.s(26),
                FayteSkin.labelf));
        l.setcolor(DESC);
        y += l.sz.y + FayteSkin.s(4);
    }

    private void head(String s) {
        add(new Label(new Coord(PAD, y), this, s, Window.bigtf));
        y += Window.bigtf.height() + 8;
    }

    private void note(String s) {
        add(new Label(new Coord(PAD, y), this, s, FayteSkin.labelf));
        y += FayteSkin.labelf.height() + 6;
    }

    private void check(String label, String tip, boolean val, Consumer<Boolean> set) {
        CheckBox cb = add(new CheckBox(new Coord(PAD, y), this, label) {
            @Override
            public void changed(boolean v) {
                super.changed(v);
                set.accept(v);
            }
        });
        cb.a = val;
        y += Math.max(FayteSkin.s(24), cb.sz.y + 6);
        desc(tip);
    }

    private void slider(String label, String tip, double val, DoubleFunction<String> fmt, DoubleConsumer set) {
        add(new Label(new Coord(PAD, y + 1), this, label));
        add(new FayteSlider(
                new Coord(PAD + FayteSkin.s(170), y), asz.x - PAD * 2 - FayteSkin.s(180), this, val, fmt, set));
        y += ROWH;
        desc(tip);
    }

    private final List<String[]> rowtips = new ArrayList<>();

    private Button button(int x, String label, String tip, int w, Runnable r) {
        Button b = add(new Button(new Coord(PAD + FayteSkin.s(x), y), FayteSkin.s(w), this, label) {
            @Override
            public void click() {
                r.run();
            }
        });
        if (tip != null && !tip.isEmpty()) {
            rowtips.add(new String[] {label.replace("\u2026", "").trim(), tip});
        }
        return b;
    }

    private void endrow(int h) {
        y += h;
        if (rowtips.size() == 1) {
            desc(rowtips.get(0)[1]);
        } else if (!rowtips.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String[] t : rowtips) {
                sb.append(sb.length() > 0 ? "   \u00b7   " : "")
                        .append(t[0])
                        .append(": ")
                        .append(t[1]);
            }
            desc(sb.toString());
        }
        rowtips.clear();
    }

    private void choice(String[] names, double[] vals, double cur, String tip, DoubleConsumer set) {
        int x = 0;
        for (int i = 0; i < vals.length; i++) {
            final double v = vals[i];
            button(x, (Math.abs(v - cur) < 0.01 ? "\u2713 " : "") + names[i], null, 80, () -> {
                set.accept(v);
                build();
            });
            x += 86;
        }
        y += FayteSkin.s(32);
        desc(tip);
    }

    private void cmd(String c) {
        try {
            gui.ui.cons.run(new String[] {c});
        } catch (Exception e) {
            FayteMsg.say(e.getMessage(), GameUI.MsgType.BAD);
        }
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    private void go(String p) {
        page = p;
        scroll = 0;
        build();
    }

    private void embedpage(Window w) {
        add(w);
        y += w.sz.y + PAD;
    }

    private void build() {
        for (Widget w : content) {
            ui.destroy(w);
        }
        content.clear();
        rowtips.clear();
        y = 0;
        if (!page.equals("home") && !page.equals("fayte")) {
            button(0, "\u2039 Back", null, 80, () -> go("fayte"));
            y += FayteSkin.s(36);
        } else if (page.equals("fayte")) {
            button(0, "\u2039 Home", null, 80, () -> go("home"));
            y += FayteSkin.s(36);
        }
        switch (page) {
            case "home":
                home();
                break;
            case "fayte":
                break;
            case "interface":
                iface();
                break;
            case "windows":
                windows();
                break;
            case "controls":
                controls();
                break;
            case "chat":
                chat();
                break;
            case "audio":
                audio();
                break;
            case "tools":
                tools();
                break;
            case "view":
                view();
                break;
            case "timers":
                timers();
                break;
            case "almanac":
                almanac();
                break;
            case "shots":
                shots();
                break;
            case "modules":
                modules();
                break;
            case "profiles":
                profiles();
                break;
            case "help":
                help();
                break;
            default:
                home();
        }
        fit();
    }

    private int scroll = 0;
    private final Map<Widget, Integer> basey = new HashMap<>();

    private void fit() {
        basey.clear();
        for (Widget w : content) {
            basey.put(w, w.c.y);
        }
        int want = page.equals("home") || page.equals("fayte") ? SZ.y : Math.max(SZ.y, y + PAD);
        int max = Math.max(SZ.y, gui.sz.y - FayteSkin.s(80));
        int h = Math.min(want, max);
        if (h != asz.y) {
            resize(new Coord(asz.x, h));
            c = GameUI.onScreen(c, sz, gui.sz);
        }
        applyscroll();
    }

    private void applyscroll() {
        int over = Math.max(0, y + PAD - asz.y);
        scroll = Math.max(0, Math.min(scroll, over));
        for (Widget w : content) {
            Integer by = basey.get(w);
            if (by == null) {
                continue;
            }
            int ny = by - scroll;
            w.c = new Coord(w.c.x, ny);
            boolean in = (w instanceof Window && ((Window) w).embedded) || (ny >= 0 && ny + w.sz.y <= asz.y);
            if (in != w.visible) {
                w.show(in);
            }
        }
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        if (super.mousewheel(c, amount)) {
            return true;
        }
        if (y + PAD > asz.y) {
            scroll += amount * FayteSkin.s(30);
            applyscroll();
        }
        return true;
    }

    private int cardw() {
        return (asz.x - PAD * 3) / 2;
    }

    private int cardh() {
        return FayteSkin.s(150);
    }

    private void home() {
        y = SZ.y - Button.bh() - FayteSkin.s(6);
        button(0, "Switch character", "Go back to character select.", 160, () -> gui.act("lo", "cs"));
        button(170, "Log out", "Log out of the game.", 120, () -> gui.act("lo"));
        rowtips.clear();
        y = 0;
    }

    private int helph() {
        return FayteSkin.s(22) + Window.bigtf.height() + FayteSkin.labelf.height();
    }

    private int tilew() {
        return (asz.x - PAD * 4) / 3;
    }

    private int tileh() {
        return FayteSkin.s(66);
    }

    private Coord tilepos(int i) {
        int top = FayteSkin.s(36);
        return new Coord(PAD + (i % 3) * (tilew() + PAD), top + (i / 3) * (tileh() + PAD));
    }

    private void iface() {
        head("Interface");
        pendscale = FayteConfig.uiScale.get();
        slider(
                "Interface scale",
                "Makes the whole interface bigger. Slide, then press Apply; you have 15 seconds to keep it before it"
                        + " goes back.",
                (pendscale - 1.0) / 2.0,
                (v) -> String.format("%.2f\u00d7", Math.round((1.0 + v * 2.0) * 20.0) / 20.0),
                (v) -> pendscale = Math.round((1.0 + v * 2.0) * 20.0) / 20.0);
        button(
                0,
                "Apply scale",
                "Apply the scale above. You get 15 seconds to keep it.",
                130,
                () -> FayteScaleConfirm.apply(gui, pendscale));
        endrow(FayteSkin.s(34));
        add(new Label(new Coord(PAD, y + 4), this, "Tooltip size"));
        y += FayteSkin.s(24);
        choice(
                new String[] {"Base", "1.5\u00d7", "2\u00d7"},
                new double[] {1.0, 1.5, 2.0},
                FayteConfig.tooltipScale.get(),
                "How big the pop-up hints are.",
                (v) -> FayteConfig.tooltipScale.set(v));
        add(new Label(new Coord(PAD, y + 4), this, "Tooltip delay (how long to hover before a hint shows)"));
        y += FayteSkin.s(24);
        choice(
                new String[] {"Instant", "1 s", "2 s", "3 s"},
                new double[] {0.0, 1.0, 2.0, 3.0},
                FayteConfig.tooltipDelay.get(),
                "How long to hover before a hint shows.",
                (v) -> FayteConfig.tooltipDelay.set(v));
        add(new Label(new Coord(PAD, y + 4), this, "Almanac buttons (takes effect when the Almanac reopens)"));
        y += FayteSkin.s(24);
        choice(
                new String[] {"Words", "Icon + word", "Icon over word"},
                new double[] {0.0, 1.0, 2.0},
                FayteConfig.almanacIcons.get(),
                "How the Almanac's section and tab buttons look.",
                (v) -> FayteConfig.almanacIcons.set(v));
        add(new Label(new Coord(PAD, y + 4), this, "Text font (after a restart)"));
        y += FayteSkin.s(24);
        int curf = Arrays.asList(FayteFont.FAMILIES).indexOf(FayteFont.family());
        choice(
                FayteFont.FAMILIES,
                new double[] {0, 1, 2, 3},
                curf,
                "The font for all Arcana text. Takes effect after a restart.",
                (v) -> Utils.setpref("fayte_font", FayteFont.FAMILIES[(int) v]));
        add(new Label(new Coord(PAD, y + 4), this, "Letter spacing (after a restart)"));
        y += FayteSkin.s(24);
        choice(
                FayteFont.SPACINGS,
                new double[] {0, 1, 2},
                FayteFont.spacing(),
                "Extra room between letters and digits. Takes effect after a restart.",
                (v) -> Utils.setpref("fayte_font_spacing", Integer.toString((int) v)));
        slider(
                "Interface text",
                "Text size in Arcana windows. Takes effect after a restart.",
                (FayteTextSize.pct(FayteTextSize.UI, 115) - 80) / 120.0,
                (v) -> Math.round(80 + v * 120) + "%",
                (v) -> Utils.setpref(FayteTextSize.UI, Long.toString(Math.round(80 + v * 120))));
        slider(
                "Almanac text",
                "Text size of Almanac pages. Takes effect after a restart.",
                (FayteTextSize.pct(FayteTextSize.ALMANAC) - 80) / 120.0,
                (v) -> Math.round(80 + v * 120) + "%",
                (v) -> Utils.setpref(FayteTextSize.ALMANAC, Long.toString(Math.round(80 + v * 120))));
        slider(
                "Notes text",
                "Text size of your notes in the Almanac.",
                (FayteAlmanacWnd.prefint(FayteTextSize.NOTES, 20) - 10) / 30.0,
                (v) -> Math.round(10 + v * 30) + " pt",
                (v) -> Utils.setpref(FayteTextSize.NOTES, Long.toString(Math.round(10 + v * 30))));
        String where = FayteBright.incave(gui) ? "You are underground right now." : "You are on the surface right now.";
        slider(
                "Surface brightness",
                "How bright the world is outdoors. " + where,
                FayteBright.surface(),
                FayteOptWnd::pct,
                (v) -> FayteBright.set(false, (float) v, gui));
        slider(
                "Cave brightness",
                "How bright mines and caves are. " + where,
                FayteBright.cave(),
                FayteOptWnd::pct,
                (v) -> FayteBright.set(true, (float) v, gui));
        check(
                "Season pictures",
                "Show the season's picture when the season changes.",
                FayteSeason.on(),
                FayteSeason::seton);
    }

    private void windows() {
        head("Layout & windows");
        check(
                "Lock UI",
                "Windows and HUD pieces stay where they are until you unlock.",
                FayteConfig.lockUi.get(),
                (v) -> FayteConfig.lockUi.set(v));
        button(0, "Reset windows", "Bring every window back onto the screen.", 150, () -> cmd("gatherwindows"));
        endrow(FayteSkin.s(38));
        add(new Label(new Coord(PAD, y), this, "Movable while the UI is locked:"));
        y += FayteSkin.labelf.height() + 8;
        int col = 0;
        int y0 = y;
        int half = (FayteLock.WINDOWS.length + 1) / 2;
        for (int i = 0; i < FayteLock.WINDOWS.length; i++) {
            final String l = FayteLock.WINDOWS[i][0];
            col = i < half ? 0 : 1;
            int yy = y0 + (i % half) * FayteSkin.s(24);
            CheckBox cb = add(new CheckBox(new Coord(PAD + col * (asz.x / 2), yy), this, l) {
                @Override
                public void changed(boolean val) {
                    super.changed(val);
                    FayteLock.set(l, val);
                }
            });
            cb.a = FayteLock.on(l);
        }
        y = y0 + half * FayteSkin.s(24);
    }

    private void controls() {
        head("Controls");
        embedpage(FayteKeysWnd.embedded(this, new Coord(PAD, y)));
        button(
                0,
                "Actions & bars\u2026",
                "Every action and toggle in one place. Drag them onto your own action bars, and build macros.",
                200,
                () -> FayteActionsWnd.toggle(gui));
        endrow(FayteSkin.s(34));
    }

    private void chat() {
        head("Chat");
        button(
                0,
                "New chat window",
                "Opens another chat window. Pick its channels with the buttons on its right side.",
                180,
                () -> FayteChatWindow.create(gui));
        endrow(FayteSkin.s(34));
        check(
                "Arcana notices in their own channels",
                "Put Arcana's own messages in separate chat channels: Arcana: Crafting, Arcana: Alerts (timers,"
                        + " knockouts, highlights), Arcana: Map, and Arcana for everything else.",
                FayteMsg.ownchannel(),
                FayteMsg::setownchannel);
        add(new Label(new Coord(PAD, y + 4), this, "Chat text size"));
        y += FayteSkin.s(24);
        int cur = (int) Utils.getpreff("chatfontsize", 12);
        choice(
                new String[] {"Base", "1.5\u00d7", "2\u00d7"},
                new double[] {12, 18, 24},
                cur,
                "Text size in chat windows.",
                (v) -> {
                    int fs = (int) v;
                    gui.chat.setbasesize(fs);
                    FayteChatWindow.setsize(fs);
                    Utils.setpreff("chatfontsize", fs);
                });
    }

    private void audio() {
        head("Audio");
        slider("Master", "Overall game volume.", Audio.volume, FayteOptWnd::pct, (v) -> {
            Audio.setvolume(v);
            Utils.setpreff("sfxvol", (float) v);
        });
        slider("Soundtrack", "Music volume.", Music.volume, FayteOptWnd::pct, (v) -> Music.setvolume(v));
        for (int i = 0; i < FayteSound.CATS.length; i++) {
            final String cat = FayteSound.CATS[i];
            slider(
                    FayteSound.NAMES[i],
                    "Volume of " + FayteSound.NAMES[i].toLowerCase()
                            + ". Effects are actions and creatures; ambient is the background.",
                    FayteSound.gain(cat),
                    FayteOptWnd::pct,
                    (v) -> FayteSound.set(cat, v));
        }
        check(
                "Mute violin and market music",
                "Silences the violin player and the market tunes.",
                Config.mute_violin,
                (v) -> {
                    Config.mute_violin = v;
                    Utils.setprefb("mute_violin", v);
                });
    }

    private void tools() {
        head("Tools & automation");
        embedpage(FayteToolsWnd.embedded(this, new Coord(PAD, y)));
        check(
                "Open containers glow in the world",
                "A chest or other container you have open gets a ring in its window's colour, so you can see which one"
                        + " it is.",
                FayteConfig.openGlow.get(),
                FayteConfig.openGlow::set);
        check(
                "Timers you add belong to this character",
                "On: each character only sees their own timers. Off: timers you add are shared by every character on"
                        + " this computer. Automatic timers always belong to the character they started for.",
                FayteConfig.timersPerChar.get(),
                FayteConfig.timersPerChar::set);
    }

    private void view() {
        head("View & performance");
        embedpage(FayteViewWnd.embedded(this, new Coord(PAD, y)));
    }

    private void timers() {
        head("Timers & alerts");
        button(
                0,
                "Timers\u2026",
                "Your timers, on the server clock. Pop a timer to keep it on screen.",
                160,
                () -> FayteTimersWnd.toggle(gui));
        button(
                170,
                "Selections\u2026",
                "Area selections: mark areas on the ground and see their size.",
                160,
                () -> FayteSelectionsWnd.toggle(gui));
        endrow(FayteSkin.s(34));
    }

    private void almanac() {
        head("Almanac & crafting");
        button(
                0,
                "Recipes\u2026",
                "Craft from the Almanac with exact ingredients, Craft N and Craft all.",
                160,
                () -> cmd("recipes"));
        button(170, "Skills\u2026", "Skills and proficiencies, with goals.", 160, () -> cmd("skills"));
        endrow(FayteSkin.s(34));
    }

    private void shots() {
        head("Screenshots");
        check(
                "PrintScreen takes screenshots",
                "Press PrintScreen to save a screenshot.",
                FayteOpt.shots(),
                FayteOpt::setshots);
        check("Include the interface", "Keep windows and HUD in the picture.", Config.ss_ui, (v) -> {
            Config.ss_ui = v;
            Utils.setprefb("ss_ui", v);
        });
        check("Save silently", "Save without asking where.", Config.ss_silent, (v) -> {
            Config.ss_silent = v;
            Utils.setprefb("ss_slent", v);
        });
        check("Compress (JPEG)", "Smaller files, slightly lower quality.", Config.ss_compress, (v) -> {
            Config.ss_compress = v;
            Utils.setprefb("ss_compress", v);
        });
        button(0, "Open folder", "Screenshots stay on this computer and carry no account details.", 140, () -> {
            try {
                File d = Config.getFile("screenshots");
                d.mkdirs();
                Desktop.getDesktop().open(d);
            } catch (Exception e) {
                FayteMsg.say("Could not open the folder: " + e.getMessage(), GameUI.MsgType.BAD);
            }
        });
        endrow(FayteSkin.s(34));
    }

    private void profiles() {
        head("Profiles");
        desc("A profile is a character's whole setup: where windows and HUD pieces sit, chat windows, action bars"
                + " and macros, right-click menu choices, recipe favorites and \"any ingredient\" picks, and object"
                + " colours and alerts. World map markers stay with each character.");
        desc("Every character keeps its own setup, saved as a profile with its name when you log out. Load copies a"
                + " profile onto this character; Save as keeps a copy of how it is now. Nothing is replaced without"
                + " a copy going into History. New characters start from Default, a clean slate that can't be"
                + " changed.");
        add(new Label(new Coord(PAD, y), this, "This character: " + FayteProfiles.activelabel(), FayteSkin.labelf));
        y += FayteSkin.labelf.height() + 4;
        button(
                0,
                "Save as\u2026",
                "Keep a copy of this character's setup as a profile with a new name.",
                110,
                () -> new FayteAsk(gui, "Save profile", "Name for this profile.", "", (n) -> {
                    final String name = n.trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    if (FayteProfiles.isdefault(name)) {
                        FayteMsg.say(
                                "Default is the fresh start for new characters and can't be changed. Pick another"
                                        + " name.",
                                GameUI.MsgType.BAD);
                        return;
                    }
                    Runnable go = () -> {
                        FayteProfiles.saveas(name);
                        FayteMsg.say("Profile saved as \"" + name + "\".");
                        build();
                    };
                    if (FayteProfiles.exists(name)) {
                        FayteConfirm.warn(
                                gui,
                                "Replace \"" + name + "\"?",
                                "There is already a profile called \"" + name
                                        + "\". Replace it with this character's setup? The old one goes into History.",
                                go);
                    } else {
                        go.run();
                    }
                }));
        button(
                116,
                "Load\u2026",
                "Copy a saved profile onto this character.",
                90,
                () -> pickprofile((n) -> FayteConfirm.warn(
                        gui,
                        "Load \"" + n + "\"?",
                        "Replace this character's whole setup with \"" + n + "\"? How it is now goes into History.",
                        () -> {
                            if (FayteProfiles.use(gui, n)) {
                                FayteMsg.say("Profile \"" + n + "\" copied onto this character.");
                                build();
                            }
                        })));
        button(
                212,
                "History\u2026",
                "Get back an older version of a profile: every replaced or loaded-over version is kept.",
                110,
                this::pickhistory);
        endrow(FayteSkin.s(34));
        mapchars();
    }

    private void mapchars() {
        head("World map");
        check(
                "Separate world map per character",
                "Each character's world map (and the minimap's filled-in edges) only shows places that character has"
                        + " explored. The first character to turn this on keeps everything explored so far.",
                FayteConfig.mapPerChar.get(),
                (v) -> FayteConfig.mapPerChar.set(v));
        check(
                "Separate map markers per character",
                "Each character keeps their own world map markers. The first character keeps the current markers.",
                FayteConfig.markersPerChar.get(),
                (v) -> {
                    FayteConfig.markersPerChar.set(v);
                    WorldMapMarkers.reload();
                });
        check(
                "Fill the minimap's empty edges from your world map",
                "Where the game hasn't loaded the area yet, the minimap shows what your world map remembers.",
                FayteConfig.miniFill.get(),
                (v) -> FayteConfig.miniFill.set(v));
        button(
                0,
                "Import from another character\u2026",
                "Add another character's explored map and markers to this character's.",
                260,
                () -> {
                    Set<String> names = new TreeSet<>();
                    FayteMapStore st = FayteMapStore.current();
                    if (st != null) {
                        names.addAll(FayteMapSeen.others(st));
                    }
                    names.addAll(WorldMapMarkers.othermarkers());
                    if (names.isEmpty()) {
                        FayteMsg.say("No other characters have their own map yet.");
                        return;
                    }
                    final List<String> l = new ArrayList<>(names);
                    new FaytePopup(ui.mc, ui.root, l.toArray(new String[0]), -1, (i) -> {
                        String o = l.get(i);
                        int g = st == null ? 0 : FayteMapSeen.importfrom(st, o);
                        int m = WorldMapMarkers.importfrom(o);
                        FayteMsg.say("Imported from " + o + ": " + g + " map areas, " + m + " markers.");
                    });
                });
        endrow(FayteSkin.s(34));
        button(
                0,
                "Show this character the whole map",
                "Give this character every area explored by anyone on this computer. Use it if a character lost their"
                        + " map when maps became per character.",
                260,
                () -> {
                    FayteMapStore st = FayteMapStore.current();
                    if (st == null) {
                        FayteMsg.say("The world map isn't loaded yet.");
                        return;
                    }
                    int g = FayteMapSeen.giveall(st);
                    FayteMsg.say(
                            g == 0
                                    ? "This character already sees everything explored."
                                    : "Added " + g + " map areas to this character's world map.");
                });
        endrow(FayteSkin.s(34));
    }

    private void pickprofile(Consumer<String> then) {
        final List<String> names = FayteProfiles.list();
        if (names.isEmpty()) {
            FayteMsg.say("No saved profiles yet. Use Save as first.");
            return;
        }
        new FaytePopup(ui.mc, ui.root, names.toArray(new String[0]), -1, (i) -> then.accept(names.get(i)));
    }

    private void pickhistory() {
        final List<String> names = FayteProfiles.historynames();
        if (names.isEmpty()) {
            FayteMsg.say("No older profile versions yet.");
            return;
        }
        new FaytePopup(ui.mc, ui.root, names.toArray(new String[0]), -1, (i) -> {
            final String name = names.get(i);
            final List<String[]> vs = FayteProfiles.history(name);
            String[] labels = new String[vs.size()];
            for (int k = 0; k < labels.length; k++) {
                labels[k] = vs.get(k)[1];
            }
            new FaytePopup(ui.mc, ui.root, labels, -1, (j) -> FayteConfirm.warn(
                    gui,
                    "Restore \"" + name + "\"?",
                    "Put the " + labels[j] + " version back as the \"" + name
                            + "\" profile? The current one goes into History. Then use Load to put it on a character.",
                    () -> {
                        if (FayteProfiles.restore(name, vs.get(j)[0])) {
                            FayteMsg.say("Profile \"" + name + "\" restored.");
                        }
                    }));
        });
    }

    private void modules() {
        head("Modules");
        note("Changes apply after a restart.");
        for (final FayteModule m : FayteModules.all()) {
            if (!m.required()) {
                check(m.name, m.desc, m.setting.get(), (v) -> m.setting.set(v));
            }
        }
    }

    private void help() {
        head("Help & feedback");
        button(
                0,
                "Report a bug or suggest something",
                "Sends your message straight to the Arcana developer.",
                280,
                () -> FayteFeedbackWnd.open(gui));
        endrow(FayteSkin.s(36));
        button(
                0,
                "Ask again",
                "Bring back every \"Are you sure?\" question you told Arcana not to ask again.",
                140,
                () -> {
                    FayteConfirm.forgetall();
                    FayteMsg.say("Arcana will ask again before those choices.");
                });
        endrow(FayteSkin.s(34));
        if (FayteConfig.DEV) {
            check(
                    "Diagnostics",
                    "Record station windows and window moves to files.",
                    FayteConfig.diagnostics.get(),
                    FayteConfig.diagnostics::set);
        }
        y += FayteSkin.s(6);
        note("Logged in this session: " + FayteProgress.fmt(FayteTrade.online())
                + (FayteTrade.ready() ? "  (trade orders can be taken)" : "  (trade orders need an hour online)"));
    }

    private int homeat(Coord p) {
        if (!page.equals("home")) {
            return -1;
        }
        int top = FayteSkin.s(40);
        for (int i = 0; i < 2; i++) {
            int x = PAD + i * (cardw() + PAD);
            if (p.x >= x && p.x < x + cardw() && p.y >= top && p.y < top + cardh()) {
                return i;
            }
        }
        int fy = top + cardh() + PAD;
        if (p.x >= PAD && p.x < asz.x - PAD && p.y >= fy && p.y < fy + helph()) {
            return 2;
        }
        return -1;
    }

    private int tileat(Coord p) {
        if (!page.equals("fayte")) {
            return -1;
        }
        for (int i = 0; i < TILES.length; i++) {
            Coord tp = tilepos(i);
            if (p.x >= tp.x && p.x < tp.x + tilew() && p.y >= tp.y && p.y < tp.y + tileh()) {
                return i;
            }
        }
        return -1;
    }

    private final Map<String, Tex> cache = new HashMap<>();

    private Tex cached(String k, Supplier<Text> make) {
        Tex t = cache.get(k);
        if (t == null) {
            t = make.get().tex();
            cache.put(k, t);
        }
        return t;
    }

    private void card(GOut g, Coord c, Coord sz, String title, String sub, boolean hov) {
        FayteSkin.box(
                g,
                c,
                sz,
                hov
                        ? FayteSkin.mix(FayteSkin.PANEL, GOLD, 0.18)
                        : FayteSkin.mix(FayteSkin.PANEL, FayteSkin.BORDER, 0.2),
                hov ? GOLD : FayteSkin.BORDER);
        g.image(
                cached("t" + hov + title, () -> Window.bigtf.render(title, hov ? GOLD : FayteSkin.TEXT)),
                c.add(FayteSkin.s(10), FayteSkin.s(8)));
        int w = sz.x - FayteSkin.s(20);
        g.image(
                cached(
                        "s" + w + sub,
                        () -> FayteSkin.labelf.renderwrap(
                                sub, FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.8), w)),
                c.add(FayteSkin.s(10), FayteSkin.s(12) + Window.bigtf.height()));
    }

    @Override
    public void cdraw(GOut g) {
        if (page.equals("home")) {
            g.image(
                    cached("settings", () -> Window.bigtf.render("Settings", FayteSkin.TEXT)),
                    new Coord(PAD, FayteSkin.s(6)));
            int top = FayteSkin.s(40);
            card(
                    g,
                    new Coord(PAD, top),
                    new Coord(cardw(), cardh()),
                    "Arcana",
                    "Everything Arcana adds: interface size, chat, tools, timers, the Almanac and more, sorted into"
                            + " tiles.",
                    hover == 0);
            card(
                    g,
                    new Coord(PAD * 2 + cardw(), top),
                    new Coord(cardw(), cardh()),
                    "Classic",
                    "The game's and Latikai's own options: graphics, camera, and older client settings.",
                    hover == 1);
            card(
                    g,
                    new Coord(PAD, top + cardh() + PAD),
                    new Coord(asz.x - PAD * 2, helph()),
                    "Help & feedback",
                    "Report a bug or suggest something.",
                    hover == 2);
        } else if (page.equals("fayte")) {
            for (int i = 0; i < TILES.length; i++) {
                card(g, tilepos(i), new Coord(tilew(), tileh()), TILES[i][1], TILES[i][2], hover == i);
            }
        }
    }

    @Override
    public void mousemove(Coord c) {
        Coord p = c.sub(atl);
        int h = page.equals("home") ? homeat(p) : tileat(p);
        hover = h;
        super.mousemove(c);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        Coord p = c.sub(atl);
        int t = tileat(p);
        if (t >= 0) {
            return TILES[t][3];
        }
        return super.tooltip(c, prev);
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        Coord p = c.sub(atl);
        if (button == 1) {
            int h = homeat(p);
            if (h == 0) {
                go("fayte");
                return true;
            } else if (h == 1) {
                OptWnd2.toggle();
                return true;
            } else if (h == 2) {
                go("help");
                return true;
            }
            int t = tileat(p);
            if (t >= 0) {
                go(TILES[t][0]);
                return true;
            }
        }
        return super.mousedown(c, button);
    }

    @Override
    public void destroy() {
        if (instance == this) {
            instance = null;
        }
        super.destroy();
    }

    @Override
    protected boolean compactstyle() {
        return true;
    }
}
