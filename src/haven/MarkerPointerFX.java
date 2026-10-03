package haven;

import java.awt.Color;

public class MarkerPointerFX extends Sprite {
    private static final Location SCALE = Location.scale(new Coord3f(1.2F, 1.2F, 1.0F));
    private static final Material.Colors COLORS = new Material.Colors(new Color(80, 200, 255));
    private static final Location XLATE = Location.xlate(new Coord3f(0.0F, 0.0F, 4.0F));
    static Resource sres = Resource.load("gfx/fx/arrow", 1);
    private Rendered fx = null;
    private double ca = 0.0;
    private final Gob.Overlay curol;
    public volatile Coord target = null;

    public MarkerPointerFX(Gob owner) {
        super(owner, sres);
        owner.ols.add(curol = new Gob.Overlay(this));
    }

    @Override
    public boolean setup(RenderList d) {
        if (fx == null) {
            FastMesh.MeshRes mres = sres.layer(FastMesh.MeshRes.class);
            fx = mres.mat.get().apply(mres.m);
        }
        Gob gob = (Gob) owner;
        if (target != null && gob.rc != null) {
            Location rot = Location.rot(Coord3f.zu, (float) (gob.a - ca));
            d.add(fx, GLState.compose(XLATE, SCALE, COLORS, rot));
        }
        return false;
    }

    @Override
    public boolean tick(int dt) {
        Gob gob = (Gob) owner;
        Coord t = target;
        if (t != null && gob.rc != null) {
            ca = gob.rc.angle(t);
        }
        return false;
    }

    @Override
    public void dispose() {
        super.dispose();
        ((Gob) owner).ols.remove(curol);
    }
}
