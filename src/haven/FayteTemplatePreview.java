package haven;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class FayteTemplatePreview extends Window {
    public static final String WIKI_FILE = "wiki_entry_template.json";
    public static final String TIP_FILE = "tooltip_template.json";
    private static FayteTemplatePreview instance;
    private FayteTooltipCard card;
    private Tex cardtex;
    private double cardscale;
    private final Coord hoverc;
    private final Coord hoversz = new Coord(170, 60);
    private final Text hoverlbl = FayteSkin.labelf.render("Hover for tooltip");

    public FayteTemplatePreview(Widget parent) {
        super(new Coord(100, 100), new Coord(600, 380), parent, "Template Preview");
        justclose = true;
        FayteWikiEntry entry = null;
        String err = null;
        try {
            entry = FayteWikiEntry.parse(load(WIKI_FILE));
            card = FayteTooltipCard.parse(load(TIP_FILE));
        } catch (Exception e) {
            err = e.toString();
        }
        new FayteWikiEntry.View(Coord.z, new Coord(410, 380), this, entry);
        hoverc = new Coord(420, 0);
        if (err != null) {
            FayteMsg.say("Template error: " + err, GameUI.MsgType.BAD);
        }
    }

    public static File userfile(String name) {
        return new File(new File(Config.userhome, "fayte"), name);
    }

    public static String load(String name) throws Exception {
        File f = userfile(name);
        InputStream in =
                f.exists() ? new FileInputStream(f) : FayteTemplatePreview.class.getResourceAsStream("/fayte/" + name);
        if (in == null) {
            throw new Exception("Missing template " + name);
        }
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[4096];

            int n;
            while ((n = in.read(b)) > 0) {
                buf.write(b, 0, n);
            }
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    public static void toggle(Widget parent) {
        if (instance != null && instance.attached()) {
            instance.ui.destroy(instance);
            instance = null;
        } else {
            instance = new FayteTemplatePreview(parent);
        }
    }

    @Override
    public void cdraw(GOut g) {
        FayteSkin.box(g, hoverc, hoversz, FayteSkin.HOVER, FayteSkin.BORDER);
        g.aimage(hoverlbl.tex(), hoverc.add(hoversz.div(2)), 0.5, 0.5);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        Coord cc = xlate(c, false);
        if (card != null && cc.isect(hoverc, hoversz)) {
            if (cardtex == null || cardscale != HavenPanel.uiscale) {
                if (cardtex != null) {
                    cardtex.dispose();
                }
                cardtex = card.render(260);
                cardscale = HavenPanel.uiscale;
            }
            return cardtex;
        } else {
            return super.tooltip(c, prev);
        }
    }

    @Override
    public void destroy() {
        if (cardtex != null) {
            cardtex.dispose();
        }
        if (instance == this) {
            instance = null;
        }
        super.destroy();
    }
}
