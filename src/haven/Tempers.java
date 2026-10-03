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

import haven.RichText.Foundry;

import java.awt.Color;
import java.awt.font.TextAttribute;
import java.awt.image.*;

import static haven.PUtils.*;

public class Tempers extends SIWidget {
    static final Foundry tmprfnd = new RichText.Foundry(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, TextAttribute.FOREGROUND, new Color(32,32,64), TextAttribute.SIZE, 12);
    public static final BufferedImage[] bg = {
	Resource.loadimg("gfx/hud/tempers/bg1"),
	Resource.loadimg("gfx/hud/tempers/bg2"),
	Resource.loadimg("gfx/hud/tempers/bg3"),
	Resource.loadimg("gfx/hud/tempers/bg4"),
	Resource.loadimg("gfx/hud/tempers/bg5"),
	Resource.loadimg("gfx/hud/tempers/bg6"),
	Resource.loadimg("gfx/hud/tempers/bg7"),
	Resource.loadimg("gfx/hud/tempers/bg8"),
	Resource.loadimg("gfx/hud/tempers/bg9"),
	Resource.loadimg("gfx/hud/tempers/bg10"),
    };
    public static final BufferedImage[] bars, sbars, fbars;
    public static final BufferedImage lcap = Resource.loadimg("gfx/hud/tempers/lcap");
    public static final BufferedImage rcap = Resource.loadimg("gfx/hud/tempers/rcap");
    public static final BufferedImage[] gbtni = {
	Resource.loadimg("gfx/hud/tempers/gbtn"),
	Resource.loadimg("gfx/hud/tempers/gbtn"),
	Resource.loadimg("gfx/hud/tempers/gbtn"),
    };
    public static final Tex crbg = Resource.loadtex("gfx/hud/tempers/crframe");
    public static final Coord boxc = new Coord(96, 0), boxsz = new Coord(339, 62);
    public static final Color[] colors = {
	new Color(255, 64, 64),
	new Color(0, 128, 255),
	new Color(255, 255, 64),
	new Color(160, 160, 160),
    };
    public static final String[] tcolors;
    static final Color softc = new Color(168, 128, 200);
    static final Color foodc = new Color(192, 160, 0);
    static final Coord[] mc = {new Coord(295, 11), new Coord(235, 11), new Coord(235, 35), new Coord(295, 35)};
    static final String[] anm = {"blood", "phlegm", "ybile", "bbile"};
    static final String[] rnm = {"Blood", "Phlegm", "Yellow Bile", "Black Bile"};
    int[] soft = new int[4], hard = new int[4];
    int[] lmax = new int[4];
    int insanity = 0;
    public boolean gavail = true;
    public Indir<Resource> cravail = null;
    Tex tt = null;
    public Widget gbtn, crimg;
    private Tex[] texts = null;

    static {
	int n = anm.length;
	BufferedImage[] b = new BufferedImage[n];
	BufferedImage[] s = new BufferedImage[n];
	BufferedImage[] f = new BufferedImage[n];
	for(int i = 0; i < n; i++) {
	    b[i] = Resource.loadimg("gfx/hud/tempers/" + anm[i]);
	    s[i] = monochromize(b[i], softc);
	    f[i] = monochromize(b[i], foodc);
	}
	bars = b;
	sbars = s;
	fbars = f;
	String[] buf = new String[colors.length];
	for(int i = 0; i < colors.length; i++)
	    buf[i] = String.format("%d,%d,%d", colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue());
	tcolors = buf;
    }
    
    public Tempers(Coord c, Widget parent) {
	super(c, imgsz(bg[0]), parent);
    }
    
    private FoodInfo lfood;
    public void tick(double dt) {
	int[] max = new int[4];
	for(int i = 0; i < 4; i++) {
	    max[i] = ui.sess.glob.cattr.get(anm[i]).comp;
	    if(max[i] == 0)
		return;
	    if(max[i] != lmax[i]) {
		redraw();
		texts = null;
		flabels = null;
		tt = null;
	    }
	}
	lmax = max;

	if(gavail && (gbtn == null)) {
	    gbtn = new IButton(Coord.z, parent, gbtni[0], gbtni[1], gbtni[2]) {
		    public void reqdestroy() {
			new NormAnim(0.25) {
			    public void ntick(double a) {
				c = new Coord(Tempers.this.c.x + ((Tempers.this.sz.x - sz.x) / 2),
					      (int)(Tempers.this.c.y + fbottom() - (a * sz.y)));
				if(a == 1.0)
				    destroy();
			    }
			};
		    }

		    public void click() {
			getparent(GameUI.class).act("gobble");
		    }

		    public void presize() {
			c = new Coord(Tempers.this.c.x + ((Tempers.this.sz.x - sz.x) / 2),
				      (int)(Tempers.this.c.y + fbottom()));
		    }

		    {
			if(!Tempers.this.visible)
			    hide();
			new NormAnim(0.25) {
			    public void ntick(double a) {
				double f = Math.abs(1.0 - (6 * Math.pow(a, 2)) + (5 * Math.pow(a, 3)));
				c = new Coord(Tempers.this.c.x + ((Tempers.this.sz.x - sz.x) / 2),
					      (int)(Tempers.this.c.y + fbottom() - (f * sz.y)));
			    }
			}.ntick(0.0);
		    }
		};
	    raise();
            this.ui.gui.updateRenderFilter();
	} else if(!gavail && (gbtn != null)) {
	    gbtn.reqdestroy();
	    gbtn = null;
	}

	if((cravail != null) && (crimg == null)) {
	    final Indir<Resource> crres = cravail;
	    crimg = new Widget(Coord.z, crbg.sz(), parent) {
		    final int xoff = ((Tempers.this.sz.x - gbtni[0].getWidth()) / 2) - 10 - sz.x;
		    Tex img = null;

		    void move(double a) {
			c = new Coord(Tempers.this.c.x + xoff,
				      (int)(Tempers.this.c.y + fbottom() + ((a - 1.0) * sz.y)));
		    }

		    public void draw(GOut g) {
			if(!FayteSkin.on())
			    g.image(crbg, Coord.z);
			try {
			    if(img == null)
				img = crres.get().layer(Resource.imgc).tex();
			    g.image(img, sz.sub(img.sz()).div(2));
			} catch(Loading l) {
			}
		    }

		    Text tip = null;
		    public Object tooltip(Coord c, Widget prev) {
			try {
			    if(tip == null)
				tip = Text.render("Craving: " + crres.get().layer(Resource.tooltip).t);
			    return(tip);
			} catch(Loading l) {
			    return("...");
			}
		    }

		    public void reqdestroy() {
			new NormAnim(0.25) {
			    public void ntick(double a) {
				move(1.0 - a);
				if(a == 1.0)
				    destroy();
			    }
			};
		    }

		    public void presize() {move(1.0);}

		    {
			if(!Tempers.this.visible)
			    hide();
			new NormAnim(0.25) {
			    public void ntick(double a) {
				double f = Math.abs(1.0 - (6 * Math.pow(a, 2)) + (5 * Math.pow(a, 3)));
				move(1.0 - f);
			    }
			}.ntick(0.0);
		    }
		};
	}

	FoodInfo food = null;
	if(ui.lasttip instanceof WItem.ItemTip) {
	    try {
		food = ItemInfo.find(FoodInfo.class, ((WItem.ItemTip)ui.lasttip).item().info());
	    } catch(Loading e) {}
	}
	if(lfood != food) {
	    lfood = food;
	    redraw();
	}
    }

    public void cravail(Indir<Resource> res) {
	if((cravail != null) && (res == null))
	    FayteGains.craving(cravail);
	if((cravail != null) && (res == null) && (ui != null))
	    FayteBuffTimers.craving(ui.gui);
	cravail = res;
	if(crimg != null) {
	    crimg.reqdestroy();
	    crimg = null;
	}
    }

    public void show() {
	super.show();
	if(gbtn != null)
	    gbtn.show();
    }

    public void hide() {
	super.hide();
	if(gbtn != null)
	    gbtn.hide();
    }

    public static WritableRaster rmeter(Raster tex, int val, int max) {
	int w = 1 + (Utils.clip(val, 0, max) * (tex.getWidth() - 1)) / Math.max(max, 1);
	WritableRaster bar = copy(tex);
	gayblit(bar, 3, new Coord(w - rcap.getWidth(), 0), rcap.getRaster(), 0, Coord.z);
	for(int y = 0; y < bar.getHeight(); y++) {
	    for(int x = w; x < bar.getWidth(); x++)
		bar.setSample(x, y, 3, 0);
	}
	return(bar);
    }

    public static WritableRaster lmeter(Raster tex, int val, int max) {
	int w = 1 + (Utils.clip(val, 0, max) * (tex.getWidth() - 1)) / Math.max(max, 1);
	WritableRaster bar = copy(tex);
	gayblit(bar, 3, new Coord(bar.getWidth() - w, 0), lcap.getRaster(), 0, Coord.z);
	for(int y = 0; y < bar.getHeight(); y++) {
	    for(int x = 0; x < bar.getWidth() - w; x++)
		bar.setSample(x, y, 3, 0);
	}
	return(bar);
    }

    private WritableRaster rfmeter(FoodInfo food, int t) {
	return(alphablit(rmeter(fbars[t].getRaster(), soft[t] + food.tempers[t], lmax[t]),
			 rmeter(sbars[t].getRaster(), soft[t], lmax[t]),
			 Coord.z));
    }

    private WritableRaster lfmeter(FoodInfo food, int t) {
	return(alphablit(lmeter(fbars[t].getRaster(), soft[t] + food.tempers[t], lmax[t]),
			 lmeter(sbars[t].getRaster(), soft[t], lmax[t]),
			 Coord.z));
    }

    public void draw(BufferedImage buf) {
	WritableRaster dst = buf.getRaster();
	blit(dst, bg[insanity].getRaster(), Coord.z);

	if(lfood != null) {
	    alphablit(dst, rfmeter(lfood, 0), mc[0]);
	    alphablit(dst, lfmeter(lfood, 1), mc[1].sub(bars[1].getWidth() - 1, 0));
	    alphablit(dst, lfmeter(lfood, 2), mc[2].sub(bars[2].getWidth() - 1, 0));
	    alphablit(dst, rfmeter(lfood, 3), mc[3]);
	} else {
	    if(soft[0] > hard[0]) alphablit(dst, rmeter(sbars[0].getRaster(), soft[0], lmax[0]), mc[0]);
	    if(soft[1] > hard[1]) alphablit(dst, lmeter(sbars[1].getRaster(), soft[1], lmax[1]), mc[1].sub(bars[1].getWidth() - 1, 0));
	    if(soft[2] > hard[2]) alphablit(dst, lmeter(sbars[2].getRaster(), soft[2], lmax[2]), mc[2].sub(bars[2].getWidth() - 1, 0));
	    if(soft[3] > hard[3]) alphablit(dst, rmeter(sbars[3].getRaster(), soft[3], lmax[3]), mc[3]);
	}

	alphablit(dst, rmeter(bars[0].getRaster(), hard[0], lmax[0]), mc[0]);
	alphablit(dst, lmeter(bars[1].getRaster(), hard[1], lmax[1]), mc[1].sub(bars[1].getWidth() - 1, 0));
	alphablit(dst, lmeter(bars[2].getRaster(), hard[2], lmax[2]), mc[2].sub(bars[2].getWidth() - 1, 0));
	alphablit(dst, rmeter(bars[3].getRaster(), hard[3], lmax[3]), mc[3]);
    }

    public void updinsanity(int n) {
	if(insanity != n) {
	    String direction = insanity > n ? "decreased" : "increased";
	    GameUI.MsgType type = insanity > n ? GameUI.MsgType.GOOD : GameUI.MsgType.BAD;
	    ui.gui.message(String.format("Your madness %s to level %d!", direction, n), type);
	    if((insanity > n) && (insanity >= 0)) {
		String obj = FayteTools.lastobject(ui.gui);
		FayteLog.log("Madness went down to " + n + "; last clicked object: " + obj + " (" + (FayteTools.lastclickage() / 1000) + " s ago)");
		if((obj != null) && obj.toLowerCase().contains("hookah") && (FayteTools.lastclickage() < 600000L)) {
		    String who = Config.currentCharName;
		    FayteTimers.restart("Hookah" + (((who != null) && !who.isEmpty()) ? (": " + who) : ""), 20L * 3600L * 1000L);
		    ui.gui.message("Hookah timer started: 20 hours until your next puff.", GameUI.MsgType.INFO);
		}
	    }
	}
	insanity = n;
	flabels = null;
	redraw();
	tt = null;
    }

    public static final Coord FSZ = new Coord(FayteSkin.s(230), 6 + 7 * FayteSkin.s(24) + 10);
    static final Color[] fcolors = {
	new Color(0xC0, 0x3A, 0x3A),
	new Color(0x3A, 0x7C, 0xC8),
	new Color(0xC8, 0xB4, 0x3A),
	new Color(0x8C, 0x8C, 0x8C),
    };
    private boolean fmode = false;
    private Text[] flabels = null;
    private Text fmad = null;

    private boolean fayte() {
	boolean on = FayteSkin.on();
	if(on != fmode) {
	    fmode = on;
	    sz = on ? FSZ : imgsz(bg[0]);
	    flabels = null;
	    redraw();
	    if(parent instanceof GameUI) {
		GameUI gui = (GameUI)parent;
		gui.resize(gui.sz);
	    }
	}
	return(on);
    }

    private static String whole(int v) {
	return(Integer.toString(v / 1000));
    }

    static final int ROWH = FayteSkin.s(24), PAD = 6, BARH = 6;
    static final int WROW = 4, IROW = 5;
    private final java.util.Map<String, Text> ftexts = new java.util.HashMap<>();

    private Text ftext(String s, Color c) {
	String k = c.getRGB() + "|" + s;
	Text t = ftexts.get(k);
	if(t == null) {
	    if(ftexts.size() > 200)
		ftexts.clear();
	    t = FayteSkin.labelf.render(s, c);
	    ftexts.put(k, t);
	}
	return(t);
    }

    private int rowy(int row) {
	return(PAD + (row * ROWH) + ((row >= WROW) ? 6 : 0));
    }

    private void frow(GOut g, int row, String left, String right, Color rc, double full, double soft, double food, Color c) {
	int y = rowy(row);
	int w = sz.x - (PAD * 2);
	Text l = ftext(left, FayteSkin.TEXT);
	g.chcolor();
	g.image(l.tex(), new Coord(PAD, y));
	g.aimage(ftext(right, rc).tex(), new Coord(sz.x - PAD, y), 1.0, 0.0);
	int by = y + l.sz().y + 1;
	g.chcolor(FayteSkin.mix(FayteSkin.PANEL, c, 0.22));
	g.frect(new Coord(PAD, by), new Coord(w, BARH));
	if(food >= 0) {
	    g.chcolor(FayteSkin.mix(FayteSkin.PANEL, FayteSkin.TEXT, 0.55));
	    g.frect(new Coord(PAD, by), new Coord((int)(Utils.clip(food, 0, 1) * w), BARH));
	}
	if(soft >= 0) {
	    g.chcolor(FayteSkin.mix(c, FayteSkin.TEXT, 0.45));
	    g.frect(new Coord(PAD, by), new Coord((int)(Utils.clip(soft, 0, 1) * w), BARH));
	}
	g.chcolor(c);
	g.frect(new Coord(PAD, by), new Coord((int)(Utils.clip(full, 0, 1) * w), BARH));
	g.chcolor(FayteSkin.BORDER);
	g.rect(new Coord(PAD, by), new Coord(w + 1, BARH + 1));
	g.chcolor();
    }

    static final Color WEIGHTC = new Color(0x9A, 0x7A, 0x52);
    static final Color INSPC = new Color(0xA8, 0x80, 0xC8);

    public int carry() {
	Glob.CAttr ca = ui.sess.glob.cattr.get("carry");
	return((ca != null && ca.comp > 0) ? ca.comp : 25000);
    }

    private void fdraw(GOut g) {
	FayteSkin.panel(g, Coord.z, sz, "tempers");
	Color dim = FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.75);
	for(int i = 0; i < 4; i++) {
	    double max = Math.max(lmax[i], 1);
	    String r = whole(hard[i]) + ((soft[i] > hard[i]) ? (" +" + whole(soft[i] - hard[i])) : "") + " / " + whole(lmax[i]);
	    double food = (lfood != null) ? ((soft[i] + lfood.tempers[i]) / max) : -1;
	    frow(g, i, rnm[i], r, dim, hard[i] / max, soft[i] / max, food, fcolors[i]);
	}
	g.chcolor(FayteSkin.BORDER);
	g.frect(new Coord(PAD, rowy(WROW) - 4), new Coord(sz.x - (PAD * 2), 1));
	g.chcolor();
	GameUI gui = ui.gui;
	int wt = (gui != null) ? gui.weight : 0;
	int cap = carry();
	frow(g, WROW, "Weight", String.format("%.2f / %.2f kg", wt / 1000.0, cap / 1000.0), (wt > cap) ? new Color(0xE0, 0x50, 0x48) : dim, (double)wt / cap, -1, -1, (wt > cap) ? new Color(0xE0, 0x50, 0x48) : WEIGHTC);
	if((gui != null) && (gui.maininv != null))
	    g.image(ftext("(" + gui.maininv.usedslots() + ")", dim).tex(), new Coord(PAD + ftext("Weight", FayteSkin.TEXT).sz().x + FayteSkin.s(10), rowy(WROW)));
	Glob.CAttr ac = ui.sess.glob.cattr.get("scap"), ar = ui.sess.glob.cattr.get("srate");
	int ins = (gui != null && gui.chrwdg != null) ? gui.chrwdg.tmexp : 0;
	int icap = (ac != null) ? ac.comp : 0;
	double rate = (ar != null) ? (3 * ar.comp / 1000.0) : 0;
	String il = String.format("Inspiration  +%.2f/s", rate);
	frow(g, IROW, il, String.format("%,d / %,d", ins, icap), dim, (icap > 0) ? ((double)ins / icap) : 0, -1, -1, INSPC);
	int my = rowy(MROW);
	g.chcolor();
	g.image(ftext("Madness", FayteSkin.TEXT).tex(), new Coord(PAD, my));
	int levels = bg.length - 1;
	int nw = 12, gap = 3;
	int nx = sz.x - PAD - (levels * (nw + gap) - gap);
	for(int i = 0; i < levels; i++) {
	    Coord nc = new Coord(nx + i * (nw + gap), my + 2);
	    g.chcolor((i < insanity) ? MADC : FayteSkin.mix(FayteSkin.PANEL, MADC, 0.22));
	    g.frect(nc, new Coord(nw, 10));
	    g.chcolor(FayteSkin.BORDER);
	    g.rect(nc, new Coord(nw + 1, 11));
	}
	g.chcolor();
    }

    static final Color MADC = new Color(0x6C, 0xB0, 0x4A);
    static final int MROW = 6;

    public int fbottom() {
	return(fmode ? sz.y : boxsz.y);
    }

    @Override
    public void draw(GOut g) {
	if(fayte()) {
	    fdraw(g);
	    return;
	}
	super.draw(g);
	if(Config.show_tempers){
	    int i;
	    if(texts == null){
		texts = new TexI[4];
		for(i = 0; i < 4; i++){
		    String str = String.format("%s / %s / %s", Utils.fpformat(hard[i], 3, 1), Utils.fpformat(soft[i], 3, 1), Utils.fpformat(lmax[i], 3, 1));
		    texts[i] = text(str);
		}
	    }
	    g.aimage(texts[0], mc[0].add(bars[0].getWidth()/2, bars[0].getHeight()/2 - 1), 0.5, 0.5);
	    g.aimage(texts[1], mc[1].add(-bars[1].getWidth()/2, bars[1].getHeight()/2 - 1), 0.5, 0.5);
	    g.aimage(texts[2], mc[2].add(-bars[2].getWidth()/2, bars[2].getHeight()/2 - 1), 0.5, 0.5);
	    g.aimage(texts[3], mc[3].add(bars[3].getWidth()/2, bars[3].getHeight()/2 - 1), 0.5, 0.5);
	}
    }

    public void upds(int[] n) {
	texts = null;
	flabels = null;
	this.soft = n;
	redraw();
	tt = null;
    }
    
    public void updh(int[] n) {
	texts = null;
	flabels = null;
	this.hard = n;
	redraw();
	tt = null;
    }
    
    public boolean mousedown(Coord c, int button) {
	if(fmode) {
	    if(button == 1 && ui.gui != null && !FayteHud.editing()) {
		if(c.y >= rowy(WROW) && c.y < rowy(IROW))
		    FayteXfer.invpack(ui.gui);
		else if(c.y >= rowy(IROW) && c.y < rowy(MROW))
		    ui.gui.fayteskills();
	    }
	    return(true);
	}
	if(bg[insanity].getRaster().getSample(c.x, c.y, 3) > 128)
	    return(true);
	return(super.mousedown(c, button));
    }

    public Object tooltip(Coord c, Widget prev) {
	if(fmode && c.y >= rowy(WROW)) {
	    if(c.y < rowy(IROW))
		return("Click: open your inventory and backpack");
	    if(c.y >= rowy(MROW))
		return("Madness level. The game only tells the client the level, not the progress toward the next one.");
	    return("Inspiration fills over time and is spent on skills. Click: open skills");
	}
	if(fmode || c.isect(boxc, boxsz)) {
	    if(tt == null) {
		StringBuilder buf = new StringBuilder();
		for(int i = 0; i < 4; i++)
		    buf.append(String.format("%s: %s/%s/%s\n", rnm[i], Utils.fpformat(hard[i], 3, 1), Utils.fpformat(soft[i], 3, 1), Utils.fpformat(lmax[i], 3, 1)));
		buf.append(String.format("Madness level: %d", insanity));
		tt = RichText.render(buf.toString(), 0).tex();
	    }
	    return(tt);
	}
	return(null);
    }
    
    public static TexI text(String str) {
	return new TexI(Utils.outline2(tmprfnd.render(str).img, new Color(240, 240, 240), false));
    }
}
