package haven;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Color;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteNotesPanel extends Widget {
    private static final int LISTW = 220;
    private static Store store = null;
    private static boolean dirty = false;
    private static long lastsave = 0L;
    private final FayteTextArea area;
    private final FayteSplit split;
    private final Map<String, Text> texts = new HashMap<>();
    private int listw = FayteAlmanacWnd.prefint("fayte_alm_split_notes", LISTW);
    private Note cur = null;
    private int fontpt = FayteAlmanacWnd.prefint("fayte_notes_font", 20);
    private int scroll = 0;

    public static class Note {
        public String title;
        public String text = "";
        public long updated;
    }

    public static class Store {
        public List<Note> notes = new ArrayList<>();
    }

    private static File file() {
        return new File(FaytePaths.fayte(), "notes.json");
    }

    public static synchronized Store get() {
        if (store == null) {
            File f = file();
            if (f.exists()) {
                try {
                    store = new Gson()
                            .fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), Store.class);
                } catch (Exception e) {
                    FayteLog.log("Notes: could not read " + f + ": " + e);
                }
            }
            if (store == null) {
                store = new Store();
            }
            if (store.notes == null) {
                store.notes = new ArrayList<>();
            }
        }
        return store;
    }

    public static synchronized void save(boolean force) {
        long now = System.currentTimeMillis();
        if (store == null || !dirty || (!force && now - lastsave < 3000L)) {
            return;
        }
        dirty = false;
        lastsave = now;
        File f = file();
        try {
            f.getParentFile().mkdirs();
            FaytePaths.write(
                    f,
                    new GsonBuilder().setPrettyPrinting().create().toJson(store).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FayteLog.log("Notes: could not write " + f + ": " + e);
        }
    }

    public FayteNotesPanel(Coord c, Coord sz, Widget parent) {
        super(c, sz, parent);
        new Button(new Coord(0, 0), FayteSkin.s(70), this, "New") {
            @Override
            public void click() {
                FayteNotesPanel.this.newnote();
            }
        };
        new Button(new Coord(FayteSkin.s(74), 0), FayteSkin.s(70), this, "Rename") {
            @Override
            public void click() {
                FayteNotesPanel.this.rename();
            }
        };
        new Button(new Coord(FayteSkin.s(148), 0), FayteSkin.s(70), this, "Delete") {
            @Override
            public void click() {
                FayteNotesPanel.this.delete();
            }
        };
        new Button(new Coord(FayteSkin.s(222), 0), FayteSkin.s(34), this, "A\u2212") {
            @Override
            public void click() {
                FayteNotesPanel.this.fontsize(-2);
            }
        };
        new Button(new Coord(FayteSkin.s(258), 0), FayteSkin.s(34), this, "A+") {
            @Override
            public void click() {
                FayteNotesPanel.this.fontsize(2);
            }
        };
        area = new FayteTextArea(Coord.z, Coord.z, this);
        area.setsize(fontpt);
        area.changed = () -> {
            if (cur != null) {
                cur.text = area.text();
                cur.updated = System.currentTimeMillis();
                dirty = true;
            }
        };
        split = new FayteSplit(this, true, (p) -> {
            listw = Math.max(140, Math.min(this.sz.x - 200, p - 1));
            Utils.setpref("fayte_alm_split_notes", Integer.toString(listw));
            layout();
        });
        List<Note> ns = get().notes;
        select(ns.isEmpty() ? null : ns.get(0));
        layout();
    }

    private void fontsize(int d) {
        fontpt = Math.max(10, Math.min(40, fontpt + d));
        Utils.setpref("fayte_notes_font", Integer.toString(fontpt));
        area.setsize(fontpt);
    }

    public void layout() {
        listw = Math.max(140, Math.min(Math.max(140, sz.x - 200), listw));
        area.c = new Coord(listw + 8, hdr());
        area.resize(new Coord(sz.x - listw - 8, sz.y - hdr()));
        split.place(listw + 1, hdr(), sz.y - hdr());
    }

    @Override
    public void resize(Coord sz) {
        super.resize(sz);
        layout();
    }

    private void select(Note n) {
        cur = n;
        area.settext(n == null ? "" : n.text);
        area.visible = n != null;
    }

    private void newnote() {
        new FayteAsk(ui.gui, "New note", "Name the note. Enter to create.", "", (t) -> {
            Note n = new Note();
            n.title = t.isEmpty() ? "Note " + (get().notes.size() + 1) : t;
            n.updated = System.currentTimeMillis();
            get().notes.add(0, n);
            dirty = true;
            select(n);
            parent.setfocus(this);
            setfocus(area);
        });
    }

    private void rename() {
        if (cur == null) {
            return;
        }
        Note n = cur;
        new FayteAsk(ui.gui, "Rename note", "New name. Enter to save.", n.title, (t) -> {
            if (!t.isEmpty()) {
                n.title = t;
                dirty = true;
            }
        });
    }

    private long armed = 0L;

    private void delete() {
        if (cur == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - armed < 4000L) {
            armed = 0L;
            get().notes.remove(cur);
            dirty = true;
            save(true);
            List<Note> ns = get().notes;
            select(ns.isEmpty() ? null : ns.get(0));
        } else {
            armed = now;
            FayteMsg.say("Click Delete again within 4 seconds to delete \"" + cur.title + "\".");
        }
    }

    private Text text(String s, Color c) {
        String k = c.getRGB() + "|" + s;
        Text t = texts.get(k);
        if (t == null) {
            if (texts.size() > 200) {
                texts.clear();
            }
            t = FayteSkin.labelf.render(s, c);
            texts.put(k, t);
        }
        return t;
    }

    private static int hdr() {
        return Button.bh() + 6;
    }

    private int rowh() {
        return FayteSkin.s(22);
    }

    @Override
    public void draw(GOut g) {
        Coord lc = new Coord(0, hdr());
        Coord ls = new Coord(listw, sz.y - hdr());
        FayteSkin.box(g, lc, ls, FayteSkin.PANEL, FayteSkin.BORDER);
        List<Note> ns = get().notes;
        int rh = rowh();
        int rows = (ls.y - 8) / rh;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, ns.size() - rows)));
        Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.6);
        if (ns.isEmpty()) {
            g.image(text("No notes yet. Click New.", dim).tex(), lc.add(6, 6));
        }
        for (int i = 0; i < rows && scroll + i < ns.size(); i++) {
            Note n = ns.get(scroll + i);
            Coord rc = lc.add(4, 4 + i * rh);
            if (n == cur) {
                FayteSkin.box(g, rc.sub(2, 0), new Coord(ls.x - 4, rh), FayteSkin.BORDER, null);
            }
            String t = n.title;
            Text tt = text(t, FayteSkin.TEXT);
            while (tt.sz().x > ls.x - 12 && t.length() > 4) {
                t = t.substring(0, t.length() - 2);
                tt = text(t + "\u2026", FayteSkin.TEXT);
            }
            g.image(tt.tex(), rc.add(4, (rh - tt.sz().y) / 2));
        }
        if (cur == null && !ns.isEmpty()) {
            g.image(text("Pick a note on the left.", dim).tex(), new Coord(listw + 14, 10));
        }
        super.draw(g);
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (button == 1 && c.x < listw && c.y >= hdr() + 4) {
            int i = scroll + (c.y - hdr() - 4) / rowh();
            List<Note> ns = get().notes;
            if (i >= 0 && i < ns.size()) {
                select(ns.get(i));
            }
            return true;
        }
        return super.mousedown(c, button);
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        if (c.x < listw) {
            scroll += amount > 0 ? 1 : -1;
            return true;
        }
        return super.mousewheel(c, amount);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        save(false);
    }

    @Override
    public void destroy() {
        save(true);
        super.destroy();
    }
}
