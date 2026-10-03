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

public class CheckBox extends Widget {
    public static final Tex box = Resource.loadtex("gfx/hud/chkbox");
    public static final Tex act = Resource.loadtex("gfx/hud/chkboxa");
    public static final Text.Foundry lblf = new Text.Foundry("Sans", 11);
    public boolean a = false;
    public boolean enabled = true;
    Text lbl;

    @RName("chk")
    public static class $_ implements Factory {
	public Widget create(Coord c, Widget parent, Object[] args) {
	    CheckBox ret = new CheckBox(c, parent, (String)args[0]);
	    ret.canactivate = true;
	    return(ret);
	}
    }

    private static Coord bsz() {
	if(FayteSkin.on()) {
	    int s = Math.max(box.sz().y, FayteSkin.labelf.height() + 2);
	    return(new Coord(s, s));
	}
	return(box.sz());
    }

    public CheckBox(Coord c, Widget parent, String lbl) {
	super(c, bsz(), parent);
	this.lbl = (FayteSkin.on() ? FayteSkin.labelf : lblf).render(lbl);
	Coord b = bsz();
	sz = new Coord(b.x + 2 + this.lbl.sz().x, Math.max(b.y, this.lbl.sz().y));
    }
	
    public boolean mousedown(Coord c, int button) {
	if(!enabled){return false;}
	if(button != 1)
	    return(false);
	set(!a);
	return(true);
    }
    
    public void set(boolean a) {
	this.a = a;
	changed(a);
    }

    public void draw(GOut g) {
	if(!enabled){
	    g.chcolor(128, 128, 128, 255);
	}
	Coord bb = bsz();
	g.image(lbl.tex(), new Coord(bb.x + 2, (bb.y - lbl.sz().y) / 2));
	if(FayteSkin.on()) {
	    int bw = Math.max(12, bb.y - 4);
	    Coord bs = new Coord(bw, bw);
	    Coord bc = bb.sub(bs).div(2);
	    FayteSkin.box(g, bc, bs, FayteSkin.PANEL, FayteSkin.BORDER);
	    if(a)
		FayteSkin.box(g, bc.add(3, 3), bs.sub(6, 6), FayteSkin.TEXT, null);
	    if(!enabled)
		g.chcolor(128, 128, 128, 255);
	} else {
	    g.image(a?act:box, Coord.z);
	}
	g.chcolor();
	super.draw(g);
    }
    
    public void changed(boolean val) {
	if(canactivate)
	    wdgmsg("ch", a);
    }
}
