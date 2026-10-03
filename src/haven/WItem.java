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

import static haven.ItemInfo.find;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WItem extends Widget implements DTarget {
    public static final Resource missing = Resource.load("gfx/invobjs/missing");
    private static final Coord hsz = new Coord(24, 24);//Inventory.sqsz.div(2);
    private static final Color MATCH_COLOR = new Color(96, 255, 255, 128);
    public static final Color CARAT_COLOR = new Color(192, 160, 0);
    public final GItem item;
    private Tex ltex = null;
    private Tex mask = null;
    private Resource cmask = null;
    private long ts = 0;
    public Coord server_c;
    public long flashat = 0;
    private long gobbleUpdateTime = 0;

    public WItem(Coord c, Widget parent, GItem item) {
	super(c, Inventory.sqsz, parent);
	this.item = item;
    }
    
    public WItem(Coord c, Widget parent, GItem item, Coord server_c) {
	this(c, parent, item);
        this.server_c = server_c;
    }
    
    private static Coord upsize(Coord sz) {
	int w = sz.x, h = sz.y;
	if((w % Inventory.sqsz.x) != 0)
	    w = Inventory.sqsz.x * ((w / Inventory.sqsz.x) + 1);
	if((h % Inventory.sqsz.y) != 0)
	    h = Inventory.sqsz.y * ((h / Inventory.sqsz.y) + 1);
	return(new Coord(w, h));
    }
    
    public void drawmain(GOut g, Tex tex) {
	g.image(tex, Coord.z);
	if(tex != ltex) {
	    resize(upsize(tex.sz()));
	    ltex = tex;
	}
    }
    
    public static BufferedImage rendershort(List<ItemInfo> info) {
	ItemInfo.Name nm = find(ItemInfo.Name.class, info);
	if(nm == null)
	    return(null);
	BufferedImage img = nm.str.img;
	Alchemy ch = find(Alchemy.class, info);
	if(ch != null)
	    img = ItemInfo.catimgsh(5, img, ch.smallmeter(),
		    Text.std.renderf("(%d%% pure)", (int) (ch.a[0] * 100)).img);
	return(img);
    }
    
    public static BufferedImage shorttip(List<ItemInfo> info) {
	BufferedImage img = rendershort(info);
	ItemInfo.Contents cont = find(ItemInfo.Contents.class, info);
	if(cont != null) {
	    BufferedImage rc = rendershort(cont.sub);
	    if((img != null) && (rc != null))
		img = ItemInfo.catimgs(0, img, rc);
	    else if((img == null) && (rc != null))
		img = rc;
	}
	if(img == null)
	    return(null);
	return(img);
    }
    
    public static BufferedImage longtip(GItem item, List<ItemInfo> info) {
	return(longtip(item, info, null));
    }

    public static BufferedImage longtip(GItem item, List<ItemInfo> info, String label) {
	if(FayteTip.on()) {
	    BufferedImage fimg = FayteTip.longtip(info, label);
	    if((ItemInfo.find(GobbleInfo.class, info) == null) && (ItemInfo.find(FoodInfo.class, info) == null)) {
		Resource.Pagina fpg = item.res.get().layer(Resource.pagina);
		if((fpg != null) && (fimg != null))
		    fimg = ItemInfo.catimgs(5, fimg, RichText.render(fpg.text, 200).img);
	    }
	    return(fimg);
	}
	BufferedImage img = ItemInfo.longtip(info);
	Resource.Pagina pg = item.res.get().layer(Resource.pagina);
	if(pg != null)
	    img = ItemInfo.catimgs(5, img, RichText.render(pg.text, 200).img);
	return(img);
    }
    
    public BufferedImage longtip(List<ItemInfo> info) {
	return(longtip(item, info, namelabel()));
    }

    private String namelabel() {
	return(FayteLabels.on() ? FayteLabels.get(FayteLabels.itemkey(this)) : null);
    }

    private BufferedImage withlabel(BufferedImage img) {
	String l = namelabel();
	if((img == null) || (l == null))
	    return(img);
	return(ItemInfo.catimgs(0, img, FayteTip.label(l)));
    }

    public class ItemTip implements Indir<Tex> {
	private final TexI tex;
	
	public ItemTip(BufferedImage img) {
	    if(img == null)
		throw(new Loading());
	    tex = new TexI(img);
	}
	
	public GItem item() {
	    return(item);
	}
	
	public Tex get() {
	    return(tex);
	}
    }
    
    public class ShortTip extends ItemTip {
	public ShortTip(List<ItemInfo> info) {super(FayteFoodNote.tip(WItem.this, withlabel(shorttip(info))));}
    }
    
    public class LongTip extends ItemTip {
	public LongTip(List<ItemInfo> info) {super(FayteFoodNote.tip(WItem.this, longtip(info)));}
    }
    
    private long hoverstart;
    private ItemTip shorttip = null, longtip = null;
    private List<ItemInfo> ttinfo = null;
    private int labelver = -1;
    public Object tooltip(Coord c, Widget prev) {
	long now = System.currentTimeMillis();
	if (prev != this) {
	    if(prev instanceof WItem) {
		long ps = ((WItem)prev).hoverstart;
		if(now - ps < 1000)
		    hoverstart = now;
		else
		    hoverstart = ps;
	    } else {
		hoverstart = now;
	    }
	}
	try {
	    if(item == null){return "...";}
	    List<ItemInfo> info = item.info();
	    if(info.size() < 1)
		return(null);
	    if((info != ttinfo) || (labelver != FayteLabels.version)) {
		shorttip = longtip = null;
		ttinfo = info;
		labelver = FayteLabels.version;
	    }
	    if(now - hoverstart < 1000) {
		if(shorttip == null)
		    shorttip = new ShortTip(info);
		return(shorttip);
	    } else {
		if((longtip == null) || ts < GItem.infoUpdated){
		    ts = GItem.infoUpdated;
		    longtip = new LongTip(info);
		}
		return(longtip);
	    }
	} catch(Loading e) {
	    return("...");
	}
    }

    public abstract class AttrCache<T> {
	private List<ItemInfo> forinfo = null;
	private T save = null;
	
	public T get() {
	    try {
		List<ItemInfo> info = item.info();
		if(info != forinfo || save == null) {
		    save = find(info);
		    forinfo = info;
		}
	    } catch(Loading e) {
		return(null);
	    }
	    return(save);
	}
	public void reset(){
	    save = null;
	}
	protected abstract T find(List<ItemInfo> info);
    }
    
    public final AttrCache<Color> olcol = new AttrCache<Color>() {
	protected Color find(List<ItemInfo> info) {
	    GItem.ColorInfo cinf = ItemInfo.find(GItem.ColorInfo.class, info);
	    return((cinf == null)?null:cinf.olcol());
	}
    };

    public final AttrCache<Tex> itemnum = new AttrCache<Tex>() {
	protected Tex find(List<ItemInfo> info) {
	    GItem.NumberInfo ninf = ItemInfo.find(GItem.NumberInfo.class, info);
	    if(ninf == null) return(null);
	    return(new TexI(Utils.outline2(Text.render(Integer.toString(ninf.itemnum()), Color.WHITE).img, Color.DARK_GRAY)));
	}
    };
    
    public final AttrCache<Tex> heurnum = new AttrCache<Tex>() {
	protected Tex find(List<ItemInfo> info) {
	    String num= ItemInfo.getCount(info);
	    if(num == null) return(null);
	    return(new TexI(Utils.outline2(Text.render(num, Color.WHITE).img, Color.DARK_GRAY)));
	}
    };
    
    public final AttrCache<List<Integer>> heurmeter = new AttrCache<List<Integer>>() {
	protected List<Integer> find(List<ItemInfo> info) {
	    return ItemInfo.getMeters(info);
	}
    };

    public final AttrCache<Double> gobblemeter = new AttrCache<Double>() {
	protected Double find(List<ItemInfo> info) {
	    return ItemInfo.getGobbleMeter(info);
	}
    };

    public final AttrCache<String> contentName = new AttrCache<String>() {
	protected String find(List<ItemInfo> info) {
	    return ItemInfo.getContent(info);
	}
    };

    public final AttrCache<String> vesselName = new AttrCache<String>() {
	protected String find(List<ItemInfo> info) {
	    return FayteContents.vessel(info);
	}
    };

    public final AttrCache<Float> carats = new AttrCache<Float>() {
	protected Float find(List<ItemInfo> info) {
	    return ItemInfo.getCarats(info);
	}
    };

    public final AttrCache<Tex> carats_tex = new AttrCache<Tex>() {
	protected Tex find(List<ItemInfo> info) {
	    float c = carats.get();
	    if(c > 0){
		return(new TexI(Utils.outline2(Text.render(String.format("%.2f",c), CARAT_COLOR).img, Color.DARK_GRAY)));
	    }
	    return null;
	}
    };
    
    public void draw(GOut g) {
	try {
	    Resource res = item.res.get();
                    if(res == null || res.layer(Resource.imgc) == null)
                        throw new Loading("Somehow the resource is null!");
	    Tex tex = res.layer(Resource.imgc).tex();
	    drawmain(g, tex);
	    if(FayteContents.on()) {
		String vn = vesselName.get();
		if(vn != null) {
		    Tex tt = FayteContents.tint(res, vn);
		    if(tt != null)
			g.image(tt, Coord.z);
		}
	    }
	    draw_highlight(g, res, tex);
	    if(item.num >= 0) {
		g.atext(Integer.toString(item.num), tex.sz(), 1, 1);
	    } else if(itemnum.get() != null) {
		g.aimage(itemnum.get(), tex.sz(), 1, 1);
	    } else if(carats_tex.get() != null) {
		g.aimage(carats_tex.get(), tex.sz(), 1, 1);
	    } else if(heurnum.get() != null) {
		g.aimage(heurnum.get(), tex.sz(), 1, 1);
	    }
	    if(item.meter > 0) {
		double a = ((double)item.meter) / 100.0;
		int r = (int) ((1-a)*255);
		int gr = (int) (a*255);
		Coord s2 = sz.sub(0, 4);
		g.chcolor(r, gr, 0, 255);
		Coord bsz = new Coord(4, (int) (a*s2.y));
		g.frect(s2.sub(bsz).sub(4,0), bsz);
		g.chcolor();
		FayteProgress.draw(g, this);
	    }
	    FayteFeastWnd.drawitem(g, this);
	    FayteAbacus.drawitem(g, this);
	    if(FayteStationPanel.hl == this) {
		g.chcolor(0xF0, 0xD0, 0x48, 255);
		g.rect(Coord.z, sz.sub(1, 1));
		g.rect(new Coord(1, 1), sz.sub(3, 3));
		g.chcolor();
	    }
	    checkContents(g);
	    heurmeters(g);
	    drawpurity(g);
	    if(flashat > 0) {
		long el = System.currentTimeMillis() - flashat;
		if(el > 3000) {
		    flashat = 0;
		} else {
		    int a = (int)(90 + 110 * (0.5 + 0.5 * Math.cos(el / 1000.0 * Math.PI * 4)));
		    g.chcolor(0xF0, 0xD0, 0x48, a / 3);
		    g.frect(Coord.z, sz);
		    g.chcolor(0xF0, 0xD0, 0x48, a);
		    g.rect(Coord.z, sz.sub(1, 1));
		    g.rect(new Coord(1, 1), sz.sub(3, 3));
		    g.chcolor();
		}
	    }
	    item.testMatch();
	} catch(Loading e) {
	    missing.loadwait();
	    g.image(missing.layer(Resource.imgc).tex(), Coord.z, sz);
	}
    }

    private void draw_highlight(GOut g, Resource res, Tex tex) {
	Color col = olcol.get();
	if(col == null && item.matched && GItem.filter != null){
	    col = MATCH_COLOR;
	}
	if(col != null) {
	    if(cmask != res) {
		mask = null;
		if(tex instanceof TexI)
		    mask = ((TexI)tex).mkmask();
		cmask = res;
	    }
	    if(mask != null) {
		g.chcolor(col);
		g.image(mask, Coord.z);
		g.chcolor();
	    }
	}
    }

    public final AttrCache<Alchemy> alch = new AttrCache<Alchemy>() {
	protected Alchemy find(List<ItemInfo> info) {
	    Alchemy alch = ItemInfo.find(Alchemy.class, info);
	    if(alch == null){
		ItemInfo.Contents cont = ItemInfo.find(ItemInfo.Contents.class, info);
		if(cont == null){return null;}
		alch = ItemInfo.find(Alchemy.class, cont.sub);
		if(alch == null){return(null);}
	    }
	    return alch;
	}
    };

    public final AttrCache<Tex> purity = new AttrCache<Tex>() {
	protected Tex find(List<ItemInfo> info) {
	    Alchemy a = alch.get();
	    if(a != null) {
		String num = String.format("%.2f%%", 100 * a.purity());
		Color c = tryGetFoodColor(info, a);
		return (new TexI(Utils.outline2(Text.render(num, c).img, Color.DARK_GRAY)));
	    }
	    return null;
	}
    };
    
    public final AttrCache<Tex> puritymult = new AttrCache<Tex>() {
	protected Tex find(List<ItemInfo> info) {
	    Alchemy a = alch.get();
	    if(a != null) {
		String num = String.format("%.2f",1+a.purity());
		Color c = tryGetFoodColor(info, a);
		return (new TexI(Utils.outline2(Text.render(num, c).img, Color.DARK_GRAY)));
	    }
	    return null;
	}
    };
    
    private Color tryGetFoodColor(List<ItemInfo> info, Alchemy alch)
    {
	GobbleInfo food = ItemInfo.find(GobbleInfo.class, info);
	Color c = alch.color();
	if(food!=null)
	{
	    int[] means = new int[4];
	    int i_highest=-1,i_nexthighest=-1;
            int lowest_mean = Integer.MAX_VALUE;
	    for(int b = 0;b<4;b++)
	    {
		means[b]=(food.h[b]+food.l[b])/2;
                lowest_mean = Math.min(lowest_mean,means[b]);
		if(i_highest < 0 || means[i_highest] < means[b])
		{
		    i_nexthighest = i_highest;
		    i_highest = b;
		}
		else if(i_nexthighest < 0 || means[i_nexthighest] < means[b])
		{
		    i_nexthighest = b;
		}
	    }
	    if(means[i_nexthighest] < means[i_highest])
	    {
		c = Tempers.colors[i_highest];
	    }
            else if(means[i_highest] > lowest_mean)
            {
                //not completely the same: use a mix of the two colors.
                float[] c1 = Tempers.colors[i_highest].getRGBColorComponents(null);
                float[] c2 = Tempers.colors[i_nexthighest].getRGBColorComponents(null);
                float[] fc = new float[3];
                for (int i = 0; i < fc.length; i++) {
                    fc[i] = (c1[i]+c2[i])/2;
                }
                c=new Color(fc[0],fc[1],fc[2]);
            }
	}
	return c;
    }
    
    private void drawpurity(GOut g) {
	if(!Config.alwaysshowpurity && ui.modflags() == 0){return;}//show purity only when any mod key pressed
	Tex img = Config.pure_mult?puritymult.get():purity.get();
	if(img != null){
	    g.aimage(img, new Coord(0, sz.y), 0, 1);
	}
    }

    private void checkContents(GOut g) {
	if(FayteContents.on())
	    return;
	if(!Config.show_contents_icons){return;}
	String contents = contentName.get();
	if(contents == null){ return; }

	Tex tex = getContentTex(contents);
	if(tex == null){return;}

	g.image(tex, Coord.z,hsz);
    }
        
    private Tex getContentTex(String contents) {
	if(Config.contents_icons == null){ return null;}

	String name = Config.contents_icons.get(contents);
	for(Map.Entry<String, String> entry : Config.contents_icons.entrySet()) {
	    if(contents.contains(entry.getKey())){
	    	name = entry.getValue();
		break;
	    }
	}

	Tex tex = null;
	if(name != null){
	    try {
		//return Resource.loadtex(name);
		Resource res = Resource.load(name);
		tex =  new TexI(Utils.outline2(Utils.outline2(res.layer(Resource.imgc).img, Color.BLACK, true), Color.BLACK, true));
	    } catch (Loading e){
		tex =  missing.layer(Resource.imgc).tex();
	    }
	}
	return tex;
    }
        
    private void heurmeters(GOut g) {
	Coord c0 = sz.sub(0, 4);

	//process for meters in gobble mode
	if (Config.gobble_meters && UI.isCursor(UI.Cursor.GOBBLE)) {
	    Double meter = gobblemeter.get();
	    if (meter != null && meter > 0) {
		draw_meter(g, 0, c0, meter);
	    }
	} else { //process generic meters
	    List<Integer> meters = heurmeter.get();
	    if (meters == null) {
		return;
	    }

	    int k = 0;
	    for (Integer meter : meters) {
		double a = ((double) meter) / 100.0;
		draw_meter(g, k, c0, a);
		k++;
	    }
	}
    }

    private void draw_meter(GOut g, int k, Coord c0, double a) {
	int r = (int) ((1-a)*255);
	int gr = (int) (a*255);
	g.chcolor(r, gr, 0, 255);
	Coord bsz = new Coord(4, (int) (a*c0.y));
	g.frect(new Coord(bsz.x*k+1, c0.y - bsz.y), bsz);
	g.chcolor();
    }

    @Override
    public void tick(double dt) {
	if(ui.gui.gobble != null){
	    long lastUpdate = 0;
	    if(ui.gui.gobble instanceof Gobble){
		lastUpdate = ((Gobble) ui.gui.gobble).lastUpdate;
	    } else if(ui.gui.gobble instanceof OldGobble){
		lastUpdate = ((OldGobble) ui.gui.gobble).lastUpdate;
	    }
	    if(lastUpdate != gobbleUpdateTime && lastUpdate > 0){
	        gobbleUpdateTime = lastUpdate;
	        gobblemeter.reset();
	    }
	}
	super.tick(dt);
    }

    public boolean mousedown(Coord c, int btn) {
	if((btn == 2) && !ui.modmeta && !ui.modshift && !ui.modctrl) {
	    if(FayteModules.ALMANAC.on())
		FayteInspect.middleclick(this);
	    return true;
	} else if((btn == 3) && ui.modctrl && !ui.modshift && !ui.modmeta && FayteXfer.on() && FayteXfer.openall(this)) {
	    return true;
	} else if(checkXfer(btn)) {
	    return true;
	} else if(btn == 1) {
	    item.wdgmsg("take", c);
	    return true;
	} else if(btn == 3) {
	    FayteTools.itemrc(this);
	    FayteCraving.rc(this, c);
	    if(FayteLabels.on())
		FayteLabels.opened(this);
	    if(FayteModules.ALMANAC.on())
		FayteAlmanac.itemrc(this);
	    item.wdgmsg("iact", c);
	    return true;
	}
	return (false);
    }

    private boolean checkXfer(int button) {
	boolean inv = parent instanceof Inventory;
	if(inv && ui.modmeta && !ui.modshift && !ui.modctrl && (button == 1) && FayteXfer.on())
	    return(FayteXfer.samepurity(this));
	if(inv && (ui.modshift || ui.modctrl) && FayteXfer.on())
	    FayteXfer.touch((Inventory)parent);
	if(ui.modshift) {
	    if(ui.modmeta) {
		if(inv) {
		    wdgmsg("transfer-same", item, button == 3);
		    return true;
		}
	    } else if(button == 1) {
		item.wdgmsg("transfer", c);
		return true;
	    }
	} else if(ui.modctrl) {
	    if(ui.modmeta) {
		if(inv) {
		    wdgmsg("drop-same", item, button == 3);
		    return true;
		}
	    } else if(button == 1) {
		item.wdgmsg("drop", c);
		return true;
	    }
	}
	return false;
    }
    
    public boolean drop(Coord cc, Coord ul) {
	return(false);
    }
    
    public boolean iteminteract(Coord cc, Coord ul) {
	item.wdgmsg("itemact", ui.modflags());
	return(true);
    }
}
