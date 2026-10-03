package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

public class FaytePortraitCam extends Avaview {
    public static final Coord SZ = new Coord(96, 112);
    private final Consumer<BufferedImage> done;
    private final long start = System.currentTimeMillis();
    private int frames = 0;
    private boolean finished = false;

    public FaytePortraitCam(Widget parent, long gobid, Consumer<BufferedImage> done) {
        super(Coord.z, SZ, parent, gobid, "avacam");
        this.done = done;
        lower();
    }

    @Override
    protected Color clearcolor() {
        return new Color(0x20, 0x24, 0x2A);
    }

    private static boolean blank(BufferedImage img) {
        int first = img.getRGB(0, 0);
        int diff = 0;
        for (int y = 0; y < img.getHeight(); y += 4) {
            for (int x = 0; x < img.getWidth(); x += 4) {
                if (img.getRGB(x, y) != first) {
                    diff++;
                }
            }
        }
        return diff < 20;
    }

    @Override
    public void draw(GOut g) {
        if (finished) {
            return;
        }
        super.draw(g);
        frames++;
        if (!missed && frames > 30 && System.currentTimeMillis() - start > 1500L) {
            BufferedImage img = g.getimage(Coord.z, sz);
            if (!blank(img)) {
                finished = true;
                done.accept(img);
            }
        }
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if (finished || System.currentTimeMillis() - start > 8000L) {
            if (!finished) {
                finished = true;
                done.accept(null);
            }
        }
    }

    public boolean over() {
        return finished;
    }
}
