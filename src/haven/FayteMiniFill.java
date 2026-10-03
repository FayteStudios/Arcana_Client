package haven;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class FayteMiniFill {
    private static final Map<Long, Defer.Future<Tex>> texs = new HashMap<>();
    private static Coord pgc = null;
    private static FayteMapStore.Pos ppos = null;
    private static long lastpos = 0L;

    public static boolean on() {
        return FayteModules.WORLDMAP.on() && FayteConfig.miniFill.get();
    }

    public static Tex tile(MCache map, Coord plg, Coord cg) {
        if (!on()) {
            return null;
        }
        FayteMapStore st = FayteMapStore.current();
        if (st == null || map == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (!plg.equals(pgc) || now - lastpos > 5000L) {
            pgc = plg;
            lastpos = now;
            long id;
            try {
                id = FayteMapStore.gridid(map, plg);
            } catch (Loading e) {
                id = 0L;
            }
            ppos = id == 0L ? null : st.pos(st.resolve(id));
        }
        if (ppos == null) {
            return null;
        }
        Long id = st.idat(ppos.seg, ppos.c.add(cg.sub(plg)));
        if (id == null || !FayteMapSeen.visible(st, id)) {
            return null;
        }
        Defer.Future<Tex> f;
        synchronized (texs) {
            f = texs.get(id);
            if (f == null) {
                if (texs.size() > 300) {
                    texs.clear();
                }
                final File file = st.tilefile(id);
                f = Defer.later(new Defer.Callable<Tex>() {
                    public Tex call() {
                        if (!file.exists()) {
                            return null;
                        }
                        try {
                            BufferedImage img = ImageIO.read(file);
                            return img == null ? null : new TexI(img);
                        } catch (Exception e) {
                            return null;
                        }
                    }
                });
                texs.put(id, f);
            }
        }
        try {
            return f.done() ? f.get() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
