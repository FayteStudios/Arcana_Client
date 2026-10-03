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

import java.awt.Graphics;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;

public class Button extends SIWidget {
    static final BufferedImage bl = Resource.loadimg("gfx/hud/buttons/tbtn/left");
    static final BufferedImage br = Resource.loadimg("gfx/hud/buttons/tbtn/right");
    static final BufferedImage ut = Resource.loadimg("gfx/hud/buttons/tbtn/utex");
    static final Color defcol = new Color(248, 240, 193);
    static final Text.Foundry tf = new Text.Foundry(new Font("Sans", Font.PLAIN, 11), defcol);
    public static final int h = ut.getHeight();

    public static int bh() {
	return(FayteSkin.on() ? Math.max(h, FayteSkin.labelf.height() + 12) : h);
    }
    public static final int pad = bl.getWidth() + br.getWidth();
    public Text text;
    public BufferedImage cont;
    boolean a = false;
    boolean hov = false;
    private Text flabel;
    private int flabelw = -1;
    private Tex fcont;
    private BufferedImage fcontsrc;
    public Object Info;
	
    @RName("btn")
    public static class $Btn implements Factory {
	public Widget create(Coord c, Widget parent, Object[] args) {
	    return(gamesized(new Button(c, (Integer)args[0], parent, (String)args[1])));
	}
    }
    @RName("ltbtn")
    public static class $LTBtn implements Factory {
	public Widget create(Coord c, Widget parent, Object[] args) {
	    return(gamesized(wrapped(c, (Integer)args[0], parent, (String)args[1])));
	}
    }

    private static Button gamesized(Button b) {
	if(b.sz.y > h) {
	    b.sz = new Coord(b.sz.x, Math.max(h, Utils.imgsz(b.cont).y + 4));
	    b.redraw();
	}
	return(b);
    }
	
    public static Button wrapped(Coord c, int w, Widget parent, String text) {
	Button ret = new Button(c, w, parent, tf.renderwrap(text, w - 10));
	return(ret);
    }
        
    public Button(Coord c, Integer w, Widget parent, String text) {
	super(c, new Coord(w, bh()), parent);
	this.text = tf.render(text);
	this.cont = this.text.img;
    }
        
    public Button(Coord c, Integer w, Widget parent, Text text) {
	super(c, new Coord(w, bh()), parent);
	this.text = text;
	this.cont = text.img;
    }
	
    public Button(Coord c, Integer w, Widget parent, BufferedImage cont) {
	super(c, new Coord(w, bh()), parent);
	this.cont = cont;
    }
	
    public void draw(BufferedImage buf) {
	Graphics g = buf.getGraphics();
	int iw = sz.x - pad;
	for(int x = 0; x < iw; x += ut.getWidth()) {
	    int w = Math.min(ut.getWidth(), iw - x), ix = x + bl.getWidth();
	    g.drawImage(ut, ix, 0, ix + w, ut.getHeight(), 0, 0, w, ut.getHeight(), null);
	}
	g.drawImage(bl, 0, 0, null);
	g.drawImage(br, sz.x - br.getWidth(), 0, null);
	Coord tc = sz.div(2).add(Utils.imgsz(cont).div(2).inv());
	if(a)
	    tc = tc.add(1, 1);
	g.drawImage(cont, tc.x, tc.y, null);
    }

    public void draw(GOut g) {
	if(!FayteSkin.on()) {
	    super.draw(g);
	    return;
	}
	FayteSkin.button(g, sz, hov, a);
	if(text != null) {
	    if((flabel == null) || !flabel.text.equals(text.text) || (flabelw != sz.x)) {
		flabelw = sz.x;
		flabel = FayteSkin.labelf.render(text.text);
		for(int pt = FayteSkin.s(10); ((flabel.sz().x > sz.x - 8) || (flabel.sz().y > sz.y)) && (pt >= 8); pt--)
		    flabel = new Text.Foundry(FayteFont.font(java.awt.Font.PLAIN, pt), FayteSkin.TEXT).aa(true).render(text.text);
	    }
	    Coord tc = sz.sub(flabel.sz()).div(2);
	    g.image(flabel.tex(), a ? tc.add(1, 1) : tc);
	} else if(cont != null) {
	    if(fcontsrc != cont) {
		if(fcont != null)
		    fcont.dispose();
		fcont = new TexI(cont);
		fcontsrc = cont;
	    }
	    Coord tc = sz.sub(Utils.imgsz(cont)).div(2);
	    g.image(fcont, a ? tc.add(1, 1) : tc);
	}
    }

    public void mousemove(Coord c) {
	hov = c.isect(Coord.z, sz);
	super.mousemove(c);
    }

    @Override
    public Coord contentsz() {
	return new Coord(text.sz().x + pad, bh());
    }

    public void change(String text, Color col) {
	this.text = tf.render(text, col);
	this.cont = this.text.img;
	redraw();
    }
    
    public void change(String text) {
	change(text, defcol);
    }
    
    public void change(Color col) {
	if(col == null){
	    col = defcol;
	}
	change(text.text, col);
    }

    public void click() {
	wdgmsg("activate");
    }
    
    public void uimsg(String msg, Object... args) {
	if(msg == "ch") {
	    if(args.length > 1)
		change((String)args[0], (Color)args[1]);
	    else
		change((String)args[0]);
	} else {
	    super.uimsg(msg, args);
	}
    }
    
    public boolean mousedown(Coord c, int button) {
	if(button != 1)
	    return(false);
	a = true;
	redraw();
	ui.grabmouse(this);
	return(true);
    }
	
    public boolean mouseup(Coord c, int button) {
	if(a && button == 1) {
	    a = false;
	    redraw();
	    ui.grabmouse(null);
	    if(c.isect(new Coord(0, 0), sz))
		click();
	    return(true);
	}
	return(false);
    }
}
