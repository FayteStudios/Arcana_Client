package haven;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class FayteInspect extends Window {
    public static final String TITLE = "Inspect";
    public static final String OPTION = "Inspect";
    public static final String ALMANAC = "Almanac";
    public static final String ARRANGE = "Arrange right-click options\u2026";
    public static final Coord SZ = new Coord(FayteSkin.s(380), FayteSkin.s(420));
    private static final Coord MINSZ = new Coord(FayteSkin.s(280), FayteSkin.s(220));
    private FayteGrip grip;
    private static final Pattern DONE = Pattern.compile("\\s*\\(\\d+% done\\)\\s*$");
    private static FayteInspect instance;
    private static volatile Gob pendgob;
    private FayteWikiEntry.View view;
    private FayteWikiData.Entry entry;
    private GItem item;
    private String itemname;
    private String icon;
    private boolean current = false;
    private final FayteTitleButton curbtn;

    public FayteInspect(Widget parent) {
        super(new Coord(120, 80), FayteGrip.saved("fayte_inspect_size", SZ, MINSZ), parent, TITLE);
        justclose = true;
        addtwdg(new FayteTitleButton(this, "Almanac", "Open this in the Almanac", this::almanac));
        FayteTitleButton wiki =
                new FayteTitleButton(this, "W", "Open this entry on the Salem wiki in your browser", this::openwiki);
        addtwdg(wiki);
        curbtn = new FayteTitleButton(
                this,
                "Base",
                "Showing the entry for this kind of item. Click to see this item's own values.",
                () -> mode(!current));
        addtwdg(curbtn);
        hlbtns = FayteHighlights.buttons(this, () -> entry != null ? entry.title : itemname);
        addtwdg(hlbtns[1]);
        addtwdg(hlbtns[0]);
    }

    private final FayteTitleButton[] hlbtns;

    private void almanac() {
        if (ui.gui != null) {
            FayteAlmanacWnd.showlink(ui.gui, entry != null ? entry.title : itemname);
        }
    }

    private void relayout() {
        if (view != null) {
            view.resize(asz);
        }
    }

    private void mode(boolean cur) {
        current = cur && item != null;
        curbtn.setlabel(current ? "This item" : "Base");
        curbtn.tooltip = current
                ? "Showing this item's own values. Click to see the entry."
                : "Showing the entry for this kind of item. Click to see this item's own values.";
        placetwdgs();
        refresh();
    }

    private void refresh() {
        FayteWikiEntry g;
        if (current && item != null) {
            g = FayteItemFacts.entry(item, itemname, icon, entry);
        } else if (entry != null) {
            g = FayteWikiGlance.glance(entry, icon);
        } else {
            g = new FayteWikiEntry();
            g.name = itemname;
            g.summary = "No wiki entry for this item yet.";
        }
        if (view == null) {
            view = new FayteWikiEntry.View(Coord.z, asz, this, g);
            view.onlink = target -> {
                if (ui.gui != null) {
                    FayteAlmanacWnd.showlink(ui.gui, target);
                }
            };
            grip = new FayteGrip(this, "fayte_inspect_size", MINSZ, this::relayout);
            grip.place();
        } else {
            view.set(g);
        }
    }

    public static void middleclick(WItem w) {
        String name = null;
        String res = null;

        try {
            w.item.info();
            String n = w.item.name();
            if (n != null) {
                name = DONE.matcher(n).replaceAll("").trim();
            }
            res = w.item.resname();
        } catch (Exception e) {
            FayteLog.log("Inspect: could not read item name: " + e);
        }
        if (name == null) {
            return;
        }
        FayteWikiData.Entry e =
                res != null && FayteEntries.alias(res) != null ? FayteWikiData.find(res) : FayteWikiData.find(name);
        boolean known = FayteAlmanac.get(FayteAlmanac.ITEMS, name) != null;
        if (e == null && FayteWikiData.get() != null) {
            FayteMissing.note("item", name, res);
        }
        if (w.ui.gui != null) {
            List<String> opts = new ArrayList<>();
            opts.add(OPTION);
            if (known || e != null) {
                opts.add(ALMANAC);
            }
            if (res != null && FayteMenus.on() && FayteConfig.menuArrange.get()) {
                opts.add(ARRANGE);
            }
            FayteInspect.Menu m = new FayteInspect.Menu(
                    w.ui.gui, w.ui.mc, e, res, name, FayteAlmanac.ITEMS, opts.toArray(new String[0]));
            m.item = w.item;
            m.reskey = res;
        }
    }

    public static void worldclick(MapView mv, Coord c) {
        mv.delay(mv.new Hittest(c) {
            @Override
            public void hit(Coord pc, Coord mc, MapView.ClickInfo inf) {
                if (inf != null && inf.gob != null) {
                    pendgob = inf.gob;
                }
            }
        });
    }

    public static void tick(GameUI gui) {
        Gob g = pendgob;
        if (g != null) {
            pendgob = null;
            String rn = FayteMsg.resname(g);
            if (rn == null || rn.isEmpty() || rn.startsWith("gfx/borka/")) {
                return;
            }
            String name = FayteWorldNames.display(rn);
            FayteWikiData.Entry e = FayteWorldNames.lookup(rn);
            FayteAlmanac.Rec rec = FayteAlmanac.match(e != null ? e.title : name);
            if (e == null && FayteWikiData.get() != null) {
                FayteMissing.note(rn.startsWith("gfx/kritter/") ? "creature" : "world object", name, rn);
            }
            boolean arrange = FayteMenus.on() && FayteConfig.menuArrange.get();
            List<String> opts = new ArrayList<>();
            if (e != null) {
                opts.add(OPTION);
            }
            opts.add(ALMANAC);

            if (arrange) {
                opts.add(ARRANGE);
            }
            FayteInspect.Menu m = new FayteInspect.Menu(
                    gui,
                    gui.ui.mc,
                    e,
                    null,
                    rec != null ? rec.name : name,
                    rec != null ? rec.cat : null,
                    opts.toArray(new String[0]));
            m.reskey = rn;
        }
    }

    public static class Menu extends FlowerMenu {
        private final GameUI gui;
        private final FayteWikiData.Entry entry;
        private final String icon;
        private final String name;
        private final String cat;
        GItem item;
        String reskey;

        Menu(GameUI gui, Coord c, FayteWikiData.Entry entry, String icon, String name, String cat, String... opts) {
            super(c, gui.ui.root, opts);
            this.gui = gui;
            this.entry = entry;
            this.icon = icon;
            this.name = name;
            this.cat = cat;
        }

        @Override
        public void choose(FlowerMenu.Petal opt) {
            if (opt != null && opt.name.equals(ARRANGE)) {
                FayteMenuWnd.open(gui, reskey, name, null);
                uimsg("act", new Object[] {opt.num});
                return;
            }
            if (opt != null) {
                if (opt.name.equals(ALMANAC)) {
                    if (cat != null && FayteAlmanac.get(cat, name) != null) {
                        FayteAlmanacWnd.show(gui, cat, name);
                    } else {
                        FayteAlmanacWnd.showlink(gui, entry != null ? entry.title : name);
                    }
                } else if (entry != null || item != null) {
                    open(gui, entry, icon, item, name);
                }
                uimsg("act", new Object[] {opt.num});
            } else {
                uimsg("cancel", new Object[0]);
            }
        }
    }

    public static void open(Widget parent, FayteWikiData.Entry e, String icon) {
        open(parent, e, icon, null, e == null ? null : e.title);
    }

    public static void open(Widget parent, FayteWikiData.Entry e, String icon, GItem item, String name) {
        if (instance == null || !instance.attached()) {
            instance = new FayteInspect(parent);
        }
        instance.set(e, icon, item, name);
        instance.raise();
    }

    private void set(FayteWikiData.Entry e, String icon, GItem item, String name) {
        entry = e;
        this.icon = icon;
        this.item = item;
        itemname = name;
        curbtn.show(item != null);
        boolean hl = FayteHighlights.on();
        hlbtns[0].show(hl);
        hlbtns[1].show(hl);
        if (hl) {
            FayteHighlights.relabel(hlbtns, e != null ? e.title : name);
        }
        placetwdgs();
        mode(e == null);
    }

    private void openwiki() {
        if (entry != null) {
            try {
                WebBrowser.sshow(new URL(entry.url()));
            } catch (Exception e) {
                FayteMsg.say("Could not open the wiki: " + e.getMessage(), GameUI.MsgType.BAD);
            }
        }
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
