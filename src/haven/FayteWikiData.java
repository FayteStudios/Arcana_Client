package haven;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class FayteWikiData {
    public static final String HOST = "https://nh.salemthegame.wiki";
    public static final String API = HOST + "/api.php";
    public static final String PAGE = HOST + "/page/";
    public static final String FILE_NAME = "wiki_data.json.gz";
    public static final String AGENT =
            "FayteSalemClient/1.0 (personal Salem client; wiki snapshot for in-game item info)";
    private static final long CHECK_EVERY = 24L * 60L * 60L * 1000L;
    private static final long SPACING = 1000L;
    private static volatile FayteWikiData.Store store = null;
    private static volatile boolean started = false;
    private static long lastreq = 0L;

    public static class Section {
        public String h;
        public List<String> paras = new ArrayList<>();
    }

    public static class Entry {
        public String title;
        public String ts;
        public String summary;
        public String image;
        public String imagesize;
        public boolean custom;
        public List<String> cats = new ArrayList<>();
        public Map<String, Map<String, String>> tpl = new LinkedHashMap<>();
        public List<FayteWikiData.Section> sections = new ArrayList<>();

        public String url() {
            try {
                return PAGE
                        + URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("%2F", "/");
            } catch (Exception e) {
                return PAGE + title.replace(' ', '_');
            }
        }
    }

    public static class Raw {
        public String ts;
        public String text;

        Raw(String ts, String text) {
            this.ts = ts;
            this.text = text;
        }
    }

    public static class Store {
        public String fetched;
        public long checked;
        public Map<String, FayteWikiData.Raw> pages = new TreeMap<>();
        public Map<String, String> redirects = new TreeMap<>();
        transient Map<String, FayteWikiData.Entry> entries;
        transient Map<String, String> index;
        transient Map<String, String> squash;

        public Map<String, FayteWikiData.Entry> entries() {
            return entries;
        }

        void reindex() {
            FayteWikiQuery.use(pages);
            Map<String, FayteWikiData.Entry> ents = new TreeMap<>();

            for (Map.Entry<String, FayteWikiData.Raw> p : pages.entrySet()) {
                try {
                    ents.put(p.getKey(), FayteWikiText.parse(p.getKey(), p.getValue().text, p.getValue().ts));
                } catch (RuntimeException e) {
                    System.out.println("Could not parse wiki page " + p.getKey() + ": " + e);
                }
            }
            entries = ents;
            Map<String, String> idx = new HashMap<>();

            for (String t : entries.keySet()) {
                idx.put(norm(t), t);
            }
            for (Map.Entry<String, String> r : redirects.entrySet()) {
                idx.putIfAbsent(norm(r.getKey()), r.getValue());
            }
            index = idx;
            Map<String, String> sq = new HashMap<>();
            for (String t : entries.keySet()) {
                sq.putIfAbsent(squash(t), t);
            }
            squash = sq;
        }

        public FayteWikiData.Entry find(String name) {
            if (name == null || index == null) {
                return null;
            }
            String t = index.get(norm(name));
            if (t == null && squash != null) {
                t = squash.get(squash(name));
            }
            for (int i = 0; t != null && entries != null && i < 4; i++) {
                FayteWikiData.Entry e = entries.get(t);
                if (e != null) {
                    return e;
                }
                String next = redirects.get(t);
                t = next != null ? next : index.get(norm(t));
            }
            return null;
        }
    }

    public static String squash(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public static String norm(String s) {
        return s.replace('_', ' ').trim().toLowerCase();
    }

    public static FayteWikiData.Store get() {
        return store;
    }

    public static void use(FayteWikiData.Store s) {
        store = s;
    }

    public static FayteWikiData.Entry find(String name) {
        if (name == null) {
            return null;
        }
        String a = FayteEntries.alias(name);
        if (a != null) {
            name = a;
        }
        FayteWikiData.Entry c = FayteEntries.custom(name);
        if (c != null) {
            return c;
        }
        FayteWikiData.Store s = store;
        FayteWikiData.Entry e = s == null ? null : s.find(name);
        if (e != null) {
            FayteWikiData.Entry c2 = FayteEntries.custom(e.title);
            if (c2 != null) {
                return c2;
            }
            if (FayteEntries.hidden(e.title)) {
                return null;
            }
        }
        return e;
    }

    public static File userfile() {
        return new File(FaytePaths.fayte(), FILE_NAME);
    }

    private static Gson gson() {
        return new GsonBuilder().disableHtmlEscaping().create();
    }

    public static FayteWikiData.Store read(InputStream in) throws Exception {
        try (Reader r = new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8)) {
            FayteWikiData.Store s = gson().fromJson(r, FayteWikiData.Store.class);
            s.reindex();
            return s;
        }
    }

    public static void write(FayteWikiData.Store s, File f) throws Exception {
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".tmp");

        try (Writer w =
                new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(tmp)), StandardCharsets.UTF_8)) {
            gson().toJson(s, w);
        }
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static String peekfetched(InputStream in) {
        try (InputStream gz = new GZIPInputStream(in)) {
            byte[] b = new byte[200];
            int n = 0;
            while (n < b.length) {
                int r = gz.read(b, n, b.length - n);
                if (r < 0) {
                    break;
                }
                n += r;
            }
            Matcher m =
                    Pattern.compile("\"fetched\":\"([^\"]*)\"").matcher(new String(b, 0, n, StandardCharsets.UTF_8));
            return m.find() ? m.group(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static FayteWikiData.Store loadbest() {
        File uf = userfile();
        String bf = null, ufd = null;
        try (InputStream in = FayteWikiData.class.getResourceAsStream("/fayte/" + FILE_NAME)) {
            if (in != null) {
                bf = peekfetched(in);
            }
        } catch (Exception e) {
            FayteLog.once("FayteWikiData.loadbest", e);
        }
        if (uf.exists()) {
            try (InputStream in = new FileInputStream(uf)) {
                ufd = peekfetched(in);
            } catch (Exception e) {
                FayteLog.once("FayteWikiData.loadbest", e);
            }
        }
        boolean preferuser = uf.exists() && (bf == null || (ufd != null && ufd.compareTo(bf) >= 0));
        if (preferuser) {
            try (InputStream in = new FileInputStream(uf)) {
                return read(in);
            } catch (Exception e) {
                System.out.println("Could not read " + uf + ": " + e);
            }
        } else {
            try (InputStream in = FayteWikiData.class.getResourceAsStream("/fayte/" + FILE_NAME)) {
                if (in != null) {
                    return read(in);
                }
            } catch (Exception e) {
                System.out.println("Could not read bundled wiki data: " + e);
            }
        }
        return loadboth();
    }

    private static FayteWikiData.Store loadboth() {
        FayteWikiData.Store bundled = null;
        FayteWikiData.Store user = null;

        try (InputStream in = FayteWikiData.class.getResourceAsStream("/fayte/" + FILE_NAME)) {
            if (in != null) {
                bundled = read(in);
            }
        } catch (Exception e) {
            System.out.println("Could not read bundled wiki data: " + e);
        }
        File f = userfile();
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                user = read(in);
            } catch (Exception e) {
                System.out.println("Could not read " + f + ": " + e);
            }
        }
        if (user == null) {
            return bundled;
        } else if (bundled == null) {
            return user;
        } else {
            return user.fetched != null && bundled.fetched != null && user.fetched.compareTo(bundled.fetched) >= 0
                    ? user
                    : bundled;
        }
    }

    public static synchronized void startup() {
        if (!started) {
            started = true;
            FayteLog.log("Wiki data: loading");
            Thread t = new Thread(
                    () -> {
                        FayteWikiData.Store s;
                        try {
                            long t0 = System.currentTimeMillis();
                            s = loadbest();
                            store = s;
                            FayteLog.log("Wiki data: "
                                    + (s == null ? "none found" : s.entries.size() + " entries, fetched " + s.fetched)
                                    + " (" + (System.currentTimeMillis() - t0) + " ms)");
                        } catch (Throwable e) {
                            FayteLog.log("Wiki data: load failed", e);
                            return;
                        }
                        if (s != null && System.currentTimeMillis() - s.checked > CHECK_EVERY) {
                            try {
                                FayteWikiData.Store u = new FayteWikiData.Store();
                                u.fetched = s.fetched;
                                u.checked = s.checked;
                                u.pages.putAll(s.pages);
                                u.redirects.putAll(s.redirects);
                                int n = update(u);
                                u.checked = System.currentTimeMillis();
                                u.reindex();
                                write(u, userfile());
                                store = u;
                                FayteLog.log("Wiki data: updated, " + n + " changed pages");
                            } catch (Throwable e) {
                                FayteLog.log("Wiki data: update failed", e);
                            }
                        }
                    },
                    "Fayte wiki data");
            t.setDaemon(true);
            t.start();
        }
    }

    private static final Object apilock = new Object();

    private static JsonObject api(Map<String, String> params) throws Exception {
        synchronized (apilock) {
            return apilocked(params);
        }
    }

    private static JsonObject apilocked(Map<String, String> params) throws Exception {
        long wait = lastreq + SPACING - System.currentTimeMillis();
        if (wait > 0L) {
            Thread.sleep(wait);
        }
        StringBuilder q = new StringBuilder("format=json&formatversion=2&maxlag=5");

        for (Map.Entry<String, String> p : params.entrySet()) {
            q.append('&').append(p.getKey()).append('=').append(URLEncoder.encode(p.getValue(), "UTF-8"));
        }
        byte[] body = q.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", AGENT);
        c.setRequestProperty("Accept-Encoding", "gzip");
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        c.getOutputStream().write(body);
        lastreq = System.currentTimeMillis();
        int code = c.getResponseCode();
        if (code != 200) {
            throw new Exception("Wiki API returned HTTP " + code);
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (InputStream raw = c.getInputStream();
                InputStream in = "gzip".equalsIgnoreCase(c.getContentEncoding()) ? new GZIPInputStream(raw) : raw) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                buf.write(b, 0, n);
            }
        }
        JsonObject o = new JsonParser()
                .parse(new String(buf.toByteArray(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        if (o.has("error")) {
            throw new Exception("Wiki API error: " + o.get("error"));
        }
        return o;
    }

    private static Map<String, String> params(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();

        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static void addcontinue(Map<String, String> p, JsonObject o) {
        if (o.has("continue")) {
            for (Map.Entry<String, JsonElement> c :
                    o.getAsJsonObject("continue").entrySet()) {
                p.put(c.getKey(), c.getValue().getAsString());
            }
        }
    }

    private static int takepages(FayteWikiData.Store s, JsonObject o, Set<String> asked) {
        int n = 0;
        JsonObject q = o.getAsJsonObject("query");
        if (q == null) {
            return 0;
        }
        if (q.has("pages")) {
            for (JsonElement pe : q.getAsJsonArray("pages")) {
                JsonObject p = pe.getAsJsonObject();
                String title = p.get("title").getAsString();
                if (asked != null) {
                    asked.remove(title);
                }
                if (p.has("missing") || p.has("invalid")) {
                    s.pages.remove(title);
                    s.redirects.remove(title);
                    n++;
                } else if (p.has("revisions")) {
                    JsonObject rev = p.getAsJsonArray("revisions").get(0).getAsJsonObject();
                    String text;
                    if (rev.has("slots")) {
                        text = rev.getAsJsonObject("slots")
                                .getAsJsonObject("main")
                                .get("content")
                                .getAsString();
                    } else {
                        text = rev.get("content").getAsString();
                    }
                    String ts = rev.has("timestamp") ? rev.get("timestamp").getAsString() : null;
                    String target = FayteWikiText.redirect(text);
                    if (target != null) {
                        s.pages.remove(title);
                        s.redirects.put(title, target);
                    } else {
                        s.redirects.remove(title);
                        s.pages.put(title, new FayteWikiData.Raw(ts, text));
                    }
                    n++;
                }
            }
        }
        return n;
    }

    public static String now() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    public static FayteWikiData.Store snapshot() throws Exception {
        FayteWikiData.Store s = new FayteWikiData.Store();
        s.fetched = now();
        Map<String, String> p = params(
                "action",
                "query",
                "generator",
                "allpages",
                "gapnamespace",
                "0",
                "gaplimit",
                "50",
                "prop",
                "revisions",
                "rvprop",
                "content|timestamp",
                "rvslots",
                "main");

        while (true) {
            JsonObject o = api(p);
            takepages(s, o, null);
            System.out.println("Pages so far: " + s.pages.size() + " pages, " + s.redirects.size() + " redirects");
            if (!o.has("continue")) {
                break;
            }
            addcontinue(p, o);
        }
        s.checked = System.currentTimeMillis();
        s.reindex();
        return s;
    }

    public static int update(FayteWikiData.Store s) throws Exception {
        if (s.fetched == null) {
            return 0;
        }
        String start = now();
        Set<String> titles = new LinkedHashSet<>();
        Map<String, String> p = params(
                "action",
                "query",
                "list",
                "recentchanges",
                "rcnamespace",
                "0",
                "rclimit",
                "500",
                "rcprop",
                "title|loginfo",
                "rcdir",
                "newer",
                "rcstart",
                s.fetched);

        while (true) {
            JsonObject o = api(p);
            JsonArray rc = o.getAsJsonObject("query").getAsJsonArray("recentchanges");

            for (JsonElement ce : rc) {
                JsonObject c = ce.getAsJsonObject();
                titles.add(c.get("title").getAsString());
                if (c.has("logparams") && c.getAsJsonObject("logparams").has("target_title")) {
                    titles.add(
                            c.getAsJsonObject("logparams").get("target_title").getAsString());
                }
            }
            if (!o.has("continue")) {
                break;
            }
            addcontinue(p, o);
        }
        int n = 0;
        List<String> all = new ArrayList<>(titles);

        for (int i = 0; i < all.size(); i += 50) {
            List<String> batch = all.subList(i, Math.min(all.size(), i + 50));
            Set<String> asked = new LinkedHashSet<>(batch);
            JsonObject o = api(params(
                    "action",
                    "query",
                    "titles",
                    String.join("|", batch),
                    "prop",
                    "revisions",
                    "rvprop",
                    "content|timestamp",
                    "rvslots",
                    "main"));
            n += takepages(s, o, asked);
        }
        s.fetched = start;
        return n;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("usage: FayteWikiData <output.json.gz>");
            return;
        }
        FayteWikiData.Store s = snapshot();
        write(s, new File(args[0]).getAbsoluteFile());
        System.out.println("Wrote " + args[0] + ": " + s.pages.size() + " pages, " + s.redirects.size()
                + " redirects, fetched " + s.fetched);
    }
}
