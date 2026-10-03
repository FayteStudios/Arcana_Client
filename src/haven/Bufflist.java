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

import java.awt.Color;

public class Bufflist extends Widget {
    static final Tex frame = Resource.loadtex("gfx/hud/buffs/frame");
    static final Tex cframe = Resource.loadtex("gfx/hud/buffs/cframe");
    static final Tex ameter = Resource.loadtex("gfx/hud/buffs/cbar");
    static final Coord imgoff = new Coord(6, 6);
    static final Coord ameteroff = new Coord(4, 52);
    static final Coord cmeteroff = new Coord(20, 20), cmeterul = new Coord(-20, -20), cmeterbr = new Coord(20, 20);
    static final int margin = 2;
    static final int num = 15;
    static final Coord FCELL = new Coord(36, 36);
    static final int FGAP = 3;
    
    @RName("buffs")
    public static class $_ implements Factory {
	public Widget create(Coord c, Widget parent, Object[] args) {
	    return(new Bufflist(c, parent));
	}
    }
    
    public Bufflist(Coord c, Widget parent) {
	super(c, new Coord((num * frame.sz().x) + ((num - 1) * margin), cframe.sz().y), parent);
    }
    
    private static Coord cell() {
	return(FayteSkin.on() ? FCELL : frame.sz());
    }

    private static int step() {
	return(FayteSkin.on() ? (FCELL.x + FGAP) : (frame.sz().x + margin));
    }

    private void fsize(int n) {
	Coord want;
	if(FayteSkin.on())
	    want = new Coord((Math.max(n, 1) * step()) - FGAP, FCELL.y);
	else
	    want = new Coord((num * frame.sz().x) + ((num - 1) * margin), cframe.sz().y);
	if(!want.equals(sz))
	    sz = want;
    }

    private final java.util.Map<Buff, Boolean> acked = new java.util.WeakHashMap<Buff, Boolean>();
    private final java.util.Map<Buff, Boolean> told = new java.util.WeakHashMap<Buff, Boolean>();

    private static double left(Buff b, long now) {
	if(b.cmeter < 0)
	    return(-1);
	double m = b.cmeter / 100.0;
	if(b.cticks >= 0) {
	    double ot = b.cticks * 0.06;
	    double pt = ((double)(now - b.gettime)) / 1000.0;
	    m *= (ot - pt) / ot;
	}
	return(Utils.clip(m, 0.0, 1.0));
    }

    private boolean alarming(Buff b, long now) {
	try {
	    if(!FayteModules.TIMERS.on() || !FayteTimers.alerting(b.tooltip()))
		return(false);
	} catch(Loading e) {
	    return(false);
	}
	double m = left(b, now);
	return((m >= 0) && (m <= 0.1) && !acked.containsKey(b));
    }

    private Buff at(Coord c) {
	int i = 0;
	synchronized(ui.sess.glob.buffs) {
	    for(Buff b : ui.sess.glob.buffs.values()) {
		if(!b.major)
		    continue;
		if(c.isect(new Coord(i * step(), 0), cell()))
		    return(b);
		if(++i >= num)
		    break;
	    }
	}
	return(null);
    }

    public boolean mousedown(Coord c, int button) {
	if(!FayteSkin.on() || !FayteModules.TIMERS.on() || (button != 1))
	    return(super.mousedown(c, button));
	Buff b = at(c);
	if(b == null)
	    return(false);
	if(ui.modctrl) {
	    try {
		String bn = b.tooltip();
		boolean on = FayteTimers.togglealert(bn);
		FayteMsg.say((on ? "Will alert when this runs out: " : "No alert for: ") + bn);
	    } catch(Loading e) {}
	} else if(alarming(b, System.currentTimeMillis())) {
	    acked.put(b, true);
	}
	return(true);
    }

    private void fdraw(GOut g) {
	int i = 0;
	long now = System.currentTimeMillis();
	Color bar = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.7);
	synchronized(ui.sess.glob.buffs) {
	    for(Buff b : ui.sess.glob.buffs.values()) {
		if(!b.major)
		    continue;
		Coord bc = new Coord(i * step(), 0);
		FayteSkin.box(g, bc, FCELL, FayteSkin.PANEL, FayteSkin.BORDER);
		try {
		    Tex img = b.res.get().layer(Resource.imgc).tex();
		    Coord es = img.sz();
		    double f = Math.min(1.0, (double)(FCELL.x - 8) / Math.max(es.x, es.y));
		    Coord s = new Coord(Math.max(1, (int)(es.x * f)), Math.max(1, (int)(es.y * f)));
		    g.image(img, bc.add(FCELL.sub(s).div(2)), s);
		    if(b.nmeter >= 0) {
			Tex ntext = b.nmeter();
			g.image(ntext, bc.add(FCELL).sub(ntext.sz()).sub(2, 2));
		    }
		} catch(Loading e) {}
		if(b.ameter >= 0) {
		    g.chcolor(FayteSkin.mix(FayteSkin.PANEL, FayteSkin.TEXT, 0.6));
		    g.frect(bc.add(2, 2), new Coord(((FCELL.x - 4) * Utils.clip(b.ameter, 0, 100)) / 100, 2));
		}
		if(b.cmeter >= 0) {
		    double m = b.cmeter / 100.0;
		    if(b.cticks >= 0) {
			double ot = b.cticks * 0.06;
			double pt = ((double)(now - b.gettime)) / 1000.0;
			m *= (ot - pt) / ot;
		    }
		    m = Utils.clip(m, 0.0, 1.0);
		    g.chcolor(bar);
		    g.frect(bc.add(2, FCELL.y - 5), new Coord((int)((FCELL.x - 4) * m), 3));
		}
		String bn = null;
		try {
		    bn = b.tooltip();
		} catch(Loading e) {}
		if(FayteModules.TIMERS.on() && (bn != null) && FayteTimers.alerting(bn)) {
		    g.chcolor(0xF0, 0xDA, 0x9A, 255);
		    g.frect(bc.add(FCELL.x - 7, 3), new Coord(4, 4));
		    if(alarming(b, now)) {
			if(!told.containsKey(b)) {
			    told.put(b, true);
			    FayteMsg.say(bn + " is running out", GameUI.MsgType.INFO);
			}
			if((now / 400) % 2 == 0) {
			    g.chcolor(0xD0, 0x50, 0x40, 120);
			    g.frect(bc, FCELL);
			}
		    }
		}
		g.chcolor();
		if(++i >= num)
		    break;
	    }
	}
	fsize(i);
    }

    public void draw(GOut g) {
	if(FayteSkin.on()) {
	    fdraw(g);
	    return;
	}
	fsize(num);
	int i = 0;
	int w = frame.sz().x + margin;
	long now = System.currentTimeMillis();
	synchronized(ui.sess.glob.buffs) {
	    for(Buff b : ui.sess.glob.buffs.values()) {
		if(!b.major)
		    continue;
		Coord bc = new Coord(i * w, 0);
		if(b.ameter >= 0) {
		    g.image(cframe, bc);
		    g.image(ameter, bc.add(ameteroff), bc.add(ameteroff), new Coord((b.ameter * ameter.sz().x) / 100, ameter.sz().y));
		} else {
		    g.image(frame, bc);
		}
		try {
		    Tex img = b.res.get().layer(Resource.imgc).tex();
		    g.image(img, bc.add(imgoff));
		    if(b.nmeter >= 0) {
			Tex ntext = b.nmeter();
			g.image(ntext, bc.add(imgoff).add(img.sz()).add(ntext.sz().inv()).add(-1, -1));
		    }
		    if(b.cmeter >= 0) {
			double m = b.cmeter / 100.0;
			if(b.cticks >= 0) {
			    double ot = b.cticks * 0.06;
			    double pt = ((double)(now - b.gettime)) / 1000.0;
			    m *= (ot - pt) / ot;
			}
			m = Utils.clip(m, 0.0, 1.0);
			g.chcolor(255, 255, 255, 128);
			g.prect(bc.add(imgoff).add(cmeteroff), cmeterul, cmeterbr, Math.PI * 2 * m);
			g.chcolor();
		    }
		} catch(Loading e) {}
		if(++i >= num)
		    break;
	    }
	}
    }
    
    private long hoverstart;
    private Tex shorttip, longtip;
    private String tipped;
    public Object tooltip(Coord c, Widget prev) {
	long now = System.currentTimeMillis();
	if(prev != this)
	    hoverstart = now;
	int i = 0;
	int w = step();
	synchronized(ui.sess.glob.buffs) {
	    for(Buff b : ui.sess.glob.buffs.values()) {
		if(!b.major)
		    continue;
		Coord bc = new Coord(i * w, 0);
		if(c.isect(bc, cell())) {
		    String tt = b.tooltip();
		    if(tipped != tt)
			shorttip = longtip = null;
		    tipped = tt;
		    try {
			if(now - hoverstart < 1000) {
			    if(shorttip == null)
				shorttip = Text.render(tt).tex();
			    return(shorttip);
			} else {
			    if(longtip == null) {
				String text = RichText.Parser.quote(tt);
				Resource.Pagina pag = b.res.get().layer(Resource.pagina);
				if(pag != null)
				    text += "\n\n" + pag.text;
				longtip = RichText.render(text, 200).tex();
			    }
			    return(longtip);
			}
		    } catch(Loading e) {
			return("...");
		    }
		}
		if(++i >= num)
		    break;
	    }
	}
	return(null);
    }
}
