package haven;

public class FayteViewWnd extends Window {
    private static final String[] TERRAIN = {"Near", "Normal", "Far"};
    private static FayteViewWnd instance = null;
    private final Button terrain;
    private final Button fps;
    private final Button bgfps;
    private final HSlider scenery;
    private final HSlider other;
    private final Label scenerylbl;
    private final Label otherlbl;
    private final CheckBox grass;
    private final CheckBox outline;
    private final CheckBox[] hide = new CheckBox[5];
    private static final FayteConfig.BoolSetting[] HIDES = {
        FayteConfig.hideTrees,
        FayteConfig.hideBushes,
        FayteConfig.hideBoulders,
        FayteConfig.hideCrops,
        FayteConfig.hideFences
    };
    private static final String[] HIDENAMES = {"Trees, logs & stumps", "Bushes", "Boulders", "Crops", "Fences & walls"};

    private FayteViewWnd(Widget parent) {
        super(
                new Coord(FayteSkin.s(200), FayteSkin.s(120)),
                new Coord(FayteSkin.s(360), FayteSkin.s(400)),
                parent,
                "View & Performance");
        justclose = true;
        int y = 0;
        new Label(new Coord(0, y), this, "Presets:");
        new Button(new Coord(FayteSkin.s(70), y - FayteSkin.s(2)), FayteSkin.s(90), this, "Quality") {
            public void click() {
                FayteViewWnd.this.preset(3, true, 0, 0, "60");
            }
        };
        new Button(new Coord(FayteSkin.s(165), y - FayteSkin.s(2)), FayteSkin.s(90), this, "Balanced") {
            public void click() {
                FayteViewWnd.this.preset(2, true, 60, 0, "50");
            }
        };
        new Button(new Coord(FayteSkin.s(260), y - FayteSkin.s(2)), FayteSkin.s(90), this, "Performance") {
            public void click() {
                FayteViewWnd.this.preset(1, false, 30, 60, "30");
            }
        };
        y += FayteSkin.s(34);
        new Label(new Coord(0, y + FayteSkin.s(4)), this, "Terrain distance:");
        terrain = new Button(new Coord(FayteSkin.s(150), y), FayteSkin.s(120), this, "") {
            public void click() {
                int t = FayteConfig.viewTerrain.get() % 3 + 1;
                FayteConfig.viewTerrain.set(t);
                FayteViewWnd.this.sync();
            }
        };
        y += FayteSkin.s(30);
        grass = new CheckBox(new Coord(0, y), this, "Grass & ground clutter") {
            public void changed(boolean val) {
                FayteConfig.viewGrass.set(val);
            }
        };
        y += FayteSkin.s(28);
        scenerylbl = new Label(new Coord(0, y), this, "");
        y += FayteSkin.s(16);
        scenery = new HSlider(new Coord(0, y), FayteSkin.s(340), this, 0, 150, FayteConfig.distScenery.get()) {
            public void changed() {
                FayteConfig.distScenery.set(val);
                FayteViewWnd.this.labels();
            }
        };
        y += FayteSkin.s(22);
        otherlbl = new Label(new Coord(0, y), this, "");
        y += FayteSkin.s(16);
        other = new HSlider(new Coord(0, y), FayteSkin.s(340), this, 0, 150, FayteConfig.distOther.get()) {
            public void changed() {
                FayteConfig.distOther.set(val);
                FayteViewWnd.this.labels();
            }
        };
        y += FayteSkin.s(22);
        new Label(new Coord(0, y), this, "People and creatures are always shown.");
        y += FayteSkin.s(28);
        new Label(new Coord(0, y), this, "Hide (a faint ring stays on the ground):");
        y += FayteSkin.s(20);
        for (int i = 0; i < HIDES.length; i++) {
            final int k = i;
            hide[i] =
                    new CheckBox(
                            new Coord((i % 2) * FayteSkin.s(180), y + (i / 2) * FayteSkin.s(24)), this, HIDENAMES[i]) {
                        public void changed(boolean val) {
                            HIDES[k].set(val);
                        }
                    };
        }
        y += 3 * FayteSkin.s(24) + FayteSkin.s(6);
        outline = new CheckBox(new Coord(0, y), this, "Footprint rings under all objects") {
            public void changed(boolean val) {
                FayteConfig.outlineAll.set(val);
            }
        };
        y += FayteSkin.s(32);
        new Label(new Coord(0, y + FayteSkin.s(4)), this, "Frame-rate cap:");
        fps = new Button(new Coord(FayteSkin.s(150), y), FayteSkin.s(100), this, "") {
            public void click() {
                cycle(FayteConfig.fpsCap);
                FayteViewWnd.this.sync();
            }
        };
        y += FayteSkin.s(30);
        new Label(new Coord(0, y + FayteSkin.s(4)), this, "When in background:");
        bgfps = new Button(new Coord(FayteSkin.s(150), y), FayteSkin.s(100), this, "") {
            public void click() {
                cycle(FayteConfig.fpsBackground);
                FayteViewWnd.this.sync();
            }
        };
        sync();
        pack();
    }

    private static void cycle(FayteConfig.ChoiceSetting s) {
        String[] ch = s.choices;
        String cur = s.get();
        for (int i = 0; i < ch.length; i++) {
            if (ch[i].equals(cur)) {
                s.set(ch[(i + 1) % ch.length]);
                return;
            }
        }
        s.set(ch[0]);
    }

    private void preset(int terrain, boolean grass, int scenery, int other, String fps) {
        FayteConfig.viewTerrain.set(terrain);
        FayteConfig.viewGrass.set(grass);
        FayteConfig.distScenery.set(scenery);
        FayteConfig.distOther.set(other);
        FayteConfig.fpsCap.set(fps);
        sync();
    }

    private static String dist(int v) {
        return v == 0 ? "no limit" : v + " tiles";
    }

    private void labels() {
        scenerylbl.settext("Trees, bushes & boulders drawn up to: " + dist(FayteConfig.distScenery.get()));
        otherlbl.settext("Other objects drawn up to: " + dist(FayteConfig.distOther.get()));
    }

    private void sync() {
        terrain.change(TERRAIN[FayteConfig.viewTerrain.get() - 1]);
        grass.a = FayteConfig.viewGrass.get();
        scenery.val = FayteConfig.distScenery.get();
        other.val = FayteConfig.distOther.get();
        for (int i = 0; i < HIDES.length; i++) {
            hide[i].a = HIDES[i].get();
        }
        outline.a = FayteConfig.outlineAll.get();
        String f = FayteConfig.fpsCap.get();
        fps.change("unlimited".equals(f) ? "Unlimited" : f + " fps");
        String b = FayteConfig.fpsBackground.get();
        bgfps.change("same".equals(b) ? "Same" : b + " fps");
        labels();
    }

    public static FayteViewWnd embedded(Widget parent, Coord c) {
        FayteViewWnd w = new FayteViewWnd(parent);
        w.embed();
        w.c = c;
        return w;
    }

    public static void toggle(GameUI gui) {
        if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
        } else if (gui != null) {
            instance = new FayteViewWnd(gui);
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
