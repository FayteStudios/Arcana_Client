package fayte.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import haven.FayteEntries;
import haven.FayteIconLibrary;
import haven.FayteLog;
import haven.FaytePaths;
import haven.FayteWikiData;
import haven.FayteWorldNames;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class GameCatalog {
    public static class Thing {
        public final String key;
        public final String kind;
        public final String label;
        public String target;
        public boolean alias;

        Thing(String key, String kind, String label) {
            this.key = key;
            this.kind = kind;
            this.label = label;
        }

        public String iconfile() {
            return key.startsWith("gfx/invobjs/") || key.startsWith("paginae/") ? key : null;
        }
    }

    public final List<GameCatalog.Thing> things = new ArrayList<>();
    private final Map<String, List<GameCatalog.Thing>> bytarget = new HashMap<>();

    private static String kindof(String res) {
        if (res.startsWith("gfx/invobjs/")) {
            return "item";
        } else if (res.startsWith("gfx/kritter/")) {
            return "creature";
        } else if (res.startsWith("gfx/terobjs/")) {
            return "world object";
        } else if (res.startsWith("paginae/craft/") || res.startsWith("paginae/bld/")) {
            return "recipe";
        } else if (res.startsWith("paginae/")) {
            return "menu entry";
        } else {
            return null;
        }
    }

    private static void walk(File dir, String prefix, Map<String, String> into) {
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isDirectory()) {
                    walk(f, prefix + f.getName() + "/", into);
                } else if (f.getName().endsWith(".cached")) {
                    String res = prefix + f.getName().substring(0, f.getName().length() - 7);
                    String k = kindof(res);
                    if (k != null) {
                        into.putIfAbsent(res, k);
                    }
                }
            }
        }
    }

    public static GameCatalog build() {
        Map<String, String> res = new LinkedHashMap<>();
        File bin = new File(FaytePaths.home(), "bin");

        for (String jn : new String[] {"salem-res.jar", "builtin-res.jar", "lclient-res.jar"}) {
            File jf = new File(bin, jn);
            if (jf.exists()) {
                try (JarFile jar = new JarFile(jf)) {
                    Enumeration<JarEntry> en = jar.entries();

                    while (en.hasMoreElements()) {
                        String n = en.nextElement().getName();
                        if (n.startsWith("res/") && n.endsWith(".res")) {
                            String r = n.substring(4, n.length() - 4);
                            String k = kindof(r);
                            if (k != null) {
                                res.putIfAbsent(r, k);
                            }
                        }
                    }
                } catch (Exception e) {
                    FayteLog.once("GameCatalog.build", e);
                }
            }
        }
        File cr = FayteIconLibrary.cacheroot();
        File[] caches = cr == null ? null : cr.listFiles();
        if (caches != null) {
            for (File c : caches) {
                File rd = new File(c, "res");
                if (rd.isDirectory()) {
                    walk(rd, "", res);
                }
            }
        }
        Map<String, String> names = FayteIconLibrary.index();
        File ad = new File(FaytePaths.fayte(), "almanac");
        File[] servers = ad.listFiles();
        if (servers != null) {
            for (File sv : servers) {
                File[] books = sv.listFiles((d, n) -> n.endsWith(".json"));
                if (books != null) {
                    for (File b : books) {
                        try {
                            JsonObject o = new JsonParser()
                                    .parse(new String(Files.readAllBytes(b.toPath()), StandardCharsets.UTF_8))
                                    .getAsJsonObject();
                            for (Map.Entry<String, JsonElement> r :
                                    o.getAsJsonObject("recs").entrySet()) {
                                JsonObject rec = r.getValue().getAsJsonObject();
                                if (rec.has("icon")
                                        && rec.has("name")
                                        && rec.get("icon").getAsString().startsWith("gfx/")) {
                                    names.put(
                                            rec.get("icon").getAsString(),
                                            rec.get("name").getAsString());
                                    res.putIfAbsent(
                                            rec.get("icon").getAsString(),
                                            kindof(rec.get("icon").getAsString()) != null
                                                    ? kindof(rec.get("icon").getAsString())
                                                    : "item");
                                }
                            }
                        } catch (Exception e) {
                            FayteLog.once("GameCatalog.build", e);
                        }
                    }
                }
            }
        }
        GameCatalog cat = new GameCatalog();

        for (Map.Entry<String, String> e : res.entrySet()) {
            String r = e.getKey();
            String kind = e.getValue();
            String label = names.get(r);
            if (label == null) {
                label = kind.equals("item") || kind.startsWith("recipe") || kind.startsWith("menu")
                        ? null
                        : FayteWorldNames.display(r);
                if (kind.equals("world object")) {
                    String built = names.get("paginae/bld/" + r.substring(r.lastIndexOf('/') + 1));
                    if (built != null) {
                        label = built;
                    }
                }
            }
            if (label == null) {
                String base = r.substring(r.lastIndexOf('/') + 1);
                label = Character.toUpperCase(base.charAt(0)) + base.substring(1);
            }
            cat.things.add(new GameCatalog.Thing(r, kind, label));
        }
        cat.resolve();
        return cat;
    }

    public static String resolve(GameCatalog.Thing t) {
        FayteWikiData.Entry e;
        if (t.kind.equals("creature") || t.kind.equals("world object")) {
            e = FayteWorldNames.lookup(t.key);
        } else if (FayteEntries.alias(t.key) != null) {
            e = FayteWikiData.find(t.key);
        } else {
            e = FayteWikiData.find(t.label);
        }
        return e == null ? null : e.title;
    }

    public void resolve() {
        bytarget.clear();

        for (GameCatalog.Thing t : things) {
            t.target = resolve(t);
            t.alias = FayteEntries.alias(t.key) != null;
            if (t.target != null) {
                bytarget.computeIfAbsent(t.target.toLowerCase(), k -> new ArrayList<>())
                        .add(t);
            }
        }
    }

    public List<GameCatalog.Thing> linkedto(String title) {
        if (title == null) {
            return new ArrayList<>();
        }
        FayteWikiData.Entry e = FayteWikiData.find(title);
        String t = e != null ? e.title : title;
        List<GameCatalog.Thing> ret = bytarget.get(t.toLowerCase());
        return ret == null ? new ArrayList<>() : ret;
    }
}
