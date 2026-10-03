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
import java.awt.Font;
import java.awt.image.*;
import java.util.*;
import static haven.PUtils.*;

public class Window extends Widget implements DTarget {
    protected static final Tex tleft = Resource.loadtex("gfx/hud/wnd/tleft");
    protected static final Tex tmain = Resource.loadtex("gfx/hud/wnd/tmain");
    protected static final Tex tright = Resource.loadtex("gfx/hud/wnd/tright");
    public static final BufferedImage[] cbtni = new BufferedImage[] {
	Resource.loadimg("gfx/hud/wnd/cbtn"),
	Resource.loadimg("gfx/hud/wnd/cbtnd"),
	Resource.loadimg("gfx/hud/wnd/cbtnh")};
    public static final BufferedImage[] lbtni = new BufferedImage[] {
	Resource.loadimg("gfx/hud/wnd/lbtn"),
	Resource.loadimg("gfx/hud/wnd/lbtnd"),
	Resource.loadimg("gfx/hud/wnd/lbtnh")};
    public static final BufferedImage[] rbtni = new BufferedImage[] {
	Resource.loadimg("gfx/hud/wnd/rbtn"),
	Resource.loadimg("gfx/hud/wnd/rbtnd"),
	Resource.loadimg("gfx/hud/wnd/rbtnh")};
    public static final BufferedImage[] obtni = new BufferedImage[] {
	    Resource.loadimg("gfx/hud/wnd/obtn"),
	    Resource.loadimg("gfx/hud/wnd/obtnd"),
	    Resource.loadimg("gfx/hud/wnd/obtnh")};
    public static final BufferedImage[] gbtni = new BufferedImage[] {
	    Resource.loadimg("gfx/hud/wnd/gbtn"),
	    Resource.loadimg("gfx/hud/wnd/gbtnd"),
	    Resource.loadimg("gfx/hud/wnd/gbtnh")};
    public static final Color cc = new Color(248, 230, 190);
    public static final Text.Furnace cf = new Text.Imager(new Text.Foundry(new Font("Serif", Font.BOLD, 15), cc).aa(true)) {
	    protected BufferedImage proc(Text text) {
		return(rasterimg(blurmask2(text.img.getRaster(), 1, 1, Color.BLACK)));
	    }
	};
    public static final IBox fbox = new IBox("gfx/hud", "ftl", "ftr", "fbl", "fbr", "fl", "fr", "ft", "fb");
    public static final IBox tbox = new IBox("gfx/hud", "ttl", "ttr", "tbl", "tbr", "tl", "tr", "tt", "tb");
    public static final IBox swbox = new IBox("gfx/hud", "stl", "str", "sbl", "sbr", "sl", "sr", "st", "sb");
    public static final IBox wbox = new IBox("gfx/hud/wnd", "tl", "tr", "bl", "br", "vl", "vr", "ht", "hb");
    protected static final IBox topless = new IBox(Tex.empty, Tex.empty, wbox.cbl, wbox.cbr, wbox.bl, wbox.br, Tex.empty, wbox.bb);
    protected static final int th = tleft.sz().y, tdh = th - tmain.sz().y, tc = tdh + 18;
    private static final Coord capc = new Coord(20, th - 3);
    public Coord mrgn = new Coord(10, 10);
    public Text cap;
    private Text fcap;
    static final Text xlabel = FayteSkin.titlef.render("x");
    public boolean dt = false;
    public boolean dm = false;
    public Coord ctl, csz, atl, asz, ac;
    public Coord doff;
    protected final IButton cbtn;
    private final Collection<Widget> twdgs = new LinkedList<Widget>();

// ******************************
    private static final String OPT_POS = "_pos";
//    static Tex bg = Resource.loadtex("gfx/hud/bgtex");
//    static Tex cl = Resource.loadtex("gfx/hud/cleft");
//    static Tex cm = Resource.loadtex("gfx/hud/cmain");
//    static Tex cr = Resource.loadtex("gfx/hud/cright");
    public Coord tlo, rbo;
    public boolean justclose = false;
    protected final String name;
    @RName("wnd")
    public static class $_ implements Factory {
	public Widget create(Coord c, Widget parent, Object[] args) {
	    if(args.length < 2)
		return(new Window(c, (Coord)args[0], parent, null));
	    else
		return(new Window(c, (Coord)args[0], parent, (String)args[1]));
	}
    }

    public Window(Coord c, Coord sz, Widget parent, String cap) {
	super(c, new Coord(0, 0), parent);
	if(cap != null){
	    this.cap = cf.render(cap);
	    name = cap;
	} else {
	    this.cap = null;
	    name = null;
	}
	resize(sz);
	setfocustab(true);
	parent.setfocus(this);
	cbtn = new IButton(Coord.z, this, cbtni[0], cbtni[1], cbtni[2]) {
		public void draw(GOut g) {
		    if(FayteSkin.on()) {
			FayteSkin.button(g, sz, h, a);
			g.aimage(xlabel.tex(), sz.div(2), 0.5, 0.5);
		    } else {
			super.draw(g);
		    }
		}
	    };
	cbtn.recthit = true;
	addtwdg(cbtn);
	loadOpts();
	if("Town Bell".equals(cap))
	    WorldMapMarkers.autobell();
    }

    public Coord contentsz() {
	Coord max = new Coord(0, 0);
	for(Widget wdg = child; wdg != null; wdg = wdg.next) {
	    if(twdgs.contains(wdg))
		continue;
	    if(!wdg.visible)
		continue;
	    Coord br = wdg.c.add(wdg.sz);
	    if(br.x > max.x)
		max.x = br.x;
	    if(br.y > max.y)
		max.y = br.y;
	}
	return(max.sub(1, 1));
    }

    public static final int CTH = FayteSkin.s(22);
    public static final Text.Foundry ctf = new Text.Foundry(new Font("SansSerif", Font.BOLD, FayteSkin.s(13)), FayteSkin.TEXT).aa(true);
    public static final Text.Foundry cif = new Text.Foundry(new Font("SansSerif", Font.BOLD, FayteSkin.s(12)), FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.85)).aa(true);
    public static final Text.Foundry bigtf = new Text.Foundry(new Font("SansSerif", Font.PLAIN, FayteSkin.s(15)), FayteSkin.TEXT).aa(true);
    private Tex ccapt = null;
    private String ccaps = null;
    private Tex cinfot = null;
    private Coord infoul = null, infosz = null;
    private static final java.awt.Color[] EDGES = {
	new java.awt.Color(0xD0, 0x9A, 0x3A), new java.awt.Color(0x9A, 0x5C, 0xC8), new java.awt.Color(0x3A, 0xA8, 0xA0),
	new java.awt.Color(0xC8, 0x5C, 0x8C), new java.awt.Color(0x8C, 0xA8, 0x3A), new java.awt.Color(0x5C, 0x7C, 0xC8)
    };
    private boolean laidcompact = false;
    public boolean pinned = false;
    private Coord cbtnsz = null;
    private Text ccap = null;
    private Text cinfo = null;
    private String cinfos = null;

    public boolean compact() {
	return(FayteSkin.on() && (cap != null) && (compactstyle() || hasinv()));
    }

    protected boolean compactstyle() {
	return(false);
    }

    public String info() {
	return(null);
    }

    public String infotip() {
	return(null);
    }

    public String fkey() {
	for(Widget ch = child; ch != null; ch = ch.next) {
	    if(ch instanceof Inventory)
		return(((Inventory)ch).fkey);
	}
	return(null);
    }

    public Object tooltip(Coord c, Widget prev) {
	if(laidcompact && (infoul != null) && c.isect(infoul, infosz)) {
	    String t = infotip();
	    if(t != null) {
		String[] ls = t.split("\n");
		java.awt.image.BufferedImage[] imgs = new java.awt.image.BufferedImage[ls.length];
		for(int k = 0; k < ls.length; k++)
		    imgs[k] = bigtf.render(ls[k]).img;
		return(new TexI(ItemInfo.catimgs(2, imgs)));
	    }
	}
	if(laidcompact && (c.y < ctl.y) && (FayteLabels.on()) && (fkey() != null))
	    return("Right-click the title to name this container");
	Object ret = super.tooltip(c, prev);
	return((ret != null) ? ret : "");
    }

    public java.awt.Color edgecolor() {
	if(!hasinv())
	    return(FayteSkin.BORDER);
	String t = (name == null) ? "" : name.toLowerCase();
	if(t.equals("inventory"))
	    return(new java.awt.Color(0x4A, 0x7B, 0xD0));
	if(t.contains("backpack") || t.contains("pack"))
	    return(new java.awt.Color(0xC0, 0x48, 0x48));
	if(t.contains("belt") || t.contains("sash") || t.contains("pouch"))
	    return(new java.awt.Color(0x48, 0xA8, 0x58));
	return(EDGES[Math.abs(t.hashCode()) % EDGES.length]);
    }

    private int twleft = 0;
    private int ccapw = -1;

    private int compactminw() {
	int w = FayteSkin.BW * 2 + 12 + FayteSkin.s(48);
	for(Widget ch : twdgs) {
	    if(ch.visible)
		w += ((ch == cbtn) ? (CTH - 4) : ch.sz.x) + 2;
	}
	return(w);
    }

    protected void placetwdgs() {
	if(laidcompact) {
	    int mw = compactminw();
	    if(sz.x < mw)
		sz = new Coord(mw, sz.y);
	    int cx = sz.x - FayteSkin.BW - 2;
	    for(Widget ch : twdgs) {
		if(!ch.visible)
		    continue;
		if(ch == cbtn) {
		    if(cbtnsz == null)
			cbtnsz = cbtn.sz;
		    cbtn.sz = new Coord(CTH - 4, CTH - 4);
		}
		ch.c = xlate(new Coord(cx -= ch.sz.x + 2, FayteSkin.BW + ((CTH - ch.sz.y) / 2)), false);
	    }
	    twleft = cx;
	    return;
	}
	if((cbtnsz != null) && (cbtn != null)) {
	    cbtn.sz = cbtnsz;
	    cbtnsz = null;
	}
	int x = sz.x - 5;
	for(Widget ch : twdgs) {
	    if(ch.visible){
		ch.c = xlate(new Coord(x -= ch.sz.x + 5, tc - (ch.sz.y / 2)), false);
	    }
	}
    }

    public void addtwdg(Widget wdg) {
	twdgs.add(wdg);
	placetwdgs();
    }

    public boolean embedded = false;

    public void embed() {
	embedded = true;
	for(Widget tw : twdgs)
	    tw.hide();
	resize(asz);
    }

    public void resize(Coord sz) {
	if(embedded) {
	    int pad = FayteSkin.s(6);
	    laidcompact = false;
	    this.sz = sz.add(pad * 2, pad * 2);
	    ctl = Coord.z;
	    csz = this.sz;
	    atl = new Coord(pad, pad);
	    asz = sz;
	    ac = new Coord();
	    for(Widget ch = child; ch != null; ch = ch.next)
		ch.presize();
	    return;
	}
	if(compact()) {
	    int b = FayteSkin.BW;
	    laidcompact = true;
	    this.sz = sz.add(b * 2, (b * 2) + CTH);
	    ctl = new Coord(b, b + CTH);
	    csz = sz;
	    atl = ctl;
	    asz = sz;
	    ac = new Coord();
	    placetwdgs();
	    for(Widget ch = child; ch != null; ch = ch.next)
		ch.presize();
	    return;
	}
	laidcompact = false;
	IBox box;
	int th;
	if(cap == null){
	    box = wbox;
	    th = 0;
	} else {
	    box = topless;
	    th = Window.th;
	}
	sz = sz.add(box.bisz()).add(0, th).add(mrgn.mul(2));
	this.sz = sz;
	ctl = box.btloff().add(0, th);
	csz = sz.sub(box.bisz()).sub(0, th);
	atl = ctl.add(mrgn);
	asz = csz.sub(mrgn.mul(2));
	ac = new Coord();
	//ac = tlo.add(wbox.btloff()).add(mrgn);
	placetwdgs();
	for(Widget ch = child; ch != null; ch = ch.next)
	    ch.presize();
    }

    public Coord xlate(Coord c, boolean in) {
	if(in)
	    return(c.add(atl));
	else
	    return(c.sub(atl));
    }

    public void cdraw(GOut g) {
    }

    private void cdrawframe(GOut g) {
	int b = FayteSkin.BW;
	java.awt.Color ec = edgecolor();
	FayteSkin.box(g, Coord.z, sz, FayteSkin.PANEL, ec);
	FayteSkin.box(g, new Coord(b, b), new Coord(sz.x - (b * 2), CTH), FayteSkin.mix(FayteSkin.PANEL, ec, 0.3), null);
	String lbl = FayteLabels.on() ? FayteLabels.get(fkey()) : null;
	String capname = (lbl != null) ? lbl : name;
	int right = ((twleft > 0) ? twleft : (sz.x - b)) - 6;
	String inf = info();
	infoul = null;
	if(inf != null) {
	    if((cinfot == null) || !inf.equals(cinfos)) {
		cinfos = inf;
		cinfot = new TexI(Utils.outline2(cif.render(inf).img, java.awt.Color.BLACK));
	    }
	    if(right - cinfot.sz().x - (b + 5) >= FayteSkin.s(40)) {
		infoul = new Coord(right - cinfot.sz().x, b);
		infosz = new Coord(cinfot.sz().x, CTH);
		g.aimage(cinfot, new Coord(infoul.x, b + (CTH / 2)), 0.0, 0.5);
		right = infoul.x - 8;
	    }
	}
	int avail = Math.max(10, right - (b + 5));
	if((ccapt == null) || !capname.equals(ccaps) || (ccapw != avail)) {
	    ccaps = capname;
	    ccapw = avail;
	    java.awt.Color cc = (lbl != null) ? new java.awt.Color(0xF0, 0xDA, 0x9A) : FayteSkin.TEXT;
	    String shown = capname;
	    Text t = ctf.render(shown, cc);
	    while((t.sz().x > avail) && (shown.length() > 1)) {
		shown = shown.substring(0, shown.length() - 1);
		t = ctf.render(shown.trim() + "\u2026", cc);
	    }
	    ccapt = new TexI(Utils.outline2(t.img, java.awt.Color.BLACK));
	}
	g.aimage(ccapt, new Coord(b + 5, b + (CTH / 2)), 0.0, 0.5);
    }

    protected void fdraw(GOut g) {
	if((ctl == null) || (csz == null))
	    return;
	if(compact() != laidcompact)
	    resize(asz);
	if(laidcompact) {
	    cdrawframe(g);
	    cdraw(g.reclip(xlate(Coord.z, true), asz));
	    super.draw(g);
	    return;
	}
	if(cap != null) {
	    FayteSkin.panel(g, new Coord(0, tdh), sz.sub(0, tdh), "window");
	    if(fcap == null)
		fcap = FayteSkin.titlef.render(name);
	    FayteSkin.titlebar(g, new Coord(FayteSkin.BW, tdh + FayteSkin.BW), new Coord(sz.x - (FayteSkin.BW * 2), th - tdh - FayteSkin.BW), fcap.tex());
	} else {
	    FayteSkin.panel(g, Coord.z, sz, "window");
	}
	cdraw(g.reclip(xlate(Coord.z, true), asz));
	super.draw(g);
    }

    public void draw(GOut g) {
	if(embedded) {
	    cdraw(g.reclip(atl, asz));
	    super.draw(g);
	    return;
	}
	if(FayteSkin.on()) {
	    fdraw(g);
	    return;
	}
	g.chcolor(0, 0, 0, 160);
	if(ctl == null || csz == null){return;}
	g.frect(ctl, csz);
	g.chcolor();
	cdraw(g.reclip(xlate(Coord.z, true), asz));
	if(cap != null){
	    topless.draw(g, new Coord(0, th), sz.sub(0, th));
	    g.image(tleft, Coord.z);
	    Coord tmul = new Coord(tleft.sz().x, tdh);
	    Coord tmbr = new Coord(sz.x - tright.sz().x, th);
	    for(int x = tmul.x; x < tmbr.x; x += tmain.sz().x) {
		g.image(tmain, new Coord(x, tdh), tmul, tmbr);
	    }
	    g.image(tright, new Coord(sz.x - tright.sz().x, tdh));
	    g.image(cap.tex(), capc.sub(0, cap.sz().y));
	} else {
	    wbox.draw(g, Coord.z, sz);
	}
	/*
	if(cap != null) {
	    GOut cg = og.reclip(new Coord(0, -7), sz.add(0, 7));
	    int w = cap.tex().sz().x;
	    cg.image(cl, new Coord((sz.x / 2) - (w / 2) - cl.sz().x, 0));
	    cg.image(cm, new Coord((sz.x / 2) - (w / 2), 0), new Coord(w, cm.sz().y));
	    cg.image(cr, new Coord((sz.x / 2) + (w / 2), 0));
	    cg.image(cap.tex(), new Coord((sz.x / 2) - (w / 2), 0));
	}
	*/
	super.draw(g);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "pack") {
	    pack();
	} else if(msg == "dt") {
	    dt = (Integer)args[0] != 0;
	} else {
	    super.uimsg(msg, args);
	}
    }

    public boolean mousedown(Coord c, int button) {
	if(embedded) {
	    parent.setfocus(this);
	    super.mousedown(c, button);
	    return(true);
	}
	if(!laidcompact && (c.y < tdh) && (cap != null))
	    return(false);
	if(laidcompact && (button == 3) && (c.y < ctl.y) && FayteLabels.on() && (fkey() != null)) {
	    FayteLabels.prompt(this, fkey(), "container");
	    return(true);
	}
	parent.setfocus(this);
	raise();
	if(super.mousedown(c, button)) {
	    if(laidcompact && (button == 1) && (c.y < ctl.y))
		FayteWinWatch.consumed(this, c);
	    return(true);
	}
	if(button == 1) {
	    ui.grabmouse(this);
	    dm = true;
	    doff = c;
	}
	return(true);
    }

    public boolean mouseup(Coord c, int button) {
	if(dm) {
	    canceldm();
	    storeOpt(OPT_POS, this.c);
	} else {
	    super.mouseup(c, button);
	}
	return(true);
    }

    public void canceldm() {
	if(dm)
	    ui.grabmouse(null);
	dm = false;
    }

    private static boolean hasinv(Widget w) {
	for(Widget ch = w.child; ch != null; ch = ch.next) {
	    if((ch instanceof Inventory) || hasinv(ch))
		return(true);
	}
	return(false);
    }

    public boolean hasinv() {
	return(hasinv(this));
    }

    public void mousemove(Coord c) {
	if(dm) {
	    if(ui.grabbed() != this) {
		dm = false;
		return;
	    }
	    if(FayteHud.locked(ui) && !hasinv() && !FayteLock.free(this))
		return;
	    if(pinned)
		return;
	    this.c = WindowSnap.snap(this, this.c.add(c.add(doff.inv())));
	} else {
	    super.mousemove(c);
	}
    }

    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(sender == cbtn) {
	    if(justclose)
		ui.destroy(this);
	    else
		wdgmsg("close");
	} else {
	    super.wdgmsg(sender, msg, args);
	}
    }

    public boolean type(char key, java.awt.event.KeyEvent ev) {
	if(super.type(key, ev))
	    return(true);
	if(key == 27) {
	    if(justclose)
		ui.destroy(this);
	    else
		wdgmsg("close");
	    return(true);
	}
	return(false);
    }

    public boolean drop(Coord cc, Coord ul) {
	if(dt) {
	    wdgmsg("drop", cc);
	    return(true);
	}
	return(false);
    }

    public boolean iteminteract(Coord cc, Coord ul) {
	return(false);
    }

    private static String prof(String opt) {
	return(OPT_POS.equals(opt) ? FayteLayout.profile() + opt : opt);
    }

    public void storeOpt(String opt, String value){
	if(name == null){return;}
	Config.setWindowOpt(name+prof(opt), value);
    }
    
    public void storeOpt(String opt, Coord value){
	storeOpt(opt, value.toString());
    }
    
    public void storeOpt(String opt, boolean value){
	if(name == null){return;}
	Config.setWindowOpt(name+opt, value);
    }
    
    public Coord getOptCoord(String opt, Coord def){
	synchronized (Config.window_props) {
	    try {
		String v = Config.window_props.getProperty(name+prof(opt));
		if(v == null)
		    v = Config.window_props.getProperty(name+opt, def.toString());
		return new Coord(v);
	    } catch (Exception e){
		return def;
	    }
	}
    }
    
    public boolean getOptBool(String opt, boolean def){
	synchronized (Config.window_props) {
	    try {
		return Config.window_props.getProperty(name+opt, null).equals("true");
	    } catch (Exception e){
		return def;
	    }
	}
    }
    
    protected void loadOpts(){
	if(name == null){return;}
	c = getOptCoord(OPT_POS, c);
    }
}
