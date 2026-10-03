package haven;

import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class FayteWikiEntry {
    public String name;
    public String category;
    public String icon;
    public String image;
    public String imagesize;
    public List<String> icons = new ArrayList<>();
    public String summary;
    public List<FayteWikiEntry.Section> sections = new ArrayList<>();
    public List<String> related = new ArrayList<>();

    public static class Section {
        public String title;
        public String lead;
        public List<String[]> rows = new ArrayList<>();
        public String text;
    }

    public static FayteWikiEntry parse(String json) {
        return new Gson().fromJson(json, FayteWikiEntry.class);
    }

    public static class View extends Widget {
        private FayteWikiEntry entry;
        private FayteEntryLayout layout = null;
        private int lw = -1;
        private int scroll = 0;
        private Coord mouse = null;
        private final Map<BufferedImage, Tex> texs = new IdentityHashMap<>();
        public Consumer<String> onlink = null;
        public boolean showmissing = false;

        public View(Coord c, Coord sz, Widget parent, FayteWikiEntry entry) {
            super(c, sz, parent);
            this.entry = entry;
        }

        public void set(FayteWikiEntry entry) {
            this.entry = entry;
            lw = -1;
            scroll = 0;
        }

        private Tex tex(BufferedImage img) {
            Tex t = texs.get(img);
            if (t == null) {
                t = new TexI(img);
                texs.put(img, t);
            }
            return t;
        }

        private FayteEntryLayout.Item linkat(Coord c) {
            if (c == null || layout == null) {
                return null;
            }
            Coord p = c.add(0, scroll);

            for (FayteEntryLayout.Item it : layout.items) {
                if (it.link != null && p.isect(it.c.sub(0, 2), it.sz.add(0, 4))) {
                    return it;
                }
            }
            return null;
        }

        @Override
        public void draw(GOut g) {
            if (lw != sz.x || layout == null) {
                layout = FayteEntryLayout.build(entry, sz.x, showmissing);
                lw = sz.x;
            }
            FayteSkin.box(g, Coord.z, sz, FayteSkin.PANEL, FayteSkin.BORDER);
            GOut cg = g.reclip(new Coord(FayteSkin.BW, FayteSkin.BW), sz.sub(FayteSkin.BW * 2, FayteSkin.BW * 2));
            Coord off = new Coord(-FayteSkin.BW, -FayteSkin.BW - scroll);
            FayteEntryLayout.Item hl = linkat(mouse);

            for (FayteEntryLayout.Item it : layout.items) {
                Coord c = it.c.add(off);
                if (c.y + it.sz.y >= 0 && c.y < cg.sz.y) {
                    switch (it.kind) {
                        case TEXT:
                            cg.image(it.t.tex(), c);
                            if (hl != null && it.link != null && it.link.equals(hl.link)) {
                                cg.chcolor(FayteText.LINK);
                                cg.frect(c.add(0, it.sz.y - 2), new Coord(it.sz.x, 1));
                                cg.chcolor();
                            }
                            break;
                        case IMAGE:
                            cg.image(tex(it.img), c, it.sz);
                            break;
                        default:
                            FayteSkin.box(cg, c, it.sz, it.fill, it.edge);
                    }
                }
            }
            super.draw(g);
        }

        @Override
        public void destroy() {
            for (Tex t : texs.values()) {
                t.dispose();
            }
            texs.clear();
            super.destroy();
        }

        @Override
        public void mousemove(Coord c) {
            mouse = c.isect(Coord.z, sz) ? c : null;
            super.mousemove(c);
        }

        @Override
        public boolean mousedown(Coord c, int button) {
            FayteEntryLayout.Item it = linkat(c);
            if (button == 1 && it != null && onlink != null) {
                onlink.accept(it.link);
                return true;
            } else {
                return super.mousedown(c, button);
            }
        }

        @Override
        public Object tooltip(Coord c, Widget prev) {
            FayteEntryLayout.Item it = linkat(c);
            return it != null ? "Open " + it.link : super.tooltip(c, prev);
        }

        @Override
        public boolean mousewheel(Coord c, int amount) {
            int total = layout == null ? 0 : layout.height;
            scroll = Math.max(0, Math.min(scroll + amount * 30, Math.max(0, total - sz.y)));
            return true;
        }
    }
}
