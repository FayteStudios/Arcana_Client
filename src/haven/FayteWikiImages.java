package haven;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FayteWikiImages {
    private static final long SPACING = 1000L;

    public static File dir() {
        return new File(FaytePaths.fayte(), "wiki_images");
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", FayteWikiData.AGENT);
        int code = c.getResponseCode();
        if (code != 200) {
            throw new Exception("HTTP " + code + " for " + url);
        }
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[16384];

            int n;
            while ((n = in.read(b)) > 0) {
                buf.write(b, 0, n);
            }
            return buf.toByteArray();
        }
    }

    public static class Image {
        public final String name;
        public final String url;
        public final long size;

        Image(String name, String url, long size) {
            this.name = name;
            this.url = url;
            this.size = size;
        }
    }

    public static List<FayteWikiImages.Image> list() throws Exception {
        List<FayteWikiImages.Image> ret = new ArrayList<>();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("action", "query");
        p.put("list", "allimages");
        p.put("ailimit", "500");
        p.put("aiprop", "url|size");
        p.put("format", "json");

        while (true) {
            StringBuilder q = new StringBuilder();

            for (Map.Entry<String, String> e : p.entrySet()) {
                q.append(q.length() == 0 ? "?" : "&")
                        .append(e.getKey())
                        .append('=')
                        .append(URLEncoder.encode(e.getValue(), "UTF-8"));
            }
            JsonObject o = new JsonParser()
                    .parse(new String(get(FayteWikiData.API + q), StandardCharsets.UTF_8))
                    .getAsJsonObject();

            for (JsonElement ie : o.getAsJsonObject("query").getAsJsonArray("allimages")) {
                JsonObject im = ie.getAsJsonObject();
                ret.add(new FayteWikiImages.Image(
                        im.get("name").getAsString(),
                        im.get("url").getAsString(),
                        im.has("size") ? im.get("size").getAsLong() : -1L));
            }
            if (!o.has("continue")) {
                break;
            }
            for (Map.Entry<String, JsonElement> c :
                    o.getAsJsonObject("continue").entrySet()) {
                p.put(c.getKey(), c.getValue().getAsString());
            }
            Thread.sleep(SPACING);
        }
        return ret;
    }

    public static void main(String[] args) throws Exception {
        File out = args.length > 0 ? new File(args[0]) : dir();
        out.mkdirs();
        List<FayteWikiImages.Image> all = list();
        long total = 0L;

        for (FayteWikiImages.Image im : all) {
            total += Math.max(0L, im.size);
        }
        System.out.println(all.size() + " images, " + total / 1000000L + " MB, into " + out);
        int done = 0;
        int skipped = 0;
        int failed = 0;
        long last = 0L;

        for (FayteWikiImages.Image im : all) {
            File f = new File(out, im.name);
            if (f.exists() && (im.size < 0 || f.length() == im.size)) {
                skipped++;
                continue;
            }
            long wait = last + SPACING - System.currentTimeMillis();
            if (wait > 0L) {
                Thread.sleep(wait);
            }
            last = System.currentTimeMillis();

            try {
                byte[] data = get(im.url);
                File tmp = new File(out, im.name + ".part");
                try (OutputStream os = new FileOutputStream(tmp)) {
                    os.write(data);
                }
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                done++;
            } catch (Exception e) {
                failed++;
                System.out.println("failed " + im.name + ": " + e.getMessage());
            }
            if ((done + failed) % 100 == 0) {
                System.out.println("progress: " + done + " downloaded, " + skipped + " already there, " + failed
                        + " failed, of " + all.size());
            }
        }
        System.out.println("finished: " + done + " downloaded, " + skipped + " already there, " + failed
                + " failed, of " + all.size());
    }
}
