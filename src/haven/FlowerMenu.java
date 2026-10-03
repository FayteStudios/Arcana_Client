/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import static haven.FlowerMenu.pbgm;
import static haven.FlowerMenu.ptf;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;

import static java.lang.Math.PI;

public class FlowerMenu extends Widget {
    public static final Tex pbgl = Resource.loadtex("gfx/hud/fpl");
    public static final Tex pbgm = Resource.loadtex("gfx/hud/fpm");
    public static final Tex pbgr = Resource.loadtex("gfx/hud/fpr");
    static Color ptc = new Color(248, 240, 193);
    static Text.Foundry ptf = new Text.Foundry(new Font("SansSerif", Font.PLAIN, 12));
    static int ph = pbgm.sz().y, ppl = 8;
    FlowerMenu.Petal[] opts;
    final boolean flat = FayteSkin.on();
    static final int FROWH = FayteSkin.s(28);
    static final Text.Foundry fptf = new Text.Foundry(FayteFont.font(Font.BOLD, FayteSkin.s(13)), FayteSkin.TEXT).aa(true);
    private double fast_menu1, fast_menu2;
    private Petal autochoose = null;
    private static boolean serverbuild = false;

    @Widget.RName("sm")
    public static class $_ implements Widget.Factory {
	public Widget create(Coord c, Widget parent, Object[] args) {
	    if(((c.x == -1) && (c.y == -1)) || FayteTools.ourmenu())
		c = parent.ui.lcc;
	    String[] opts = new String[args.length];
	    for(int i = 0; i < args.length; i++)
		opts[i] = (String)args[i];
	    serverbuild = true;
	    try {
		return(new FlowerMenu(c, parent, opts));
	    } finally {
		serverbuild = false;
	    }
	}
    }

    public class Petal extends Widget {
	public String name;
	public double ta, tr;
	public int num;
	Tex text;
	double a = 1;
	boolean hov = false;
		
	public Petal(String name) {
	    super(Coord.z, Coord.z, FlowerMenu.this);
	    this.name = name;
	    if(flat) {
		text = new TexI(Utils.outline2(fptf.render(name).img, Color.BLACK));
		sz = new Coord(text.sz().x + 24, FROWH);
	    } else {
		text = new TexI(Utils.outline2(ptf.render(name, ptc).img, Utils.contrast(ptc)));
		sz = new Coord(text.sz().x + 25, ph);
	    }
            if (Config.fast_menu)
            {
              FlowerMenu.this.fast_menu1 = 0.0D;
              FlowerMenu.this.fast_menu2 = 0.0D;
            }
            else
            {
              FlowerMenu.this.fast_menu1 = 0.25D;
              FlowerMenu.this.fast_menu2 = 0.75D;
            }
	}
		
	public void move(Coord c) {
	    this.c = c.add(sz.div(2).inv());
	}
		
	public void move(double a, double r) {
	    if(flat)
		return;
	    move(Coord.sc(a, r));
	}
		
	public void mousemove(Coord c) {
	    hov = c.isect(Coord.z, sz);
	}

	public void draw(GOut g) {
	    if(flat) {
		int al = (int)(255 * a);
		java.awt.Color bg = hov ? FayteSkin.HOVER : FayteSkin.PANEL;
		g.chcolor(bg.getRed(), bg.getGreen(), bg.getBlue(), al * 235 / 255);
		g.frect(Coord.z, sz);
		g.chcolor(FayteSkin.BORDER.getRed(), FayteSkin.BORDER.getGreen(), FayteSkin.BORDER.getBlue(), al);
		FayteSkin.border(g, Coord.z, sz, 1);
		g.chcolor(255, 255, 255, al);
		g.aimage(text, new Coord(12, sz.y / 2), 0.0, 0.5);
		g.chcolor();
		return;
	    }
	    g.chcolor(255, 255, 255, (int)(255 * a));
	    g.image(pbgl, Coord.z);
	    g.image(pbgm, new Coord(pbgl.sz().x, 0), new Coord(sz.x - pbgl.sz().x - pbgr.sz().x, sz.y));
	    g.image(pbgr, new Coord(sz.x - pbgr.sz().x, 0));
	    g.image(text, sz.div(2).add(text.sz().div(2).inv()));
	}
		
	public boolean mousedown(Coord c, int button) {
	    choose(this);
	    return(true);
	}
    }

    public class Opening extends Widget.NormAnim {
	Opening() {super(FlowerMenu.this.fast_menu1);}
		
	public void ntick(double s) {
	    for(FlowerMenu.Petal p : opts) {
		p.move(p.ta, p.tr * (2 - s));
		p.a = flat ? Math.min(1.0, s * 2) : s;
	    }
	}
    }

    public class Chosen extends Widget.NormAnim {
	FlowerMenu.Petal chosen;
		
	Chosen(FlowerMenu.Petal c) {
	    super(FlowerMenu.this.fast_menu2);
	    chosen = c;
	}
		
	public void ntick(double s) {
	    for(FlowerMenu.Petal p : opts) {
		if(p == chosen) {
		    if(s > 0.6) {
			p.a = 1 - ((s - 0.6) / 0.4);
		    } else if(s < 0.3) {
			p.move(p.ta, p.tr * (1 - (s / 0.3)));
			p.a = 1;
		    }
		} else {
		    if(s > 0.3) {
			p.a = 0;
		    } else {
			p.a = 1 - (s / 0.3);
			p.move(p.ta - (s * PI), p.tr);
		    }
		}
	    }
	    if(s == 1.0)
	    ui.destroy(FlowerMenu.this);
	}
    }

    public class Cancel extends Widget.NormAnim {
	Cancel() {super(FlowerMenu.this.fast_menu1);}

	public void ntick(double s) {
	    for(FlowerMenu.Petal p : opts) {
		p.move(p.ta, p.tr * (1 + s));
		p.a = 1 - s;
	    }
	    if(s == 1.0)
	    ui.destroy(FlowerMenu.this);
	}
    }

    private static Rectangle organizeflat(Petal[] opts) {
	int w = 0;
	for(Petal p : opts)
	    w = Math.max(w, p.sz.x);
	for(int i = 0; i < opts.length; i++) {
	    opts[i].sz = new Coord(w, FROWH);
	    opts[i].c = new Coord(0, i * (FROWH - 1));
	}
	return new Rectangle(0, 0, w, (opts.length * (FROWH - 1)) + 1);
    }

    private static Rectangle organize(Petal[] opts) {
	int l = 1, p = 0, i = 0;
	int lr = -1;
	Coord min = new Coord(Integer.MAX_VALUE, Integer.MAX_VALUE);
	Coord max = new Coord(Integer.MIN_VALUE, Integer.MIN_VALUE);
	for(i = 0; i < opts.length; i++) {
	    if(lr == -1) {
		//lr = (int)(ph / (1 - Math.cos((2 * PI) / (ppl * l))));
		lr = 75 + (50 * (l - 1));
	    }
	    Petal petal = opts[i];
	    petal.ta = (PI / 2) - (p * (2 * PI / (l * ppl)));
	    petal.tr = lr;
	    if(++p >= (ppl * l)) {
		l++;
		p = 0;
		lr = -1;
	    }
	    Coord tc = Coord.sc(petal.ta, petal.tr).sub(petal.sz.div(2));
	    max.x = Math.max(max.x, tc.x + petal.sz.x);
	    max.y = Math.max(max.y, tc.y + petal.sz.y);
	    min.x = Math.min(min.x, tc.x);
	    min.y = Math.min(min.y, tc.y);
	}
	return new Rectangle(min.x, min.y, max.x-min.x, max.y-min.y);
    }

    public FlowerMenu(Coord c, Widget parent, String... options) {
	super(c, Coord.z, parent);
	FlowerMenu.Petal study = null;
	if(Config.flower_study){
	    for(int i = 0; i < options.length; i++) {
		if(options[i].equals("Study")){
		    study = new FlowerMenu.Petal(options[i]);
		    study.num = i;
		    break;
		}
	    }
	}
	if(study == null) {
	    opts = new FlowerMenu.Petal[options.length];
	    for(int i = 0; i < options.length; i++) {
		String name = options[i];
		Petal p = new Petal(name);
		p.num = i;
		boolean auto = Config.AUTOCHOOSE.containsKey(name) && Config.AUTOCHOOSE.get(name);
		boolean single = ui.modctrl && options.length == 1 && Config.singleItemCTRLChoose;
		if(!ui.modshift && (auto || single)){
		    autochoose = p;
		}
		opts[i] = p;
	    }
	    if(serverbuild) {
		opts = FayteMenus.arrange(this, opts, ui.modshift);
		FlowerMenu.Petal rp = FayteCraving.repick(opts);
		if(rp != null)
		    autopick(rp);
	    }
	} else {
	    opts = new FlowerMenu.Petal[]{study};
	}
	FayteTools.menuopened();
	if(FayteTools.fillable()) {
	    FlowerMenu.Petal fill = new FlowerMenu.Petal("Fill\u2026");
	    fill.num = -2;
	    FlowerMenu.Petal[] no = new FlowerMenu.Petal[opts.length + 1];
	    System.arraycopy(opts, 0, no, 0, opts.length);
	    no[opts.length] = fill;
	    opts = no;
	}
	fitscreen(flat ? organizeflat(opts) : organize(opts));
	ui.grabmouse(this);
	ui.grabkeys(this);
	new FlowerMenu.Opening();
    }

    private void fitscreen(Rectangle rect) {
	Coord ssz = ui.gui.sz;
	Coord wsz = new Coord(rect.width, rect.height);
	Coord wc = c.add(rect.x, rect.y);
	
	if(wc.x < 0)
	    c.x -= wc.x;
	if(wc.y < 0)
	    c.y -= wc.y;
	if(wc.x + wsz.x > ssz.x)
	    c.x -= wc.x + wsz.x - ssz.x;
	if(wc.y + wsz.y > ssz.y)
	    c.y -= wc.y + wsz.y - ssz.y;
    }

    public boolean mousedown(Coord c, int button) {
	if(!anims.isEmpty())
	    return(true);
	if(!super.mousedown(c, button))
	    choose(null);
	return(true);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "cancel") {
	    new FlowerMenu.Cancel();
	    ui.grabmouse(null);
	    ui.grabkeys(null);
	} else if(msg == "act") {
	    if(FayteModules.ALMANAC.on())
		FayteAlmanac.flowerchosen(opts[get((Integer)args[0])].name);
	    FayteAuto.flower(ui.gui, opts[get((Integer)args[0])].name);
	    new FlowerMenu.Chosen(opts[get((Integer)args[0])]);
	    ui.grabmouse(null);
	    ui.grabkeys(null);
	}
    }

    private int get(int num) {
	int i = 0;
	for(FlowerMenu.Petal p : opts){
	    if(p.num == num){
		return i;
	    }
	    i++;
	}
	return 0;
    }

    @Override
    public void tick(double dt) {
	if(autochoose != null){
	    choose(autochoose);
	    autochoose = null;
	}
	super.tick(dt);
    }

    public void draw(GOut g) {
	super.draw(g, false);
    }

    public boolean type(char key, java.awt.event.KeyEvent ev) {
	if((key >= '0') && (key <= '9')) {
	    int opt = (key == '0')?10:(key - '1');
	    if(opt < opts.length)
		choose(opts[opt]);
	    ui.grabkeys(null);
	    return(true);
	} else if(key == 27) {
	    choose(null);
	    ui.grabkeys(null);
	    return(true);
	}
	return(false);
    }

    public void autopick(Petal p) {
	autochoose = p;
    }

    public void choose(FlowerMenu.Petal option) {
	if(FayteCraving.intercept(this, option) || FayteCraving.interceptstudy(this, option))
	    return;
	if((option != null) && (option.num == -2)) {
	    wdgmsg("cl", -1);
	    FayteTools.startfill();
	    return;
	}
	if(option == null) {
	    wdgmsg("cl", -1);
	} else {
	    wdgmsg("cl", option.num, ui.modflags());
	}
    }
}
