package haven;

public class FayteToolsWnd extends Window {
    private static FayteToolsWnd instance = null;
    private final Label radius;

    private FayteToolsWnd(Widget parent) {
        super(
                new Coord(FayteSkin.s(220), FayteSkin.s(120)),
                new Coord(FayteSkin.s(360), FayteSkin.s(400)),
                parent,
                "Tools & Automation");
        justclose = true;
        int y = 0;
        y = check(y, FayteConfig.toolsFill, "Fill\u2026 on buckets, pails, pots and jugs");
        y = check(y, FayteConfig.toolsFeed, "Ctrl+use keeps feeding the same target");
        y = check(y, FayteConfig.toolsSwap, "Swap to the right tool when the game asks for one");
        y = check(y, FayteConfig.autoAttack, "Attack keys hit where your mouse points, no second click");
        y = check(y, FayteConfig.swimMode, "Go back to your movement mode after swimming");
        y = check(y, FayteConfig.stationRecipes, "Using a workbench opens its recipe card");
        y = check(y, FayteConfig.autoTimers, "Start timers when you light or seal a station");
        y = check(y, FayteConfig.menuArrange, "Middle-click menu offers \"Arrange right-click options\u2026\"");
        new Button(new Coord(0, y), FayteSkin.s(220), this, "Right-click menu order\u2026") {
            @Override
            public void click() {
                FayteMenuWnd.open(FayteToolsWnd.this.getparent(GameUI.class), null, null, null);
            }
        };
        y += FayteSkin.s(30);
        y = check(y, FayteConfig.doorsBuilding, "Right-click a building to use its door");
        y = check(y, FayteConfig.doorsGates, "Open gates you walk into");
        y += FayteSkin.s(10);
        y = check(y, FayteConfig.smartGround, "Right-click empty ground runs Smart interact");
        new Label(new Coord(0, y), this, "Smart interact (Space by default, see Key Bindings) uses the nearest:");
        y += FayteSkin.s(22);
        y = check(y, FayteConfig.smartAny, "Anything at all (ignores the list below)");
        FayteConfig.BoolSetting[] s = {
            FayteConfig.smartHerbs,
            FayteConfig.smartItems,
            FayteConfig.smartCrops,
            FayteConfig.smartBushes,
            FayteConfig.smartTrees,
            FayteConfig.smartBoulders,
            FayteConfig.smartAnimals
        };
        String[] n = {"Herbs & flowers", "Items on the ground", "Crops", "Bushes", "Trees", "Boulders", "Animals"};
        for (int i = 0; i < s.length; i++) {
            final FayteConfig.BoolSetting st = s[i];
            CheckBox cb =
                    new CheckBox(new Coord((i % 2) * FayteSkin.s(180), y + (i / 2) * FayteSkin.s(24)), this, n[i]) {
                        public void changed(boolean val) {
                            st.set(val);
                        }
                    };
            cb.a = st.get();
        }
        y += 4 * FayteSkin.s(24) + FayteSkin.s(4);
        radius = new Label(new Coord(0, y), this, "");
        y += FayteSkin.s(16);
        new HSlider(new Coord(0, y), FayteSkin.s(340), this, 1, 30, FayteConfig.smartRadius.get()) {
            public void changed() {
                FayteConfig.smartRadius.set(val);
                FayteToolsWnd.this.upd();
            }
        };
        upd();
        pack();
    }

    private int check(int y, final FayteConfig.BoolSetting s, String label) {
        CheckBox cb = new CheckBox(new Coord(0, y), this, label) {
            public void changed(boolean val) {
                s.set(val);
            }
        };
        cb.a = s.get();
        return y + FayteSkin.s(24);
    }

    private void upd() {
        radius.settext("Smart interact reaches: " + FayteConfig.smartRadius.get() + " tiles");
    }

    public static FayteToolsWnd embedded(Widget parent, Coord c) {
        FayteToolsWnd w = new FayteToolsWnd(parent);
        w.embed();
        w.c = c;
        return w;
    }

    public static void toggle(GameUI gui) {
        if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
        } else if (gui != null) {
            instance = new FayteToolsWnd(gui);
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
