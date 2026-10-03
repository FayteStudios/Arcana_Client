package haven;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FayteLoginNotes extends Widget {
    private volatile List<Text> lines = new ArrayList<>();
    private int scroll = 0;
    private int total = 0;

    public FayteLoginNotes(Coord c, Coord sz, Widget parent) {
        super(c, sz, parent);
        int w = sz.x - 20;
        List<Text> lines = new ArrayList<>();
        lines.add(Window.bigtf.render("What's new in Arcana", FayteSkin.TEXT));
        try (InputStream in = FayteLoginNotes.class.getResourceAsStream("/fayte/patchnotes.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String l;
                while ((l = r.readLine()) != null) {
                    l = l.trim();
                    if (l.isEmpty()) {
                        continue;
                    } else if (l.startsWith("#")) {
                        lines.add(FayteSkin.titlef.render(l.substring(1).trim(), new Color(0xE3, 0xA8, 0x4A)));
                    } else {
                        lines.add(FayteSkin.labelf.renderwrap(
                                l.startsWith("-") ? "\u2022 " + l.substring(1).trim() : l, FayteSkin.TEXT, w));
                    }
                }
            }
        } catch (Exception e) {
            lines.add(FayteSkin.labelf.render("No patch notes found.", FayteSkin.TEXT));
        }
        int tot = 0;
        for (Text t : lines) {
            tot += t.sz().y + 4;
        }
        total = tot;
        this.lines = lines;
    }

    @Override
    public void draw(GOut g) {
        FayteSkin.box(g, Coord.z, sz, new Color(0x15, 0x18, 0x1D, 225), FayteSkin.BORDER);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - (sz.y - 16))));
        GOut cg = g.reclip(new Coord(8, 8), sz.sub(16, 16));
        int y = -scroll;
        for (Text t : lines) {
            if (y + t.sz().y > 0 && y < cg.sz.y) {
                cg.image(t.tex(), new Coord(2, y));
            }
            y += t.sz().y + 4;
        }
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        scroll += amount * 20;
        return true;
    }
}
