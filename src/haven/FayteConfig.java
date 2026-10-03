package haven;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class FayteConfig {
    public static final String FILE_NAME = "fayte_salem.cfg";
    private static final Properties props = new Properties();
    private static final Map<String, FayteConfig.Setting<?>> settings = new LinkedHashMap<>();

    public static final FayteConfig.DoubleSetting uiScale =
            add(new FayteConfig.DoubleSetting("ui_scale", 1.0, 1.0, 3.0));
    public static final FayteConfig.BoolSetting minimapMovable =
            add(new FayteConfig.BoolSetting("minimap_movable", false));
    public static final FayteConfig.CoordSetting minimapPos = add(new FayteConfig.CoordSetting("minimap_pos"));
    public static final FayteConfig.BoolSetting minimapLocked =
            add(new FayteConfig.BoolSetting("minimap_locked", false));
    public static final FayteConfig.BoolSetting worldmapEnabled =
            add(new FayteConfig.BoolSetting("worldmap_enabled", true));
    public static final FayteConfig.CoordSetting worldmapCenter = add(new FayteConfig.CoordSetting("worldmap_center"));
    public static final FayteConfig.DoubleSetting worldmapZoom =
            add(new FayteConfig.DoubleSetting("worldmap_zoom", 1.0, 0.25, 4.0));
    public static final FayteConfig.ChoiceSetting uiStyle =
            add(new FayteConfig.ChoiceSetting("ui_style", "classic", "classic", "fayte"));
    public static final FayteConfig.IntSetting actionGridCols =
            add(new FayteConfig.IntSetting("action_grid_cols", 4, 1, 16));
    public static final FayteConfig.IntSetting actionGridRows =
            add(new FayteConfig.IntSetting("action_grid_rows", 4, 1, 16));
    public static final FayteConfig.CoordSetting actionGridPos = add(new FayteConfig.CoordSetting("action_grid_pos"));
    public static final FayteConfig.IntSetting buttonPanelCols =
            add(new FayteConfig.IntSetting("button_panel_cols", 8, 1, 32));
    public static final FayteConfig.CoordSetting buttonPanelPos = add(new FayteConfig.CoordSetting("button_panel_pos"));
    public static final FayteConfig.BoolSetting windowSnap = add(new FayteConfig.BoolSetting("window_snap", false));
    public static final FayteConfig.IntSetting snapGrid = add(new FayteConfig.IntSetting("snap_grid", 10, 2, 100));
    public static final FayteConfig.IntSetting snapDist = add(new FayteConfig.IntSetting("snap_distance", 8, 0, 50));
    public static final FayteConfig.BoolSetting classicChat = add(new FayteConfig.BoolSetting("classic_chat", true));
    public static final FayteConfig.BoolSetting storeButton = add(new FayteConfig.BoolSetting("store_button", true));
    public static final FayteConfig.BoolSetting lockUi = add(new FayteConfig.BoolSetting("lock_ui", true));
    public static final FayteConfig.BoolSetting wikiButton = add(new FayteConfig.BoolSetting("wiki_button", true));
    public static final FayteConfig.BoolSetting moduleStyle = add(new FayteConfig.BoolSetting("module_fayte_ui", true));
    public static final FayteConfig.BoolSetting moduleKeys = add(new FayteConfig.BoolSetting("module_keybinds", true));
    public static final FayteConfig.BoolSetting moduleChat =
            add(new FayteConfig.BoolSetting("module_chat_windows", true));
    public static final FayteConfig.BoolSetting moduleContainers =
            add(new FayteConfig.BoolSetting("module_containers", true));
    public static final FayteConfig.BoolSetting moduleAlmanac =
            add(new FayteConfig.BoolSetting("module_almanac", true));
    public static final FayteConfig.BoolSetting moduleView = add(new FayteConfig.BoolSetting("module_view", true));
    public static final FayteConfig.BoolSetting moduleSelections =
            add(new FayteConfig.BoolSetting("module_selections", true));
    public static final FayteConfig.BoolSetting moduleTimers = add(new FayteConfig.BoolSetting("module_timers", true));
    public static final FayteConfig.BoolSetting moduleTools = add(new FayteConfig.BoolSetting("module_tools", true));
    public static final FayteConfig.BoolSetting toolsFill = add(new FayteConfig.BoolSetting("tools_fill", true));
    public static final FayteConfig.BoolSetting toolsFeed = add(new FayteConfig.BoolSetting("tools_feed", true));
    public static final FayteConfig.BoolSetting toolsSwap = add(new FayteConfig.BoolSetting("tools_swap", true));
    public static final FayteConfig.BoolSetting openGlow = add(new FayteConfig.BoolSetting("open_glow", true));
    public static final FayteConfig.BoolSetting timersPerChar =
            add(new FayteConfig.BoolSetting("timers_per_char", true));
    public static final FayteConfig.BoolSetting autoAttack = add(new FayteConfig.BoolSetting("auto_attack", true));
    public static final FayteConfig.DoubleSetting almanacIcons =
            add(new FayteConfig.DoubleSetting("almanac_icons", 1.0, 0.0, 2.0));
    public static final FayteConfig.DoubleSetting tooltipDelay =
            add(new FayteConfig.DoubleSetting("tooltip_delay", 2.0, 0.0, 3.0));
    public static final FayteConfig.DoubleSetting tooltipScale =
            add(new FayteConfig.DoubleSetting("tooltip_scale", 1.0, 1.0, 2.0));
    public static final FayteConfig.BoolSetting diagnostics = add(new FayteConfig.BoolSetting("diagnostics", false));
    public static final FayteConfig.BoolSetting autoTimers = add(new FayteConfig.BoolSetting("auto_timers", true));
    public static final FayteConfig.BoolSetting mapPerChar = add(new FayteConfig.BoolSetting("map_per_char", false));
    public static final FayteConfig.BoolSetting markersPerChar =
            add(new FayteConfig.BoolSetting("markers_per_char", false));
    public static final FayteConfig.BoolSetting miniFill = add(new FayteConfig.BoolSetting("mini_fill", true));
    public static final FayteConfig.BoolSetting menuArrange = add(new FayteConfig.BoolSetting("menu_arrange", false));
    public static final FayteConfig.BoolSetting stationRecipes =
            add(new FayteConfig.BoolSetting("station_recipes", true));
    public static final FayteConfig.BoolSetting smartAny = add(new FayteConfig.BoolSetting("smart_any", false));
    public static final FayteConfig.BoolSetting swimMode = add(new FayteConfig.BoolSetting("swim_mode", true));
    public static final FayteConfig.BoolSetting doorsBuilding =
            add(new FayteConfig.BoolSetting("doors_building", true));
    public static final FayteConfig.BoolSetting doorsGates = add(new FayteConfig.BoolSetting("doors_gates", true));
    public static final FayteConfig.BoolSetting smartHerbs = add(new FayteConfig.BoolSetting("smart_herbs", true));
    public static final FayteConfig.BoolSetting smartItems = add(new FayteConfig.BoolSetting("smart_items", true));
    public static final FayteConfig.BoolSetting smartCrops = add(new FayteConfig.BoolSetting("smart_crops", false));
    public static final FayteConfig.BoolSetting smartBushes = add(new FayteConfig.BoolSetting("smart_bushes", false));
    public static final FayteConfig.BoolSetting smartTrees = add(new FayteConfig.BoolSetting("smart_trees", false));
    public static final FayteConfig.BoolSetting smartBoulders =
            add(new FayteConfig.BoolSetting("smart_boulders", false));
    public static final FayteConfig.BoolSetting smartAnimals = add(new FayteConfig.BoolSetting("smart_animals", false));
    public static final FayteConfig.BoolSetting smartGround = add(new FayteConfig.BoolSetting("smart_ground", true));
    public static final FayteConfig.IntSetting smartRadius = add(new FayteConfig.IntSetting("smart_radius", 8, 1, 30));
    public static final FayteConfig.IntSetting viewTerrain = add(new FayteConfig.IntSetting("view_terrain", 2, 1, 3));
    public static final FayteConfig.BoolSetting viewGrass = add(new FayteConfig.BoolSetting("view_grass", true));
    public static final FayteConfig.IntSetting distScenery = add(new FayteConfig.IntSetting("dist_scenery", 0, 0, 150));
    public static final FayteConfig.IntSetting distOther = add(new FayteConfig.IntSetting("dist_other", 0, 0, 150));
    public static final FayteConfig.BoolSetting hideTrees = add(new FayteConfig.BoolSetting("hide_trees", false));
    public static final FayteConfig.BoolSetting hideBushes = add(new FayteConfig.BoolSetting("hide_bushes", false));
    public static final FayteConfig.BoolSetting hideBoulders = add(new FayteConfig.BoolSetting("hide_boulders", false));
    public static final FayteConfig.BoolSetting hideCrops = add(new FayteConfig.BoolSetting("hide_crops", false));
    public static final FayteConfig.BoolSetting hideFences = add(new FayteConfig.BoolSetting("hide_fences", false));
    public static final FayteConfig.BoolSetting outlineAll = add(new FayteConfig.BoolSetting("outline_all", false));
    public static final FayteConfig.ChoiceSetting fpsCap =
            add(new FayteConfig.ChoiceSetting("fps_cap", "50", "30", "50", "60", "120", "unlimited"));
    public static final FayteConfig.ChoiceSetting fpsBackground =
            add(new FayteConfig.ChoiceSetting("fps_background", "same", "5", "10", "20", "same"));

    private static <S extends FayteConfig.Setting<?>> S add(S s) {
        settings.put(s.key, s);
        return s;
    }

    public static Collection<FayteConfig.Setting<?>> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(settings.values()));
    }

    public static FayteConfig.Setting<?> byKey(String key) {
        return settings.get(key);
    }

    public static File file() {
        return Config.getFile(FILE_NAME);
    }

    public static synchronized void load() {
        props.clear();
        File f = file();
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                props.load(in);
            } catch (Exception e) {
                System.out.println("Could not read " + f + ": " + e);
                props.clear();
                try {
                    Files.copy(
                            f.toPath(), new File(f.getPath() + ".bad").toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e2) {
                    System.out.println("Could not back up " + f + ": " + e2);
                }
            }
        }
        for (FayteConfig.Setting<?> s : settings.values()) {
            s.reload();
        }
    }

    public static synchronized boolean save() {
        File f = file();
        File tmp = new File(f.getPath() + ".tmp");
        try {
            try (OutputStream out = new FileOutputStream(tmp)) {
                props.store(out, "Fayte Salem settings");
            }
            try {
                Files.move(
                        tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            System.out.println("Could not write " + f + ": " + e);
            tmp.delete();
            return false;
        }
    }

    public abstract static class Setting<T> {
        public final String key;
        public final T def;
        private volatile T value;

        protected Setting(String key, T def) {
            this.key = key;
            this.def = def;
            value = def;
        }

        protected abstract T parse(String raw);

        protected abstract String format(T val);

        public T get() {
            return value;
        }

        public boolean isDefault() {
            synchronized (FayteConfig.class) {
                return props.getProperty(key) == null;
            }
        }

        void reload() {
            String raw = props.getProperty(key);
            T parsed = null;
            if (raw != null) {
                try {
                    parsed = parse(raw.trim());
                } catch (RuntimeException e) {
                    parsed = null;
                }
            }
            value = parsed != null ? parsed : def;
        }

        public boolean set(T val) {
            synchronized (FayteConfig.class) {
                if (val == null) {
                    props.remove(key);
                } else {
                    props.setProperty(key, format(val));
                }
                reload();
                return save();
            }
        }

        public boolean setString(String raw) {
            T parsed;
            try {
                parsed = parse(raw.trim());
            } catch (RuntimeException e) {
                parsed = null;
            }
            return parsed != null && set(parsed);
        }

        public boolean reset() {
            return set(null);
        }

        public String display() {
            T v = get();
            return v == null ? "(unset)" : format(v);
        }

        public String displayDefault() {
            return def == null ? "(unset)" : format(def);
        }
    }

    public static class BoolSetting extends FayteConfig.Setting<Boolean> {
        public BoolSetting(String key, boolean def) {
            super(key, def);
        }

        @Override
        protected Boolean parse(String raw) {
            if (raw.equalsIgnoreCase("true")) {
                return true;
            } else {
                return raw.equalsIgnoreCase("false") ? false : null;
            }
        }

        @Override
        protected String format(Boolean val) {
            return val ? "true" : "false";
        }
    }

    public static class DoubleSetting extends FayteConfig.Setting<Double> {
        public final double min;
        public final double max;

        public DoubleSetting(String key, double def, double min, double max) {
            super(key, def);
            this.min = min;
            this.max = max;
        }

        @Override
        protected Double parse(String raw) {
            double v = Double.parseDouble(raw);
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                return null;
            } else {
                return Math.max(min, Math.min(max, v));
            }
        }

        @Override
        protected String format(Double val) {
            return Double.toString(val);
        }
    }

    public static class IntSetting extends FayteConfig.Setting<Integer> {
        public final int min;
        public final int max;

        public IntSetting(String key, int def, int min, int max) {
            super(key, def);
            this.min = min;
            this.max = max;
        }

        @Override
        protected Integer parse(String raw) {
            return Math.max(min, Math.min(max, Integer.parseInt(raw)));
        }

        @Override
        protected String format(Integer val) {
            return Integer.toString(val);
        }
    }

    public static class ChoiceSetting extends FayteConfig.Setting<String> {
        public final String[] choices;

        public ChoiceSetting(String key, String def, String... choices) {
            super(key, def);
            this.choices = choices;
        }

        @Override
        protected String parse(String raw) {
            for (String c : choices) {
                if (c.equalsIgnoreCase(raw)) {
                    return c;
                }
            }
            return null;
        }

        @Override
        protected String format(String val) {
            return val;
        }
    }

    public static class CoordSetting extends FayteConfig.Setting<Coord> {
        public CoordSetting(String key) {
            super(key, null);
        }

        @Override
        protected Coord parse(String raw) {
            String s = raw;
            if (s.startsWith("(") && s.endsWith(")")) {
                s = s.substring(1, s.length() - 1);
            }
            String[] parts = s.split(",");
            return parts.length != 2
                    ? null
                    : new Coord(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        }

        @Override
        protected String format(Coord val) {
            return val.x + "," + val.y;
        }
    }
}
