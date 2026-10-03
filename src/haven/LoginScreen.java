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
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginScreen extends Widget {
    Login cur;
    Text error;
    Window log;
    Widget btn;
    private volatile String statustext = null;
    private static final Tex fbg = Resource.loadtex("gfx/loginscr");
    static final Text.Furnace textf, texte, textfs;
    static final Tex bg = Resource.loadtex("gfx/loginscr");
    static final Tex cbox = Resource.loadtex("gfx/hud/login/cbox");
    static final Coord cboxc = new Coord((bg.sz().x - cbox.sz().x) / 2, 310);
    Text progress = null;
    AccountList accounts;
    Widget notes, authbtn;
    Coord cardc = null;

    Coord cb() {
	return((FayteSkin.on() && (cardc != null)) ? cardc : cboxc);
    }

    private static final int SIDEW = 340;
    private static final int RIGHTW = 240;

    private Coord bgsz() {
	if(!FayteSkin.on())
	    return(bg.sz());
	Coord bs = bg.sz();
	double hs = (double)(sz.y - 32) / bs.y;
	double ws = (double)(sz.x - 2 * (Math.max(SIDEW, RIGHTW) + 32)) / bs.x;
	double f = Math.max(0.5, Math.min(hs, ws));
	return(new Coord((int)(bs.x * f), (int)(bs.y * f)));
    }

    private Coord bgoff() {
	return(sz.sub(bgsz()).div(2));
    }

    Button hidebtn;

    private void flayout() {
	if(!FayteSkin.on())
	    return;
	Coord off = bgoff();
	Coord bs = bgsz();
	int lw = SIDEW;
	boolean left = off.x >= lw + 24;
	int lx = left ? off.x - lw - 16 : off.x + 8;
	int top = Math.max(8, off.y);
	if(notes != null) {
	    int nh = left ? Math.max(200, Math.min(sz.y - top - 16, bs.y)) : Math.min(300, bs.y / 2);
	    notes.c = new Coord(lx, top);
	    notes.resize(new Coord(lw, nh));
	}
	boolean right = sz.x - (off.x + bs.x) >= 250;
	int rx = right ? off.x + bs.x + 16 : off.x + bs.x - 240;
	int ry = top;
	if(authbtn != null) {
	    authbtn.c = new Coord(rx, ry);
	    ry += 25;
	}
	if(providencestate != null) {
	    providencestate.c = new Coord(rx, ry);
	    ry += 37;
	}
	if(hidebtn != null) {
	    hidebtn.c = new Coord(rx, ry);
	    ry += 37;
	}
	if(accounts != null)
	    accounts.c = new Coord(rx, ry);
	int ch = cbox.sz().y + 44;
	int cy = off.y + (bs.y * 55) / 100 - ch / 2 + 44;
	cardc = new Coord((sz.x - cbox.sz().x) / 2, Math.max(44, Math.min(cy, sz.y - ch + 36)));
	if(cur != null)
	    cur.c = cardc;
	if(btn != null)
	    btn.c = cardc.add((cbox.sz().x - btn.sz.x) / 2, 140);
    }

    Button providencestate;
	
    static {
	textf = new Text.Foundry(new Font("Sans", Font.BOLD, 16), Color.BLACK).aa(true);
	texte = new Text.Foundry(new Font("Sans", Font.BOLD, 18), new Color(255, 0, 0)).aa(true);
	textfs = new Text.Foundry(new Font("Sans", Font.BOLD, 14), Color.BLACK).aa(true);
    }
	
    public LoginScreen(Widget parent) {
	super(FayteSkin.on() ? Coord.z : parent.sz.div(2).sub(bg.sz().div(2)), FayteSkin.on() ? parent.sz : bg.sz(), parent);
	setfocustab(true);
	parent.setfocus(this);
	if(!FayteSkin.on()) {
	    new Img(Coord.z, bg, this);
	    new Img(cboxc, cbox, this);
	} else {
	    notes = new FayteLoginNotes(Coord.z, new Coord(340, 300), this);
	}

	accounts = new AccountList(Coord.z, this, 10);

        if(FayteSkin.on()) {
	    hidebtn = new Button(Coord.z, 200, this, AccountList.hidden() ? "Show account names" : "Hide account names") {
		public void click() {
		    AccountList.sethidden(!AccountList.hidden());
		    change(AccountList.hidden() ? "Show account names" : "Hide account names");
		    accounts.relabel();
		    if(cur instanceof Tokenbox)
			((Tokenbox)cur).relabel();
		}
	    };
	    hidebtn.tooltip = Text.render("Hide your account names, for example while streaming.");
	}

        authbtn = new Button(new Coord(this.sz.x-210, 20),FayteSkin.on() ? 200 : 190,this, "Connecting to New Haven"){
            @Override
            public void click()
            {
		// TODO: Allow alternative auth server usage
		setAuthServer("Providence");
            }
            
            public void setAuthServer(String name)
            {
                Config.authserver_name = name;
                Utils.setpref("authserver_name",name);
                Config.defserv = "game.salemthegame.com";
                if(name.equals("Providence"))
                {
                    Config.mainport = 1870;
                }
                else
                {
                    Config.mainport = 1606;
                }
                this.change("Connecting to "+name);
            }
        };

        providencestate = new Button(new Coord(this.sz.x-210, 45),FayteSkin.on() ? 200 : 190,this,"Providence: unknown"){
            @Override
            public void click()
            {
                update_server_statuses();                    
            }
        };
        update_server_statuses();
        flayout();
        
	if(Config.isUpdate && !FayteSkin.on()){
	    showChangelog();
	}
    }
    
    private void update_server_statuses()
    {
        providencestate.change("New Haven: checking ...");
        new Thread(this::fetchstatus, "Server status").start();
    }

    public void tick(double dt) {
        super.tick(dt);
        String st = statustext;
        if(st != null) {
            statustext = null;
            providencestate.change(st);
        }
    }

    private void fetchstatus() {
        try{
            URL statepage = new URL("http://login.salemthegame.com/portal/state");
            InputStream is = statepage.openStream();
            int ptr = 0;
            StringBuilder buffer = new StringBuilder();
            while ((ptr = is.read()) != -1) {
                buffer.append((char)ptr);
            }
            is.close();
            String html = buffer.toString();
            String[] lines = html.split("\n");
            String prov = lines[48];
            statustext = "New Haven: "+prov.substring(prov.indexOf('>')+1,prov.lastIndexOf('<'));
        }
        catch(IOException ex)
        {
            statustext = "Server status unavailable";
        }
    }

    private void showChangelog() {
	log = new Window(new Coord(100,50), new Coord(50,50), ui.root, "Changelog");
	log.justclose = true;
	Textlog txt= new Textlog(Coord.z, new Coord(450,500), log);
	txt.quote = false;
	int maxlines = txt.maxLines = 200;
	log.pack();
	try {
	    InputStream in = LoginScreen.class.getResourceAsStream("/changelog.txt");
	    BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
	    File f = Config.getFile("changelog.txt");
	    FileOutputStream out = new FileOutputStream(f);
	    String strLine;
	    int count = 0;
	    while ((count<maxlines)&&(strLine = br.readLine()) != null)   {
		txt.append(strLine);
		out.write((strLine+"\n").getBytes());
		count++;
	    }
	    br.close();
	    out.close();
	    in.close();
	} catch (FileNotFoundException ignored) {
	} catch (IOException ignored) {
	}
	txt.setprog(0);
	
	//WikiBrowser.toggle();
    }

    private static abstract class Login extends Widget {
	private Login(Coord c, Coord sz, Widget parent) {
	    super(c, sz, parent);
	}
	
	abstract Object[] data();
	abstract boolean enter();
    }

    private abstract class PwCommon extends Login {
	TextEntry user, pass;
	CheckBox savepass;

	private PwCommon(String username, boolean save) {
	    super(LoginScreen.this.cb(), cbox.sz(), LoginScreen.this);
	    setfocustab(true);
	    new Img(new Coord(35, 30), lbl("User name"), this);
	    user = new TextEntry(new Coord(150, 30), new Coord(150, 20), this, username);
	    new Img(new Coord(35, 60), lbl("Password"), this);
	    pass = new TextEntry(new Coord(150, 60), new Coord(150, 20), this, "");
	    pass.pw = true;
	    savepass = new CheckBox(new Coord(150, 90), this, "Remember me");
	    savepass.a = save;
	    if(user.text.equals(""))
		setfocus(user);
	    else
		setfocus(pass);
	}
	
	public void wdgmsg(Widget sender, String name, Object... args) {
	}
		
	boolean enter() {
	    if(user.text.equals("")) {
		setfocus(user);
		return(false);
	    } else if(pass.text.equals("")) {
		setfocus(pass);
		return(false);
	    } else {
		return(true);
	    }
	}

	public boolean globtype(char k, KeyEvent ev) {
	    if((k == 'r') && ((ev.getModifiersEx() & (KeyEvent.META_DOWN_MASK | KeyEvent.ALT_DOWN_MASK)) != 0)) {
		savepass.set(!savepass.a);
		return(true);
	    }
	    return(false);
	}
    }

    private class Pwbox extends PwCommon {
	private Pwbox(String username, boolean save) {
	    super(username, save);
	    if(Config.regurl != null) {
		final RichText text = RichText.render("If you don't have an account, $col[64,64,255]{$u{register one here}}.", 0, java.awt.font.TextAttribute.FOREGROUND, Color.BLACK);
		new Widget(new Coord(35, 115), text.sz(), this) {
		    public void draw(GOut g) {
			g.image(text.tex(), Coord.z);
		    }

		    public boolean mousedown(Coord c, int btn) {
			if(btn == 1) {
			    Number ul = (Number)text.attrat(c, java.awt.font.TextAttribute.UNDERLINE);
			    if((ul != null) && (ul.intValue() == java.awt.font.TextAttribute.UNDERLINE_ON)) {
				try {
				    WebBrowser.sshow(Config.regurl);
				} catch(WebBrowser.BrowserException e) {
				    error("Could not launch browser");
				}
			    }
			}
			return(true);
		    }
		};
	    }
	}
		
	Object[] data() {
	    return(new Object[] {new AuthClient.NativeCred(user.text, pass.text), savepass.a});
	}
    }
		
    private class Pdxbox extends PwCommon {
	private Pdxbox(String username, boolean save) {
	    super(username, save);
	    }

	Object[] data() {
	    return(new Object[] {new ParadoxCreds(user.text, pass.text), savepass.a});
	    }
	}

    private abstract class WebCommon extends Login {
	private WebCommon() {
	    super(LoginScreen.this.cb(), cbox.sz(), LoginScreen.this);
	}

	boolean enter() {
	    return(true);
	}
    }

    private class Amazonbox extends WebCommon {
	Object[] data() {
	    return(new Object[] {new BrowserAuth() {
		    public String method() {return("amz");}
		    public String name() {return("Amazon user");}
		}, false});
	}
    }
	
    private class Tokenbox extends Login {
	private final String name;
	private final String token;
	Text label;
	Button btn;
		
	private Tokenbox(String username, String token) {
	    super(LoginScreen.this.cb(), cbox.sz(), LoginScreen.this);
	    this.name = username;
	    this.token = token;
	    relabel();
	    btn = new Button(new Coord((sz.x - 100) / 2, 55), 100, this, "Forget me");
	}

	void relabel() {
	    String who = AccountList.hidden() ? "this account" : name;
	    label = FayteSkin.on() ? Window.bigtf.render("Identity is saved for " + who, FayteSkin.TEXT) : textfs.render("Identity is saved for " + who);
	}
		
	Object[] data() {
	    return(new Object[]{name, token});
	}
		
	boolean enter() {
	    return(true);
	}
		
	public void wdgmsg(Widget sender, String name, Object... args) {
	    if(sender == btn) {
		LoginScreen.this.wdgmsg("forget");
		return;
	    }
	    super.wdgmsg(sender, name, args);
	}
		
	public void draw(GOut g) {
	    g.image(label.tex(), new Coord((sz.x - label.sz().x) / 2, 30));
	    super.draw(g);
	}
	
	public boolean globtype(char k, KeyEvent ev) {
	    if((k == 'f') && ((ev.getModifiersEx() & (KeyEvent.META_DOWN_MASK | KeyEvent.ALT_DOWN_MASK)) != 0)) {
		LoginScreen.this.wdgmsg("forget");
		return(true);
	    }
	    return(false);
	}
    }

    static final BufferedImage[] loginb = {
	Resource.loadimg("gfx/hud/buttons/loginu"),
	Resource.loadimg("gfx/hud/buttons/logind"),
	Resource.loadimg("gfx/hud/buttons/loginh"),
    };
    private void mklogin() {
	synchronized(ui) {
	    if(FayteSkin.on())
		btn = new Button(cb().add((cbox.sz().x - 146) / 2, 140), 146, this, "Log in");
	    else
		btn = new IButton(cboxc.add((cbox.sz().x - loginb[0].getWidth()) / 2, 140), this, loginb[0], loginb[1], loginb[2]);
	    progress(null);
	}
    }
	
    private void error(String error) {
	synchronized(ui) {
	    if(this.error != null)
		this.error = null;
	    if(error != null)
		this.error = texte.render(error);
	}
    }
    
    private void progress(String p) {
	synchronized(ui) {
	    if(progress != null)
		progress = null;
	    if(p != null)
		progress = FayteSkin.on() ? Window.bigtf.render(p, FayteSkin.TEXT) : textf.render(p);
	}
    }
    
    private void clear() {
	if(cur != null) {
	    ui.destroy(cur);
	    cur = null;
	    ui.destroy(btn);
	    btn = null;
	}
	progress(null);
    }
    
    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(sender == btn) {
	    if(cur.enter())
		super.wdgmsg("login", cur.data());
	    return;
	} else if(msg.equals("account")){
	    //repeat if was not token box to do actual login
	    boolean repeat = !(cur instanceof Tokenbox);
	    if(repeat){
		super.wdgmsg("login", args[0], args[1]);
	    }
	    super.wdgmsg("login", args[0], args[1]);
	    return;
	}
	super.wdgmsg(sender, msg, args);
    }
	
    public void uimsg(String msg, Object... args) {
	synchronized(ui) {
	    if(msg == "passwd") {
		clear();
		if(Config.authmech.equals("native")) {
		    cur = new Pwbox((String)args[0], (Boolean)args[1]);
		} else if(Config.authmech.equals("paradox")) {
		    cur = new Pdxbox((String)args[0], (Boolean)args[1]);
		} else if(Config.authmech.equals("amz")) {
		    cur = new Amazonbox();
		} else {
		    throw(new RuntimeException("Unknown authmech `" + Config.authmech + "' specified"));
		}
		mklogin();
	    } else if(msg == "token") {
		clear();
		cur = new Tokenbox((String)args[0], (String)args[1]);
		mklogin();
	    } else if(msg == "error") {
		error((String)args[0]);
	    } else if(msg == "prg") {
		error(null);
		clear();
		progress((String)args[0]);
	    }
	}
    }
    
    public void presize() {
	if(FayteSkin.on()) {
	    c = Coord.z;
	    sz = parent.sz;
	    flayout();
	    return;
	}
	c = parent.sz.div(2).sub(sz.div(2));
    }
	
    private static Tex lbl(String s) {
	if(FayteSkin.on())
	    return(Window.bigtf.render(s, FayteSkin.TEXT).tex());
	return(textf.render(s).tex());
    }

    private Text ftitle, fsub;

    public void draw(GOut g) {
	if(FayteSkin.on()) {
	    g.image(fbg, bgoff(), bgsz());
	    Coord cc = cb().sub(0, 44);
	    Coord cs = cbox.sz().add(0, 44);
	    FayteSkin.box(g, cc, cs, new Color(0x15, 0x18, 0x1D, 140), new Color(0x3A, 0x41, 0x4C, 200));
	    if(ftitle == null) {
		ftitle = new Text.Foundry(new Font("Serif", Font.BOLD, 26), FayteSkin.TEXT).aa(true).render("Arcana");
		fsub = FayteSkin.labelf.render("A Salem client  \u00b7  " + Config.authserver_name, FayteSkin.mix(FayteSkin.BORDER, FayteSkin.TEXT, 0.7));
	    }
	    g.image(ftitle.tex(), new Coord(cc.x + 16, cc.y + 8));
	}
	super.draw(g);
	if(error != null) {
	    Coord c = FayteSkin.on() ? new Coord((sz.x - error.sz().x) / 2, cb().y - 44 - error.sz().y - 10) : new Coord((sz.x - error.sz().x) / 2, 290);
	    g.chcolor(0, 0, 0, 224);
	    g.frect(c.sub(4, 2), error.sz().add(8, 4));
	    g.chcolor();
	    g.image(error.tex(), c);
	}
	if(progress != null)
	    g.image(progress.tex(), new Coord((sz.x - progress.sz().x) / 2, cb().y + ((cbox.sz().y - progress.sz().y) / 2)));
    }
	
    public boolean type(char k, KeyEvent ev) {
	if(k == 10) {
	    if((cur != null) && cur.enter())
		wdgmsg("login", cur.data());
	    return(true);
	}
	return(super.type(k, ev));
    }

    @Override
    public void destroy() {
	if(log != null){
	    ui.destroy(log);
	    log = null;
	}
	super.destroy();
    }
}
