package haven;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class FayteChatWindow extends Widget {
    public static final String TITLE = "Chat Window";
    public static final String FILE_NAME = "fayte_chatwindows.cfg";
    public static final Coord DEFSZ = new Coord(380, 200);
    public static final Coord MINSZ = new Coord(220, 90);
    private static final int MAXBUF = 500;
    private static final Color GOLD = new Color(0xE3, 0xA8, 0x4A);
    public static RichText.Foundry fnd = mkfnd((int) Utils.getpreff("chatfontsize", 12));
    private static int fndversion = 0;
    private static final LinkedList<FayteChatWindow.Line> buffer = new LinkedList<>();
    private static final List<FayteChatWindow> open = new ArrayList<>();
    private static FayteChatWindow lastfocus = null;
    private final GameUI gui;
    public final int id;
    private final Set<String> channels = new LinkedHashSet<>();
    private String sendto;
    private final TextEntry in;
    private final Map<FayteChatWindow.Line, Text> rendered = new IdentityHashMap<>();
    private final Map<String, Tex> labels = new HashMap<>();
    private final List<Object[]> side = new ArrayList<>();
    private int myversion = 0;
    private int rw = -1;
    private int scroll = 0;
    private int edge = 0;
    private Coord edgestart = null;
    private Coord edgec = null;
    private Coord edgesz = null;
    private boolean drag = false;
    private Coord grab;
    private FayteChatWindow.ChanList picker = null;
    private Coord hover = null;
    private boolean restoring = true;
    private boolean userclosed = false;

    public static class Line {
        public final String chan;
        public final String text;
        public final Color col;

        Line(String chan, String text, Color col) {
            this.chan = chan;
            this.text = text;
            this.col = col;
        }
    }

    private static RichText.Foundry mkfnd(int px) {
        return new RichText.Foundry(new ChatUI.ChatParser(
                TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, px, TextAttribute.FOREGROUND, FayteSkin.TEXT));
    }

    public static void setsize(int px) {
        fnd = mkfnd(px);
        fndversion++;
    }

    private static int bar() {
        return FayteSkin.s(26);
    }

    public FayteChatWindow(GameUI gui, int id, Coord c, Coord sz) {
        super(c, sz, gui);
        this.gui = gui;
        this.id = id;
        setfocustab(true);
        in = new TextEntry(Coord.z, new Coord(sz.x - bar(), 20), this, "") {
            @Override
            public void activate(String text) {
                if (!FayteChatWindow.this.send(text)) {
                    return;
                }
                settext("");
                TextEntry.armed = null;
                if (FayteChatWindow.this.gui.map != null) {
                    FayteChatWindow.this.gui.setfocus(FayteChatWindow.this.gui.map);
                }
            }

            @Override
            public boolean mousedown(Coord c, int button) {
                lastfocus = FayteChatWindow.this;
                return super.mousedown(c, button);
            }
        };
        in.clicktotype = true;
        layout();
        open.add(this);
        lastfocus = this;
    }

    public static final String PRIVATE = "Private chats (any)";
    private static final String[] STANDARD = {
        "System", "Arcana", FayteMsg.CRAFTING, FayteMsg.ALERTS, FayteMsg.MAP, "Area Chat", "Party", PRIVATE
    };
    private static final Set<String> privnames = Collections.synchronizedSet(new HashSet<>());

    private static Set<String> known() {
        Set<String> s = new LinkedHashSet<>();
        for (String n : Utils.getpref("fayte_chat_known", "").split("\t")) {
            if (!n.trim().isEmpty()) {
                s.add(n.trim());
            }
        }
        return s;
    }

    private static void remember(String n) {
        if (n == null || n.isEmpty() || n.equals("???")) {
            return;
        }
        Set<String> s = known();
        if (s.add(n)) {
            Utils.setpref("fayte_chat_known", String.join("\t", s));
        }
    }

    public static void feed(ChatUI.Channel ch, ChatUI.Channel.Message msg) {
        String name;
        String text;
        try {
            name = ch.name();
            text = msg.plain();
        } catch (Exception e) {
            return;
        }
        if (ch instanceof ChatUI.PrivChat) {
            privnames.add(name);
        } else {
            remember(name);
        }
        if (text != null) {
            FayteChatWindow.Line l = new FayteChatWindow.Line(name, text, msg.color());
            synchronized (buffer) {
                buffer.add(l);
                if (buffer.size() > MAXBUF) {
                    buffer.removeFirst();
                }
            }
        }
    }

    public static FayteChatWindow create(GameUI gui) {
        int id = 1;
        for (FayteChatWindow w : open) {
            id = Math.max(id, w.id + 1);
        }
        FayteChatWindow w = new FayteChatWindow(gui, id, gui.sz.sub(DEFSZ).div(2), DEFSZ);
        w.restoring = false;
        save();
        return w;
    }

    public static boolean focuslast(GameUI gui) {
        FayteChatWindow w = lastfocus != null && open.contains(lastfocus)
                ? lastfocus
                : (open.isEmpty() ? null : open.get(open.size() - 1));
        if (w != null && w.visible) {
            w.raise();
            gui.setfocus(w);
            w.setfocus(w.in);
            w.in.arm();
            return true;
        }
        return false;
    }

    private void layout() {
        in.c = new Coord(0, sz.y - in.sz.y);
        in.resize(new Coord(sz.x - bar() - 2, in.sz.y));
    }

    private Coord msgsz() {
        return new Coord(sz.x - bar() - 2, sz.y - in.sz.y - 2);
    }

    public List<ChatUI.Channel> chatchannels() {
        List<ChatUI.Channel> ret = new ArrayList<>();
        if (gui.chat != null) {
            for (Widget w = gui.chat.child; w != null; w = w.next) {
                if (w instanceof ChatUI.Channel) {
                    ret.add((ChatUI.Channel) w);
                }
            }
        }
        return ret;
    }

    private static String nameof(ChatUI.Channel ch) {
        try {
            return ch.name();
        } catch (Exception e) {
            return null;
        }
    }

    private ChatUI.EntryChannel findentry(String name) {
        for (ChatUI.Channel ch : chatchannels()) {
            if (ch instanceof ChatUI.EntryChannel && name != null && name.equals(nameof(ch))) {
                return (ChatUI.EntryChannel) ch;
            }
        }
        return null;
    }

    private List<String> entrynames() {
        List<String> names = new ArrayList<>();
        for (ChatUI.Channel ch : chatchannels()) {
            String n = nameof(ch);
            if (ch instanceof ChatUI.EntryChannel && n != null) {
                names.add(n);
            }
        }
        return names;
    }

    private void cyclesend() {
        List<String> names = entrynames();
        if (!names.isEmpty()) {
            int i = names.indexOf(sendto);
            sendto = names.get((i + 1) % names.size());
            save();
        }
    }

    private void picksend(Coord at) {
        final List<String> names = entrynames();
        if (names.isEmpty()) {
            return;
        }

        new FaytePopup(at, ui.root, names.toArray(new String[0]), names.indexOf(sendto), (i) -> {
            sendto = names.get(i);
            save();
        });
    }

    private boolean send(String text) {
        if (text.length() == 0) {
            return true;
        }
        ChatUI.EntryChannel ch = findentry(sendto);
        if (ch == null && sendto != null) {
            FayteMsg.say(
                    sendto + " isn't open any more, so nothing was sent. Right-click the chat to pick where to send.",
                    GameUI.MsgType.BAD);
            return false;
        }
        if (ch == null) {
            cyclesend();
            ch = findentry(sendto);
        }
        if (ch == null) {
            FayteMsg.say("No chat channel to send to yet", GameUI.MsgType.BAD);
            return false;
        }
        ch.send(text);
        return true;
    }

    static void reset() {
        buffer.clear();
        privnames.clear();
        lastfocus = null;
    }

    private boolean shows(FayteChatWindow.Line l) {
        return channels.isEmpty()
                || channels.contains(l.chan)
                || (channels.contains(PRIVATE) && privnames.contains(l.chan));
    }

    private Text render(FayteChatWindow.Line l, int w) {
        if (myversion != fndversion) {
            myversion = fndversion;
            rendered.clear();
        }
        Text t = rendered.get(l);
        if (t == null) {
            String s = channels.size() == 1 && !channels.contains(PRIVATE) ? l.text : "[" + l.chan + "] " + l.text;
            s = RichText.Parser.quote(s);
            t = l.col == null ? fnd.render(s, w) : fnd.render(s, w, TextAttribute.FOREGROUND, l.col);
            rendered.put(l, t);
        }
        return t;
    }

    private Tex label(String s, Color c) {
        String k = c.getRGB() + "|" + s;
        Tex t = labels.get(k);
        if (t == null) {
            if (labels.size() > 100) {
                labels.clear();
            }
            t = FayteSkin.labelf.render(s, c).tex();
            labels.put(k, t);
        }
        return t;
    }

    private void buildside() {
        side.clear();
        side.add(new Object[] {
            "drag", "\u2261", "Drag to move this chat. Right-click anywhere in it for channels and more."
        });
    }

    private int sidey(int i) {
        return 2 + i * (bar() - 2);
    }

    private int sideat(Coord c) {
        int x0 = sz.x - bar();
        if (c.x < x0 || c.x >= sz.x) {
            return -1;
        }
        int lastrow = (sz.y - bar() * 2) / (bar() - 2);
        for (int i = 0; i < side.size() && i < lastrow; i++) {
            int y = sidey(i);
            if (c.y >= y && c.y < y + bar() - 4) {
                return i;
            }
        }
        return -1;
    }

    private boolean closeat(Coord c) {
        return c.x >= sz.x - bar() && c.y >= sz.y - bar() * 2 + 2 && c.y < sz.y - bar() + 2;
    }

    private boolean gripat(Coord c) {
        return c.x >= sz.x - bar() && c.y >= sz.y - bar() + 2;
    }

    @Override
    public void draw(GOut g) {
        Color edge = FayteSkin.BORDER;
        FayteSkin.box(g, Coord.z, sz, FayteSkin.mix(FayteSkin.PANEL, Color.BLACK, 0.3), edge);
        Coord msz = msgsz();
        int w = msz.x - 10;
        if (w != rw) {
            rendered.clear();
            rw = w;
        }
        List<FayteChatWindow.Line> lines = new ArrayList<>();
        synchronized (buffer) {
            for (FayteChatWindow.Line l : buffer) {
                if (shows(l)) {
                    lines.add(l);
                }
            }
        }
        if (rendered.size() > lines.size() * 2 + 50) {
            rendered.keySet().retainAll(new HashSet<>(lines));
        }
        GOut mg = g.reclip(new Coord(5, 3), msz.sub(8, 4));
        int y = mg.sz.y + scroll;
        int total = 0;
        for (int i = lines.size() - 1; i >= 0; i--) {
            Text t = render(lines.get(i), w);
            y -= t.sz().y;
            total += t.sz().y;
            if (y + t.sz().y >= 0 && y < mg.sz.y) {
                mg.image(t.tex(), new Coord(0, y));
            }
            if (y < -2000 && total > scroll + mg.sz.y) {
                break;
            }
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - mg.sz.y)));
        int x0 = sz.x - bar();
        g.chcolor(edge);
        g.frect(new Coord(x0 - 1, 1), new Coord(1, sz.y - 2));
        g.chcolor();
        buildside();
        int bs = bar() - 4;
        int lastrow = (sz.y - bar() * 2) / (bar() - 2);
        for (int i = 0; i < side.size() && i < lastrow; i++) {
            Object[] s = side.get(i);
            String k = (String) s[0];
            boolean on = (k.equals("chans") && picker != null) || (k.equals("chans") && !channels.isEmpty());
            boolean hov = hover != null && sideat(hover) == i;
            Coord bc = new Coord(x0 + 2, sidey(i));
            FayteSkin.box(
                    g,
                    bc,
                    new Coord(bs, bs),
                    on ? FayteSkin.mix(FayteSkin.PANEL, GOLD, 0.3) : (hov ? FayteSkin.HOVER : FayteSkin.PANEL),
                    on ? GOLD : FayteSkin.BORDER);
            g.aimage(label((String) s[1], on ? GOLD : FayteSkin.TEXT), bc.add(bs / 2, bs / 2), 0.5, 0.5);
        }
        Coord cc = new Coord(x0 + 2, sz.y - bar() * 2 + 4);
        boolean ch = hover != null && closeat(hover);
        FayteSkin.box(g, cc, new Coord(bs, bs), ch ? FayteSkin.HOVER : FayteSkin.PANEL, FayteSkin.BORDER);
        g.aimage(label("\u2715", FayteSkin.TEXT), cc.add(bs / 2, bs / 2), 0.5, 0.5);
        g.chcolor(FayteSkin.BORDER);
        for (int i = 0; i < 3; i++) {
            int o = 3 + i * 3;
            g.frect(sz.sub(o + 1, 4), new Coord(2, 2));
            g.frect(sz.sub(4, o + 1), new Coord(2, 2));
        }
        g.chcolor();
        super.draw(g);
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        if (!super.mousewheel(c, amount)) {
            scroll = Math.max(0, scroll - amount * 20);
        }
        return true;
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        lastfocus = this;
        raise();
        parent.setfocus(this);
        int s = sideat(c);
        int e = FayteEdges.at(c, sz, FayteSkin.s(5));
        if (gripat(c) && button == 1) {
            e = FayteEdges.RIGHT | FayteEdges.BOTTOM;
        }
        if (e != 0 && button == 1 && !FayteHud.locked(ui)) {
            edge = e;
            edgestart = rootpos().add(c);
            edgec = this.c;
            edgesz = sz;
            ui.grabmouse(this);
            return true;
        } else if (closeat(c) && button == 1) {
            userclosed = true;
            ui.destroy(this);
            return true;
        } else if (button == 3) {
            menu(rootpos().add(c));
            return true;
        } else if (s >= 0) {
            if (button == 1) {
                startdrag(c);
            }
            return true;
        }
        super.mousedown(c, button);
        return true;
    }

    private void startdrag(Coord c) {
        if (FayteHud.locked(ui)) {
            return;
        }
        drag = true;
        grab = c;
        ui.grabmouse(this);
    }

    @Override
    public void mousemove(Coord c) {
        hover = c.isect(Coord.z, sz) ? c : null;
        if (edge != 0) {
            Coord d = rootpos().add(c).sub(edgestart);
            Coord[] r = FayteEdges.apply(edge, edgec, edgesz, d, MINSZ);
            this.c = r[0];
            if (!r[1].equals(sz)) {
                resize(r[1]);
                layout();
            }
        } else if (drag) {
            this.c = WindowSnap.snap(this, this.c.add(c.sub(grab)));
        } else {
            super.mousemove(c);
        }
    }

    @Override
    public boolean mouseup(Coord c, int button) {
        if (edge != 0 || drag) {
            edge = 0;
            drag = false;
            ui.grabmouse(null);
            save();
            return true;
        }
        return super.mouseup(c, button);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        int s = sideat(c);
        if (s >= 0) {
            return side.get(s)[2];
        } else if (closeat(c)) {
            return "Close this chat";
        } else if (gripat(c) || FayteEdges.at(c, sz, FayteSkin.s(5)) != 0) {
            return "Drag to resize";
        }
        return super.tooltip(c, prev);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if (parent != null && !drag) {
            Coord nc = GameUI.onScreen(c, sz, parent.sz);
            if (!nc.equals(c)) {
                c = nc;
            }
        }
    }

    private void menu(Coord at) {
        String to = sendto == null ? "(pick one)" : sendto;
        String shows = channels.isEmpty()
                ? "every channel"
                : (channels.size() == 1 ? channels.iterator().next() : channels.size() + " channels");
        String[] opts = {
            "Show channels\u2026  (now: " + shows + ")",
            "Send to: " + to + " \u25b8",
            "New chat window",
            "Close this chat"
        };
        new FaytePopup(at, ui.root, opts, -1, (i) -> {
            if (i == 0) {
                chanpop(at);
            } else if (i == 1) {
                picksend(at);
            } else if (i == 2) {
                copy();
            } else if (i == 3) {
                userclosed = true;
                ui.destroy(this);
            }
        });
    }

    private void chanpop(Coord at) {
        if (picker != null && picker.linked()) {
            ui.destroy(picker);
        }
        picker = new FayteChatWindow.ChanList(at);
    }

    private void copy() {
        int nid = 1;
        for (FayteChatWindow w : open) {
            nid = Math.max(nid, w.id + 1);
        }
        Coord nc = new Coord(c.x + sz.x + 4, c.y);
        if (nc.x + sz.x > parent.sz.x) {
            nc = new Coord(Math.max(0, c.x - sz.x - 4), c.y);
        }
        FayteChatWindow w = new FayteChatWindow(gui, nid, nc, sz);
        w.channels.addAll(channels);
        w.sendto = sendto;
        w.restoring = false;
        save();
    }

    private class ChanList extends Widget {
        ChanList(Coord at) {
            super(at, Coord.z, FayteChatWindow.this.ui.root);
            int y = 6;
            int w = FayteSkin.s(200);
            new Label(new Coord(6, y), this, "Show in this chat:");
            y += FayteSkin.labelf.height() + 6;
            new CheckBox(new Coord(6, y), this, "Every channel") {
                {
                    a = FayteChatWindow.this.channels.isEmpty();
                }

                @Override
                public void changed(boolean val) {
                    if (val) {
                        FayteChatWindow.this.setchan(null, true);
                        ChanList.this.close();
                    }
                }
            };
            y += FayteSkin.s(22);
            LinkedHashSet<String> names = new LinkedHashSet<>(Arrays.asList(STANDARD));
            for (ChatUI.Channel ch : FayteChatWindow.this.chatchannels()) {
                String n = nameof(ch);
                if (n != null && !(ch instanceof ChatUI.PrivChat)) {
                    names.add(n);
                    remember(n);
                }
            }
            names.addAll(known());
            names.addAll(FayteChatWindow.this.channels);
            for (final String n : names) {
                if (n != null) {
                    new CheckBox(new Coord(6, y), this, n) {
                        {
                            a = FayteChatWindow.this.channels.contains(n);
                        }

                        @Override
                        public void changed(boolean val) {
                            FayteChatWindow.this.setchan(n, val);
                        }
                    };
                    y += FayteSkin.s(22);
                }
            }
            y += 4;
            new Label(new Coord(6, y), this, "Click outside to close.");
            y += FayteSkin.labelf.height() + 4;
            sz = new Coord(w, y + 6);
            Coord rs = FayteChatWindow.this.ui.root.sz;
            c = new Coord(Math.max(0, Math.min(at.x, rs.x - sz.x)), Math.max(0, Math.min(at.y, rs.y - sz.y)));
            raise();
            ui.grabmouse(this);
        }

        void close() {
            ui.grabmouse(null);
            ui.destroy(this);
        }

        @Override
        public void draw(GOut g) {
            FayteSkin.box(g, Coord.z, sz, FayteSkin.PANEL, FayteSkin.BORDER);
            super.draw(g);
        }

        @Override
        public boolean mousedown(Coord c, int button) {
            if (!c.isect(Coord.z, sz)) {
                close();
                return true;
            }
            super.mousedown(c, button);
            return true;
        }
    }

    private void setchan(String name, boolean on) {
        if (name == null) {
            channels.clear();
        } else if (on) {
            channels.add(name);
        } else {
            channels.remove(name);
        }
        rendered.clear();
        scroll = 0;
        save();
    }

    @Override
    public boolean type(char key, KeyEvent ev) {
        if (key == 27 && TextEntry.armed == in) {
            TextEntry.armed = null;
            if (gui.map != null) {
                gui.setfocus(gui.map);
            }
            return true;
        }
        return super.type(key, ev);
    }

    @Override
    public void destroy() {
        if (picker != null && picker.linked()) {
            picker.close();
        }
        open.remove(this);
        if (lastfocus == this) {
            lastfocus = null;
        }
        super.destroy();
        if (userclosed) {
            save();
        }
    }

    private static void purge() {
        open.removeIf(w -> !w.linked() || w.gui == null || !w.gui.linked());
    }

    public static boolean anyopen() {
        purge();
        return !open.isEmpty();
    }

    public static void closeall(GameUI gui) {
        purge();
        for (FayteChatWindow w : new ArrayList<>(open)) {
            if (w.gui == gui && w.attached()) {
                w.ui.destroy(w);
            }
        }
    }

    public static File file() {
        return Config.getFile(FILE_NAME);
    }

    public static synchronized void save() {
        purge();
        boolean anyrestoring = false;
        for (FayteChatWindow w : open) {
            anyrestoring |= w.restoring;
        }
        if (anyrestoring) {
            return;
        }
        Properties p = new Properties();
        StringBuilder ids = new StringBuilder();
        for (FayteChatWindow w : open) {
            if (ids.length() > 0) {
                ids.append(",");
            }
            ids.append(w.id);
            p.setProperty(w.id + ".pos", w.c.x + "," + w.c.y);
            p.setProperty(w.id + ".size", w.sz.x + "," + w.sz.y);
            p.setProperty(w.id + ".channels", String.join("\t", w.channels));
            if (w.sendto != null) {
                p.setProperty(w.id + ".send", w.sendto);
            }
        }
        p.setProperty("ids", ids.toString());
        p.setProperty("frameless", "1");
        File f = file();
        File tmp = new File(f.getPath() + ".tmp");
        try {
            try (OutputStream out = new FileOutputStream(tmp)) {
                p.store(out, "Fayte Salem chat windows");
            }
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.out.println("Could not write " + f + ": " + e);
            tmp.delete();
        }
    }

    private static String arcana(String ch) {
        return ch != null && ch.startsWith("Fayte") ? "Arcana" + ch.substring(5) : ch;
    }

    private static Coord coord(String s, Coord def) {
        try {
            String[] parts = s.split(",");
            return new Coord(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (Exception e) {
            return def;
        }
    }

    public static void restore(GameUI gui) {
        for (FayteChatWindow w : new ArrayList<>(open)) {
            if (w.gui != gui) {
                open.remove(w);
            }
        }
        closeall(gui);
        purge();

        File f = file();
        if (!f.exists()) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
        } catch (Exception e) {
            System.out.println("Could not read " + f + ": " + e);
            return;
        }
        boolean old = !"1".equals(p.getProperty("frameless"));
        List<FayteChatWindow> made = new ArrayList<>();
        for (String sid : p.getProperty("ids", "").split(",")) {
            try {
                int id = Integer.parseInt(sid.trim());
                boolean dup = false;
                for (FayteChatWindow o : made) {
                    dup |= o.id == id;
                }
                if (dup) {
                    continue;
                }
                Coord sz = coord(p.getProperty(id + ".size", ""), DEFSZ);
                if (old) {
                    sz = sz.add(bar(), 0);
                }
                sz = new Coord(Math.max(MINSZ.x, sz.x), Math.max(MINSZ.y, sz.y));
                Coord c = coord(p.getProperty(id + ".pos", ""), gui.sz.sub(sz).div(2));
                FayteChatWindow w = new FayteChatWindow(gui, id, c, sz);
                String chs = p.getProperty(id + ".channels", "");
                if (!chs.isEmpty()) {
                    for (String ch : chs.split("\t")) {
                        w.channels.add(arcana(ch));
                    }
                }
                w.sendto = arcana(p.getProperty(id + ".send"));
                made.add(w);
            } catch (NumberFormatException e) {
            }
        }
        for (FayteChatWindow w : made) {
            w.restoring = false;
        }
    }
}
