package haven;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

public class FayteFeedbackWnd extends Window {
    private static Properties conf = null;
    private static long lastsent = 0L;

    private static synchronized String conf(String k) {
        if (conf == null) {
            conf = new Properties();
            try (InputStream in = FayteFeedbackWnd.class.getResourceAsStream("/fayte/feedback.properties")) {
                if (in != null) {
                    conf.load(in);
                }
            } catch (Exception e) {
                FayteLog.log("Feedback: could not read settings: " + e);
            }
        }
        return conf.getProperty(k, "").trim();
    }

    private static String to() {
        String e = conf("email");
        return e.isEmpty() ? "sorinf@faytestudios.com" : e;
    }

    private static final String[] KINDS = {"Bug", "Suggestion", "Question", "Other"};
    private static FayteFeedbackWnd instance = null;
    private final FayteTextArea text;
    private final CheckBox log;
    private int kind = 0;
    private final int chipy;

    public static void open(GameUI gui) {
        if (instance != null && instance.attached()) {
            instance.raise();
            return;
        }
        instance = new FayteFeedbackWnd(gui);
    }

    private FayteFeedbackWnd(GameUI gui) {
        super(
                new Coord(FayteSkin.s(160), FayteSkin.s(100)),
                new Coord(FayteSkin.s(440), FayteSkin.s(330)),
                gui,
                "Send feedback");
        justclose = true;
        int y = 0;
        new Label(new Coord(0, y), this, "What is it about?");
        y += FayteSkin.labelf.height() + 6;
        chipy = y;
        y += FayteSkin.s(26);
        new Label(new Coord(0, y), this, "Tell us what happened, or what you'd like:");
        y += FayteSkin.labelf.height() + 4;
        text = new FayteTextArea(new Coord(0, y), new Coord(asz.x, FayteSkin.s(170)), this);
        y += FayteSkin.s(176);
        log = new CheckBox(new Coord(0, y), this, "Include the last lines of the Arcana log (helps with bugs)");
        log.a = true;
        y += FayteSkin.s(26);
        new Button(new Coord(0, y), FayteSkin.s(150), this, conf("webhook").isEmpty() ? "Send by email" : "Send") {
            @Override
            public void click() {
                if (conf("webhook").isEmpty()) {
                    FayteFeedbackWnd.this.send();
                } else {
                    FayteFeedbackWnd.this.post();
                }
            }
        };
        new Button(new Coord(FayteSkin.s(160), y), FayteSkin.s(150), this, "Copy to clipboard") {
            @Override
            public void click() {
                FayteFeedbackWnd.this.copy();
            }
        };
    }

    private static final int[] COLORS = {0xD05048, 0x58A8E0, 0xC8AA62, 0x8C8C8C};

    private static String cut(String s, int n) {
        return s.length() > n ? s.substring(0, n - 12) + "\n(cut short)" : s;
    }

    private String embedjson() {
        String who = Config.currentCharName == null ? "?" : Config.currentCharName;
        String sys = "Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name");
        StringBuilder f = new StringBuilder();
        f.append("{\"name\":\"Character\",\"value\":").append(jstr(who)).append(",\"inline\":true}");
        f.append(",{\"name\":\"System\",\"value\":").append(jstr(sys)).append(",\"inline\":true}");
        if (log.a) {
            try {
                File lf = new File(FaytePaths.fayte(), "fayte.log");
                List<String> lines = Files.readAllLines(lf.toPath(), StandardCharsets.UTF_8);
                StringBuilder lb = new StringBuilder();
                for (int i = Math.max(0, lines.size() - 12); i < lines.size(); i++) {
                    String l = lines.get(i);
                    lb.append(l.length() > 160 ? l.substring(0, 160) : l).append("\n");
                }
                String logtxt = lb.toString();
                if (logtxt.length() > 990) {
                    logtxt = logtxt.substring(logtxt.length() - 990);
                }
                f.append(",{\"name\":\"Recent log\",\"value\":")
                        .append(jstr("```\n" + logtxt.replace("`", "'") + "```"))
                        .append(",\"inline\":false}");
            } catch (Exception e) {
                FayteLog.once("FayteFeedbackWnd.embedjson", e);
            }
        }
        String text = cut(this.text.text().trim(), 3800);
        return "{\"embeds\":[{\"title\":" + jstr(KINDS[kind] + " from " + who) + ",\"description\":" + jstr(text)
                + ",\"color\":" + COLORS[kind] + ",\"fields\":[" + f + "]}]}";
    }

    private String body() {
        StringBuilder sb = new StringBuilder();
        sb.append(text.text().trim()).append("\n\n");
        sb.append("---\n");
        sb.append("Character: ")
                .append(Config.currentCharName == null ? "?" : Config.currentCharName)
                .append("\n");
        sb.append("Java: ")
                .append(System.getProperty("java.version"))
                .append(", ")
                .append(System.getProperty("os.name"))
                .append("\n");
        if (log.a) {
            try {
                File f = new File(FaytePaths.fayte(), "fayte.log");
                List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                sb.append("\nRecent log:\n");
                int from = Math.max(0, lines.size() - 25);
                for (int i = from; i < lines.size(); i++) {
                    sb.append(lines.get(i)).append("\n");
                }
            } catch (Exception e) {
                sb.append("\n(log not available)\n");
            }
        }
        return sb.toString();
    }

    private String subject() {
        return "Arcana " + KINDS[kind].toLowerCase() + ": " + first(text.text());
    }

    private static String first(String t) {
        String l = t.trim().split("\n", 2)[0].trim();
        return l.length() > 60 ? l.substring(0, 57) + "..." : (l.isEmpty() ? "(no summary)" : l);
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    private void send() {
        if (text.text().trim().isEmpty()) {
            FayteMsg.say("Write a few words first.");
            return;
        }
        String body = this.body();
        if (body.length() > 1800) {
            body = body.substring(0, 1800) + "\n(cut short)";
        }
        try {
            URI u = new URI("mailto:" + to() + "?subject=" + enc(subject()) + "&body=" + enc(body));
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                Desktop.getDesktop().mail(u);
                FayteMsg.say("Your email program should open with the message ready. Just press send.");
                return;
            }
        } catch (Exception e) {
            FayteLog.log("Feedback: could not open the mail program: " + e);
        }
        copy();
    }

    private void post() {
        if (text.text().trim().isEmpty()) {
            FayteMsg.say("Write a few words first.");
            return;
        }
        if (System.currentTimeMillis() - lastsent < 60000L) {
            FayteMsg.say("Thanks! Please wait a minute before sending another.");
            return;
        }
        lastsent = System.currentTimeMillis();
        final String json = embedjson();
        final String url = conf("webhook");
        final String report = subject() + "\n\n" + body();
        Thread t = new Thread(
                () -> {
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                        c.setRequestMethod("POST");
                        c.setDoOutput(true);
                        c.setConnectTimeout(8000);
                        c.setReadTimeout(8000);
                        c.setRequestProperty("Content-Type", "application/json");
                        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) FayteClient/1.0");
                        c.setRequestProperty("Accept", "application/json");
                        c.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
                        int code = c.getResponseCode();
                        if (code >= 200 && code < 300) {
                            result("Sent. Thank you!", null);
                        } else {
                            String err = "";
                            try (InputStream es = c.getErrorStream()) {
                                if (es != null) {
                                    byte[] buf = new byte[600];
                                    int n = es.read(buf);
                                    err = n > 0 ? new String(buf, 0, n, StandardCharsets.UTF_8) : "";
                                }
                            } catch (Exception e2) {
                                FayteLog.once("FayteFeedbackWnd.post", e2);
                            }
                            FayteLog.log("Feedback: Discord answered " + code + ": " + err.replace((char) 10, ' '));
                            lastsent = 0L;
                            result("Sending failed (" + code + ").", report);
                        }
                    } catch (Exception e) {
                        FayteLog.log("Feedback: send failed: " + e);
                        lastsent = 0L;
                        result("Sending failed.", report);
                    }
                },
                "Fayte feedback");
        t.setDaemon(true);
        t.start();
        text.settext("");
    }

    private static String jstr(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (char ch : s.toCharArray()) {
            if (ch == '"' || ch == '\\') {
                sb.append('\\').append(ch);
            } else if (ch == '\n') {
                sb.append('\\').append('n');
            } else if (ch < 0x20) {
                sb.append('\\').append('u').append(String.format("%04x", (int) ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.append('"').toString();
    }

    private static volatile String[] outcome = null;

    private static void result(String msg, String failed) {
        outcome = new String[] {msg, failed};
    }

    public static void tick(GameUI gui) {
        String[] o = outcome;
        if (o == null) {
            return;
        }
        outcome = null;
        if (o[1] == null) {
            FayteMsg.say(o[0]);
        } else {
            FayteMsg.say(o[0] + " The text was copied so you can paste it to us instead.", GameUI.MsgType.BAD);
            copy(o[1]);
        }
    }

    private void copy() {
        copy(subject() + "\n\n" + body());
    }

    private static void copy(String all) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(all), null);
            String dc = conf("discord");
            FayteMsg.say(
                    "Copied. Paste it to " + (dc.isEmpty() ? "" : dc + " on Discord, or ") + "email " + to() + ".");
        } catch (Exception e) {
            FayteMsg.say("Could not copy: " + e.getMessage(), GameUI.MsgType.BAD);
        }
    }

    private int chipx(int i) {
        return i * FayteSkin.s(96);
    }

    @Override
    public void cdraw(GOut g) {
        for (int i = 0; i < KINDS.length; i++) {
            boolean sel = i == kind;
            Color hc = new Color(0xE3, 0xA8, 0x4A);
            FayteSkin.box(
                    g,
                    new Coord(chipx(i), chipy),
                    new Coord(FayteSkin.s(90), FayteSkin.s(22)),
                    sel ? FayteSkin.mix(FayteSkin.PANEL, hc, 0.3) : FayteSkin.PANEL,
                    sel ? hc : FayteSkin.BORDER);
            g.aimage(
                    FayteSkin.labelf.render(KINDS[i], sel ? hc : FayteSkin.TEXT).tex(),
                    new Coord(chipx(i) + FayteSkin.s(45), chipy + FayteSkin.s(11)),
                    0.5,
                    0.5);
        }
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        Coord p = c.sub(atl);
        if (button == 1 && p.y >= chipy && p.y < chipy + FayteSkin.s(22)) {
            for (int i = 0; i < KINDS.length; i++) {
                if (p.x >= chipx(i) && p.x < chipx(i) + FayteSkin.s(90)) {
                    kind = i;
                    return true;
                }
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
