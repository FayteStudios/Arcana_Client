package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.imageio.ImageIO;

public class FaytePhoto extends Widget {
    private static final Color DIM = new Color(0, 0, 0, 110);
    private static final Color EDGE = new Color(0xF0, 0xEB, 0xDD, 230);
    private static FaytePhoto instance = null;
    private final String entry;
    private final Runnable done;
    private double scale = 1.0;
    private int phase = 0;
    private final List<Widget> hidden = new ArrayList<>();
    private boolean finished = false;
    private boolean hideui = Utils.getprefb("fayte_photo_hideui", true);
    private Coord off = null;
    private Coord dragstart = null;
    private Coord dragoff = null;

    public static File dir(String entry) {
        return new File(new File(FaytePaths.images(), "photos"), FaytePaths.safename(entry));
    }

    public static String relpath(String entry, File f) {
        return "photos/" + FaytePaths.safename(entry) + "/" + f.getName();
    }

    public static List<File> photos(String entry) {
        File[] fs = dir(entry).listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        List<File> l = new ArrayList<>();
        if (fs != null) {
            l.addAll(Arrays.asList(fs));
            l.sort((a, b) -> b.getName().compareTo(a.getName()));
        }
        return l;
    }

    public static void start(GameUI gui, String entry, Runnable done) {
        if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
        }
        instance = new FaytePhoto(gui, entry, done);
    }

    private FaytePhoto(GameUI gui, String entry, Runnable done) {
        super(Coord.z, gui.sz, gui);
        this.entry = entry;
        this.done = done;
        int x = gui.sz.x / 2 - 170;
        new Button(new Coord(x, 10), 110, this, "Take photo") {
            @Override
            public void click() {
                FaytePhoto.this.phase = 1;
            }
        };
        new Button(new Coord(x + 116, 10), 40, this, "\u2212") {
            @Override
            public void click() {
                FaytePhoto.this.scale = Math.max(0.4, FaytePhoto.this.scale - 0.15);
            }
        };
        new Button(new Coord(x + 160, 10), 40, this, "+") {
            @Override
            public void click() {
                FaytePhoto.this.scale = Math.min(2.2, FaytePhoto.this.scale + 0.15);
            }
        };
        new Button(new Coord(x + 206, 10), 90, this, "Cancel") {
            @Override
            public void click() {
                FaytePhoto.this.finish(null);
            }
        };
        new Button(new Coord(x + 302, 10), 120, this, hideui ? "Hide UI: on" : "Hide UI: off") {
            @Override
            public void click() {
                FaytePhoto.this.hideui = !FaytePhoto.this.hideui;
                Utils.setprefb("fayte_photo_hideui", FaytePhoto.this.hideui);
                change(FaytePhoto.this.hideui ? "Hide UI: on" : "Hide UI: off");
            }
        };
        raise();
        FayteMsg.say("Frame your photo for " + entry
                + ": move the camera as usual, resize the frame with \u2212 / +, drag the frame to move it,"
                + " then Take photo.");
    }

    private Coord fsz() {
        return new Coord((int) (480 * scale), (int) (360 * scale));
    }

    private Coord fc() {
        Coord s = fsz();
        Coord c = off != null ? off : sz.sub(s).div(2);
        return new Coord(Math.max(0, Math.min(sz.x - s.x, c.x)), Math.max(0, Math.min(sz.y - s.y, c.y)));
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (super.mousedown(c, button)) {
            return true;
        }
        if (button == 1 && !finished && c.isect(fc(), fsz())) {
            dragstart = c;
            dragoff = fc();
            ui.grabmouse(this);
            return true;
        }
        return false;
    }

    @Override
    public void mousemove(Coord c) {
        if (dragstart != null) {
            off = dragoff.add(c.sub(dragstart));
            return;
        }
        super.mousemove(c);
    }

    @Override
    public boolean mouseup(Coord c, int button) {
        if (dragstart != null && button == 1) {
            dragstart = null;
            ui.grabmouse(null);
            return true;
        }
        return super.mouseup(c, button);
    }

    private void finish(File saved) {
        if (finished) {
            return;
        }
        finished = true;
        if (saved != null) {
            FayteMsg.say("Photo saved to the Almanac entry " + entry + ".");
        }
        done.run();
    }

    @Override
    public void draw(GOut g) {
        if (finished) {
            return;
        }
        sz = parent.sz;
        Coord c = fc();
        Coord s = fsz();
        if (phase == 2) {
            phase = 0;
            BufferedImage img = g.getimage(c, s);
            unhide();
            File f = new File(dir(entry), new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".png");
            try {
                f.getParentFile().mkdirs();
                ImageIO.write(img, "PNG", f);
                finish(f);
            } catch (Exception e) {
                FayteMsg.say("Could not save the photo: " + e.getMessage(), GameUI.MsgType.BAD);
                finish(null);
            }
            return;
        }
        g.chcolor(DIM);
        g.frect(Coord.z, new Coord(sz.x, c.y));
        g.frect(new Coord(0, c.y + s.y), new Coord(sz.x, sz.y - c.y - s.y));
        g.frect(new Coord(0, c.y), new Coord(c.x, s.y));
        g.frect(new Coord(c.x + s.x, c.y), new Coord(sz.x - c.x - s.x, s.y));
        g.chcolor(EDGE);
        g.rect(c, s.add(1, 1));
        g.chcolor();
        super.draw(g);
    }

    private void unhide() {
        for (Widget w : hidden) {
            if (w.attached()) {
                w.show();
            }
        }
        hidden.clear();
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if (phase == 1) {
            GameUI gui = getparent(GameUI.class);
            for (Widget w = gui.child; w != null; w = w.next) {
                if (hideui && w != this && w != gui.map && w.visible) {
                    w.hide();
                    hidden.add(w);
                }
            }
            for (Widget w = child; w != null; w = w.next) {
                if (w.visible) {
                    w.hide();
                    hidden.add(w);
                }
            }
            phase = 2;
        }
    }

    public boolean over() {
        return finished;
    }

    public static void sweep(GameUI gui) {
        for (Widget w = gui.child; w != null; ) {
            Widget n = w.next;
            if (w instanceof FaytePhoto && ((FaytePhoto) w).over()) {
                gui.ui.destroy(w);
            }
            w = n;
        }
    }
}
