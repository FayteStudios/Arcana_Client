package haven;

import com.google.gson.Gson;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class FayteTooltipCard {
    public static final Text.Foundry titlef =
            new Text.Foundry(new Font("SansSerif", Font.BOLD, 13), FayteSkin.TEXT).aa(true);
    public static final Text.Foundry subf = new Text.Foundry(
                    new Font("SansSerif", Font.ITALIC, 11), FayteSkin.mix(FayteSkin.TEXT, FayteSkin.BORDER, 0.5))
            .aa(true);
    public static final Text.Foundry labelf = new Text.Foundry(
                    new Font("SansSerif", Font.PLAIN, 11), FayteSkin.mix(FayteSkin.TEXT, FayteSkin.BORDER, 0.45))
            .aa(true);
    public static final Text.Foundry valuef =
            new Text.Foundry(new Font("SansSerif", Font.PLAIN, 11), FayteSkin.TEXT).aa(true);
    public static final Text.Foundry notef =
            new Text.Foundry(new Font("SansSerif", Font.PLAIN, 11), FayteSkin.TEXT).aa(true);
    public String title;
    public String subtitle;
    public String icon;
    public List<String[]> rows = new ArrayList<>();
    public List<String> notes = new ArrayList<>();

    public static FayteTooltipCard parse(String json) {
        return new Gson().fromJson(json, FayteTooltipCard.class);
    }

    private static class Part {
        final Text t;
        final BufferedImage img;
        final Coord c;
        final Coord sz;

        Part(Text t, Coord c) {
            this.t = t;
            img = null;
            this.c = c;
            sz = t.sz();
        }

        Part(BufferedImage img, Coord c) {
            t = null;
            this.img = img;
            this.c = c;
            sz = Utils.imgsz(img);
        }
    }

    public Tex render(int maxw) {
        List<FayteTooltipCard.Part> parts = new ArrayList<>();
        List<Coord[]> rules = new ArrayList<>();
        int x0 = 0;
        int y = 0;
        int w = 0;
        if (icon != null) {
            BufferedImage img = Resource.load(icon).layer(Resource.imgc).img;
            parts.add(new FayteTooltipCard.Part(img, Coord.z));
            x0 = Utils.imgsz(img).x + 6;
        }
        int hy = 0;
        if (title != null) {
            Text t = titlef.render(title);
            parts.add(new FayteTooltipCard.Part(t, new Coord(x0, hy)));
            hy += t.sz().y;
            w = Math.max(w, x0 + t.sz().x);
        }
        if (subtitle != null) {
            Text t = subf.render(subtitle);
            parts.add(new FayteTooltipCard.Part(t, new Coord(x0, hy)));
            hy += t.sz().y;
            w = Math.max(w, x0 + t.sz().x);
        }
        for (FayteTooltipCard.Part p : parts) {
            if (p.img != null) {
                hy = Math.max(hy, p.sz.y);
            }
        }
        y = hy;
        if (rows != null && !rows.isEmpty()) {
            y += 4;
            rules.add(new Coord[] {new Coord(0, y), null});
            y += 5;
            List<Text[]> rr = new ArrayList<>();
            int lw = 0;

            for (String[] r : rows) {
                Text l = labelf.render(r.length > 0 ? r[0] : "");
                Text v = valuef.render(r.length > 1 ? r[1] : "");
                rr.add(new Text[] {l, v});
                lw = Math.max(lw, l.sz().x);
            }
            for (Text[] r : rr) {
                parts.add(new FayteTooltipCard.Part(r[0], new Coord(0, y)));
                parts.add(new FayteTooltipCard.Part(r[1], new Coord(lw + 10, y)));
                w = Math.max(w, lw + 10 + r[1].sz().x);
                y += Math.max(r[0].sz().y, r[1].sz().y);
            }
        }
        if (notes != null && !notes.isEmpty()) {
            y += 4;
            rules.add(new Coord[] {new Coord(0, y), null});
            y += 5;
            int nw = Math.max(120, Math.min(maxw, Math.max(w, 200)));

            for (String n : notes) {
                Text t = notef.renderwrap(n, nw);
                parts.add(new FayteTooltipCard.Part(t, new Coord(0, y)));
                w = Math.max(w, t.sz().x);
                y += t.sz().y + 2;
            }
        }
        final Coord lsz = new Coord(Math.max(w, 1), Math.max(y, 1));
        double s = Math.max(1.0, HavenPanel.uiscale);
        BufferedImage buf = TexI.mkbuf(new Coord((int) Math.ceil(lsz.x * s), (int) Math.ceil(lsz.y * s)));
        Graphics2D g = buf.createGraphics();
        Utils.AA(g);
        g.setColor(FayteSkin.BORDER);

        for (Coord[] r : rules) {
            g.fillRect(0, (int) (r[0].y * s), buf.getWidth(), (int) Math.max(1.0, s));
        }
        for (FayteTooltipCard.Part p : parts) {
            BufferedImage img = p.t != null ? p.t.hiresimg(s) : p.img;
            g.drawImage(
                    img,
                    (int) (p.c.x * s),
                    (int) (p.c.y * s),
                    (int) Math.ceil(p.sz.x * s),
                    (int) Math.ceil(p.sz.y * s),
                    null);
        }
        g.dispose();
        return s == 1.0 ? new TexI(buf) : new TexHiRes(buf, lsz);
    }
}
