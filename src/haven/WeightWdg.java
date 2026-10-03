package haven;

import java.awt.*;
import java.awt.event.KeyEvent;

public class WeightWdg extends Window {
    static final Tex bg = Resource.loadtex("gfx/hud/bgtex");

    private Tex label;

    public WeightWdg(Coord c, Widget parent) {
	super(c,  Coord.z, parent, "weightwdg");
	this.cap = null;
	sz = new Coord(100, 30);
    }

    public void update(int weight){
	if(label != null){
	    label.dispose();
	}

	int cap = 25000;
	Glob.CAttr ca = ui.sess.glob.cattr.get("carry");
	if(ca != null)
	    cap = ca.comp;
	Color color = (weight > cap)? Color.RED:Color.WHITE;

	label = Text.render(String.format("Weight: %.2f/%.2f kg", weight / 1000.0, cap / 1000.0), color).tex();
	sz = label.sz().add(Window.swbox.bisz()).add(4,0);
    }

    @Override
    public void tick(double dt) {
	boolean want = Config.weight_wdg && !FayteSkin.on();
	if(want != visible){
	    show(want);
	}
    }

    @Override
    public void draw(GOut g) {
	if(FayteSkin.on()) {
	    FayteSkin.panel(g, Coord.z, sz, "weight");
	    if(label != null)
		g.aimage(label, sz.div(2), 0.5, 0.5);
	    return;
	}
	Coord s = bg.sz();
	for(int y = 0; (y * s.y) < sz.y; y++) {
	    for(int x = 0; (x * s.x) < sz.x; x++) {
		g.image(bg, new Coord(x * s.x, y * s.y));
	    }
	}

	if(label != null){
	    g.aimage(label, sz.div(2), 0.5, 0.5);
	}

	Window.swbox.draw(g, Coord.z, this.sz);
    }

    @Override
    public boolean type(char key, KeyEvent ev) {
	return false;
    }
}
