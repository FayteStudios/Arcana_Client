package haven;

import java.awt.image.BufferedImage;

public class TexHiRes extends TexI {
    private final Coord lsz;

    public TexHiRes(BufferedImage img, Coord lsz) {
        super(img);
        this.lsz = lsz;
    }

    @Override
    public Coord sz() {
        return lsz;
    }

    @Override
    public void render(GOut g, Coord c) {
        render(g, c, Coord.z, dim, lsz);
    }

    @Override
    public void crender(GOut g, Coord c, Coord ul, Coord sz) {
        crender(g, c, ul, sz, lsz);
    }
}
