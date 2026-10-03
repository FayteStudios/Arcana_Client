package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.imageio.ImageIO;

public class WorldMapWnd extends Window {
    private static final Coord defsz = new Coord(920, 520);
    private static final int PANEL_W = 220;
    private static final int ROW_H = 20;
    private static final int ROWS_Y = 25;
    private static final int ROWS_BOTTOM = 5 + (Button.bh() + 8) * 2 + FayteSkin.s(32);
    private static WorldMapWnd instance = null;
    private long showseg = Long.MIN_VALUE;
    private long playerseg = Long.MIN_VALUE;
    private long lastforce = 0L;
    private boolean confirmclean = false;
    private final FayteTitleButton cleanbtn;
    private double cx = 0.0;
    private double cy = 0.0;
    private double zoom;
    private boolean centered = false;
    private boolean panning = false;
    private Coord pandoff;
    private int scroll = 0;
    private boolean youselected = true;
    private WorldMapMarkers.Marker selected = null;
    private WorldMapMarkers.Marker renaming = null;
    private final Button prevbtn;
    private final Button nextbtn;
    private final TextEntry renamebox;
    private final Map<String, Text> texts = new HashMap<>();
    private final Map<File, Defer.Future<Tex>> texes = new LinkedHashMap<File, Defer.Future<Tex>>(256, 0.75F, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Entry<File, Defer.Future<Tex>> eldest) {
            if (size() > 1024) {
                Defer.Future<Tex> f = eldest.getValue();
                if (f.done()) {
                    Tex t = f.get();
                    if (t != null) {
                        t.dispose();
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    };

    private WorldMapWnd(Widget parent) {
        super(new Coord(100, 100), defsz, parent, "World Map");
        justclose = true;
        cleanbtn = new FayteTitleButton(
                this,
                "Clean up old map files",
                "Delete old map session folders that are already merged into this map",
                this::cleanclick);
        cleanbtn.hide();
        addtwdg(cleanbtn);
        zoom = FayteConfig.worldmapZoom.get();
        Coord saved = FayteConfig.worldmapCenter.get();
        if (saved != null) {
            cx = saved.x + 0.5;
            cy = saved.y + 0.5;
        }
        prevbtn = new Button(new Coord(PANEL_W + 10, 5), 30, this, "<") {
            @Override
            public void click() {
                WorldMapWnd.this.switchisland(-1);
            }
        };
        nextbtn = new Button(new Coord(PANEL_W + 10 + FayteSkin.s(40), 5), 30, this, ">") {
            @Override
            public void click() {
                WorldMapWnd.this.switchisland(1);
            }
        };
        int step = Button.bh() + 8;
        int bw = (PANEL_W - 18) / 2;
        int bx2 = 5 + bw + 8;
        int by = defsz.y - 5 - step * 2;
        new Button(new Coord(5, by), bw, this, "Point") {
            @Override
            public void click() {
                WorldMapWnd.this.togglepointer();
            }
        };
        new Button(new Coord(bx2, by), bw, this, "Add here") {
            @Override
            public void click() {
                WorldMapWnd.this.addhere();
            }
        };
        new Button(new Coord(5, by + step), bw, this, "Rename") {
            @Override
            public void click() {
                WorldMapWnd.this.startrename();
            }
        };
        new Button(new Coord(bx2, by + step), bw, this, "Delete") {
            @Override
            public void click() {
                WorldMapWnd.this.deleteselected();
            }
        };
        renamebox = new TextEntry(new Coord(5, by - FayteSkin.s(28)), PANEL_W - 10, this, "") {
            @Override
            public void activate(String text) {
                WorldMapWnd.this.finishrename(text);
            }
        };
        renamebox.hide();
        WorldMapData.refresh(curseg(), false);
    }

    public static void toggle(GameUI gui) {
        if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
        } else {
            instance = new WorldMapWnd(gui);
        }
    }

    private String cursession() {
        LocalMiniMap mm = ui.gui != null ? ui.gui.mmap : null;
        return mm != null ? mm.session() : null;
    }

    private FayteMapStore.Loc ploc() {
        return WorldMapMarkers.playerloc(ui.gui);
    }

    private long curseg() {
        FayteMapStore.Loc l = ploc();
        return l == null ? Long.MIN_VALUE : l.seg;
    }

    private int islindex() {
        return islindex(WorldMapData.result());
    }

    private int islindex(WorldMapIndex.Result d) {
        if (d == null) {
            return -1;
        }
        for (int i = 0; i < d.islands.size(); i++) {
            if (d.islands.get(i).seg == showseg) {
                return i;
            }
        }
        return d.islands.isEmpty() ? -1 : 0;
    }

    private WorldMapIndex.Island isl() {
        return isl(WorldMapData.result());
    }

    private WorldMapIndex.Island isl(WorldMapIndex.Result d) {
        int i = islindex(d);
        return i < 0 ? null : d.islands.get(i);
    }

    private void switchisland(int dir) {
        WorldMapIndex.Result d = WorldMapData.result();
        if (d != null && !d.islands.isEmpty()) {
            int n = d.islands.size();
            int i = ((islindex(d) + dir) % n + n) % n;
            showseg = d.islands.get(i).seg;
            centeron(isl(d));
        }
    }

    private void showseg(long seg) {
        showseg = seg;
        WorldMapIndex.Result d = WorldMapData.result();
        if (d != null && FayteMapStore.current().island(d, seg) == null) {
            forcerefresh();
        }
    }

    private void forcerefresh() {
        long now = System.currentTimeMillis();
        if (now - lastforce > 1000L) {
            lastforce = now;
            WorldMapData.refresh(curseg(), true);
        }
    }

    private void cleanclick() {
        if (WorldMapData.running()) {
            return;
        }
        if (!confirmclean) {
            confirmclean = true;
            cleanbtn.setlabel("Click again: delete " + WorldMapData.cleanable() + " old folders");
            placetwdgs();
        } else {
            confirmclean = false;
            WorldMapData.cleanup(cursession());
        }
    }

    private void updatecleanbtn() {
        boolean want = WorldMapData.cleanable() > 0 && !WorldMapData.running();
        if (want != cleanbtn.visible) {
            if (want) {
                cleanbtn.setlabel("Clean up old map files");
                cleanbtn.show();
            } else {
                cleanbtn.hide();
                confirmclean = false;
            }
            placetwdgs();
        }
    }

    private void centeron(WorldMapIndex.Island isl) {
        Coord2 pp = playerpos(isl);
        if (pp != null) {
            cx = pp.x;
            cy = pp.y;
        } else if (isl != null && isl.min != null) {
            cx = (isl.min.x + isl.max.x + 1) / 2.0;
            cy = (isl.min.y + isl.max.y + 1) / 2.0;
        }
    }

    private void showmarker(WorldMapMarkers.Marker m) {
        WorldMapIndex.Result d = WorldMapData.result();
        if (d == null) {
            return;
        }
        FayteMapStore.Loc l = WorldMapMarkers.loc(ui.gui, m);
        if (l != null) {
            showseg(l.seg);
            cx = l.x;
            cy = l.y;
        }
    }

    private double ppg() {
        return MCache.cmaps.x * zoom;
    }

    private Coord2 playerpos(WorldMapIndex.Island isl) {
        FayteMapStore.Loc l = ploc();
        return l == null || isl == null || l.seg != isl.seg ? null : new Coord2(l.x, l.y);
    }

    private Coord2 markerpos(WorldMapMarkers.Marker m, WorldMapIndex.Island isl) {
        FayteMapStore.Loc l = WorldMapMarkers.loc(ui.gui, m);
        return l == null || isl == null || l.seg != isl.seg ? null : new Coord2(l.x, l.y);
    }

    private Tex gettex(final File f) {
        Defer.Future<Tex> fut = texes.get(f);
        if (fut == null) {
            fut = Defer.later(new Defer.Callable<Tex>() {
                public Tex call() {
                    try {
                        BufferedImage img = ImageIO.read(f);
                        return img == null ? null : new TexI(img);
                    } catch (Exception e) {
                        return null;
                    }
                }
            });
            texes.put(f, fut);
        }
        return fut.done() ? fut.get() : null;
    }

    private Text text(String s) {
        Text t = texts.get(s);
        if (t == null) {
            if (texts.size() > 500) {
                texts.clear();
            }
            t = Text.render(s, Color.WHITE);
            texts.put(s, t);
        }
        return t;
    }

    private List<WorldMapMarkers.Marker> entries() {
        List<WorldMapMarkers.Marker> ret = new ArrayList<>();
        ret.add(null);
        if (ui.gui != null && ui.gui.homestead() != null) {
            ret.add(WorldMapMarkers.HOMESTEAD);
        }
        ret.addAll(WorldMapMarkers.all());
        return ret;
    }

    private String rowlabel(WorldMapMarkers.Marker m) {
        if (m == null) {
            return "You";
        }
        GameUI gui = ui.gui;
        Coord ct = WorldMapMarkers.curtile(gui, m);
        Coord pt = WorldMapMarkers.playertile(gui);
        String where = ct == null || pt == null ? "other map" : Math.round(ct.dist(pt)) + " tiles";
        return (WorldMapMarkers.pointed == m ? "> " : "") + m.name + "  (" + where + ")";
    }

    private static Color markercolor(WorldMapMarkers.Marker m) {
        if (m == WorldMapMarkers.HOMESTEAD) {
            return new Color(80, 220, 80);
        } else if (m.kind.equals("claim")) {
            return new Color(255, 160, 40);
        } else {
            return m.kind.equals("bell") ? new Color(200, 120, 255) : new Color(255, 230, 60);
        }
    }

    private boolean isselected(WorldMapMarkers.Marker m) {
        return m == null ? youselected : !youselected && selected == m;
    }

    private void select(WorldMapMarkers.Marker m) {
        youselected = m == null;
        selected = m;
        if (m == null) {
            long seg = curseg();
            if (seg != Long.MIN_VALUE) {
                showseg(seg);
                centeron(isl());
            }
        } else {
            showmarker(m);
        }
    }

    private void togglepointer() {
        if (!youselected && selected != null) {
            WorldMapMarkers.pointed = WorldMapMarkers.pointed == selected ? null : selected;
        }
    }

    private void addhere() {
        GameUI gui = ui.gui;
        WorldMapMarkers.Marker m =
                WorldMapMarkers.addat(gui, "custom", WorldMapMarkers.nextname(), WorldMapMarkers.playertile(gui));
        if (m != null) {
            select(m);
        }
    }

    private void addat(Coord2 g) {
        WorldMapIndex.Island isl = this.isl();
        if (isl == null) {
            return;
        }
        WorldMapMarkers.Marker m =
                WorldMapMarkers.addloc("custom", WorldMapMarkers.nextname(), new FayteMapStore.Loc(isl.seg, g.x, g.y));
        if (m != null) {
            youselected = false;
            selected = m;
        }
    }

    private void startrename() {
        if (!youselected && selected != null && selected != WorldMapMarkers.HOMESTEAD) {
            renaming = selected;
            renamebox.settext(selected.name);
            renamebox.show();
            setfocus(renamebox);
        }
    }

    private void finishrename(String text) {
        if (renaming != null) {
            WorldMapMarkers.rename(renaming, text);
        }
        renaming = null;
        renamebox.hide();
    }

    private void deleteselected() {
        if (!youselected && selected != null && selected != WorldMapMarkers.HOMESTEAD) {
            WorldMapMarkers.remove(selected);
            selected = null;
            youselected = true;
        }
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        long seg = curseg();
        WorldMapData.refresh(seg, false);
        updatecleanbtn();
        WorldMapIndex.Result d = WorldMapData.result();
        if (d == null) {
            return;
        }
        if (!centered) {
            if (seg != Long.MIN_VALUE) {
                showseg(seg);
            } else if (!d.islands.isEmpty()) {
                showseg = d.islands.get(0).seg;
            }
            WorldMapIndex.Island isl = this.isl();
            if (playerpos(isl) != null || FayteConfig.worldmapCenter.get() == null) {
                centeron(isl);
            }
            playerseg = seg;
            centered = true;
        } else if (seg != Long.MIN_VALUE && seg != playerseg) {
            boolean follow = playerseg == Long.MIN_VALUE || showseg == playerseg;
            playerseg = seg;
            if (follow) {
                showseg(seg);
                centeron(isl());
            }
        } else if (seg != Long.MIN_VALUE
                && seg == showseg
                && FayteMapStore.current().island(d, seg) == null) {
            forcerefresh();
        }
    }

    private Coord mapul() {
        return new Coord(PANEL_W + 5, 0);
    }

    private Coord mapsz() {
        return asz.sub(PANEL_W + 5, 0);
    }

    @Override
    public void cdraw(GOut og) {
        drawpanel(og.reclip(Coord.z, new Coord(PANEL_W, asz.y)));
        drawmap(og.reclip(mapul(), mapsz()));
    }

    private void drawpanel(GOut g) {
        g.chcolor(10, 10, 10, 230);
        g.frect(Coord.z, g.sz);
        g.chcolor();
        g.image(text("Markers").tex(), new Coord(8, 5));
        List<WorldMapMarkers.Marker> es = entries();
        int rows = (asz.y - ROWS_Y - ROWS_BOTTOM) / ROW_H;
        scroll = Math.max(0, Math.min(scroll, es.size() - rows));

        for (int i = 0; i < rows && i + scroll < es.size(); i++) {
            WorldMapMarkers.Marker m = es.get(i + scroll);
            int y = ROWS_Y + i * ROW_H;
            if (isselected(m)) {
                g.chcolor(80, 80, 140, 200);
                g.frect(new Coord(2, y), new Coord(PANEL_W - 4, ROW_H - 2));
                g.chcolor();
            }
            g.chcolor(m == null ? new Color(255, 60, 60) : markercolor(m));
            g.frect(new Coord(6, y + 6), new Coord(7, 7));
            g.chcolor();
            g.image(text(rowlabel(m)).tex(), new Coord(18, y + 3));
        }
    }

    private void drawmap(GOut g) {
        Coord sz = g.sz;
        g.chcolor(20, 20, 20, 255);
        g.frect(Coord.z, sz);
        g.chcolor();
        WorldMapIndex.Result d = WorldMapData.result();
        WorldMapIndex.Island isl = isl(d);
        if (isl != null) {
            double ppg = this.ppg();
            int gx0 = (int) Math.floor(cx - sz.x / 2.0 / ppg);
            int gy0 = (int) Math.floor(cy - sz.y / 2.0 / ppg);
            int gx1 = (int) Math.ceil(cx + sz.x / 2.0 / ppg);
            int gy1 = (int) Math.ceil(cy + sz.y / 2.0 / ppg);
            Coord gc = new Coord();

            for (gc.y = gy0; gc.y < gy1; gc.y++) {
                for (gc.x = gx0; gc.x < gx1; gc.x++) {
                    File f = isl.tiles.get(gc);
                    if (f != null) {
                        Tex t = gettex(f);
                        if (t != null) {
                            int x0 = sx(gc.x, sz, ppg);
                            int y0 = sy(gc.y, sz, ppg);
                            int x1 = sx(gc.x + 1, sz, ppg);
                            int y1 = sy(gc.y + 1, sz, ppg);
                            g.image(t, new Coord(x0, y0), new Coord(x1 - x0, y1 - y0));
                        }
                    }
                }
            }
            List<WorldMapMarkers.Marker> es = entries();
            for (WorldMapMarkers.Marker m : es) {
                if (m != null) {
                    Coord2 p = markerpos(m, isl);
                    if (p != null) {
                        Coord pc = new Coord(sx(p.x, sz, ppg), sy(p.y, sz, ppg));
                        g.chcolor(0, 0, 0, 255);
                        g.frect(pc.sub(5, 5), new Coord(11, 11));
                        g.chcolor(markercolor(m));
                        g.frect(pc.sub(4, 4), new Coord(9, 9));
                        g.chcolor();
                        Text t = text(m.name);
                        g.chcolor(0, 0, 0, 160);
                        g.frect(pc.add(8, -8), t.sz().add(4, 2));
                        g.chcolor();
                        g.image(t.tex(), pc.add(10, -7));
                    }
                }
            }
            Coord2 pp = playerpos(isl);
            if (pp != null) {
                Coord pc = new Coord(sx(pp.x, sz, ppg), sy(pp.y, sz, ppg));
                g.chcolor(0, 0, 0, 255);
                g.frect(pc.sub(4, 4), new Coord(9, 9));
                g.chcolor(255, 60, 60, 255);
                g.frect(pc.sub(3, 3), new Coord(7, 7));
                g.chcolor();
            }
            String lbl = String.format(
                    "Map %d of %d (%d tiles)%s",
                    islindex(d) + 1, d.islands.size(), isl.tiles.size(), curseg() == isl.seg ? " - you are here" : "");
            Text lt = text(lbl);
            g.chcolor(0, 0, 0, 160);
            g.frect(new Coord(75, 5), lt.sz().add(8, 4));
            g.chcolor();
            g.image(lt.tex(), new Coord(79, 7));
        }
        String st = WorldMapData.status();
        if (st != null) {
            Text t = text(st);
            g.chcolor(0, 0, 0, 160);
            g.frect(new Coord(6, 33), t.sz().add(8, 4));
            g.chcolor();
            g.image(t.tex(), new Coord(10, 35));
        }
    }

    private int sx(double wx, Coord sz, double ppg) {
        return (int) Math.round(sz.x / 2.0 + (wx - cx) * ppg);
    }

    private int sy(double wy, Coord sz, double ppg) {
        return (int) Math.round(sz.y / 2.0 + (wy - cy) * ppg);
    }

    private boolean inmap(Coord c) {
        return c.isect(atl.add(mapul()), mapsz());
    }

    private int rowat(Coord c) {
        Coord pc = c.sub(atl);
        if (pc.x >= 0 && pc.x < PANEL_W && pc.y >= ROWS_Y && pc.y < asz.y - ROWS_BOTTOM) {
            return (pc.y - ROWS_Y) / ROW_H + scroll;
        } else {
            return -1;
        }
    }

    private boolean onbutton(Coord c) {
        return c.isect(xlate(prevbtn.c, true), prevbtn.sz) || c.isect(xlate(nextbtn.c, true), nextbtn.sz);
    }

    private Coord2 mapworld(Coord c) {
        Coord mc = c.sub(atl).sub(mapul());
        Coord sz = mapsz();
        double ppg = this.ppg();
        return new Coord2(cx + (mc.x - sz.x / 2.0) / ppg, cy + (mc.y - sz.y / 2.0) / ppg);
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (inmap(c) && !onbutton(c)) {
            parent.setfocus(this);
            raise();
            if (button == 1) {
                panning = true;
                pandoff = c;
                ui.grabmouse(this);
                return true;
            } else if (button == 3) {
                addat(mapworld(c));
                return true;
            }
        }
        int row = rowat(c);
        if (button == 1 && row >= 0) {
            List<WorldMapMarkers.Marker> es = entries();
            if (row < es.size()) {
                select(es.get(row));
            }
            parent.setfocus(this);
            raise();
            return true;
        } else {
            return super.mousedown(c, button);
        }
    }

    @Override
    public void mousemove(Coord c) {
        if (panning) {
            double ppg = this.ppg();
            cx -= (c.x - pandoff.x) / ppg;
            cy -= (c.y - pandoff.y) / ppg;
            pandoff = c;
        } else {
            super.mousemove(c);
        }
    }

    @Override
    public boolean mouseup(Coord c, int button) {
        if (panning && button == 1) {
            panning = false;
            ui.grabmouse(null);
            return true;
        } else {
            return super.mouseup(c, button);
        }
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        if (rowat(c) >= 0) {
            scroll = Math.max(0, scroll + (amount > 0 ? 1 : -1));
            return true;
        } else if (!inmap(c)) {
            return false;
        }
        Coord2 w = mapworld(c);
        Coord mc = c.sub(atl).sub(mapul());
        Coord sz = mapsz();
        double nz = amount < 0 ? zoom * 1.25 : zoom / 1.25;
        zoom = Math.max(FayteConfig.worldmapZoom.min, Math.min(FayteConfig.worldmapZoom.max, nz));
        double nppg = ppg();
        cx = w.x - (mc.x - sz.x / 2.0) / nppg;
        cy = w.y - (mc.y - sz.y / 2.0) / nppg;
        return true;
    }

    @Override
    public void destroy() {
        FayteConfig.worldmapCenter.set(new Coord((int) Math.floor(cx), (int) Math.floor(cy)));
        FayteConfig.worldmapZoom.set(zoom);
        for (Defer.Future<Tex> f : texes.values()) {
            if (f.done()) {
                Tex t = f.get();
                if (t != null) {
                    t.dispose();
                }
            }
        }
        texes.clear();
        if (instance == this) {
            instance = null;
        }
        super.destroy();
    }

    private static class Coord2 {
        final double x;
        final double y;

        Coord2(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    @Override
    protected boolean compactstyle() {
        return true;
    }
}
