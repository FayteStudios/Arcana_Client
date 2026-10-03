package haven;

import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public class FayteMsg {
    public static void say(String text) {
        say(text, null);
    }

    public static void say(String text, GameUI.MsgType type) {
        GameUI gui = UI.instance == null ? null : UI.instance.gui;
        if (gui == null || gui.syslog == null) {
            System.out.println(text);
        } else if (type == GameUI.MsgType.BAD || type == GameUI.MsgType.ERROR) {
            gui.message(text, type);
        } else {
            ChatUI.Channel ch = ownchannel() ? channel(gui, category()) : gui.syslog;
            ch.append(text, type == null ? Color.WHITE : GameUI.getMsgColor(type));
        }
    }

    public static final String CRAFTING = "Arcana: Crafting";
    public static final String ALERTS = "Arcana: Alerts";
    public static final String MAP = "Arcana: Map";
    public static final String[] CHANNELS = {"Arcana", CRAFTING, ALERTS, MAP};
    private static final Map<String, String> CATS = new HashMap<>();

    static {
        for (String c : new String[] {
            "FayteRecipes",
            "FayteRecipesPanel",
            "FayteStationPanel",
            "FayteBenchCard",
            "FayteShopList",
            "FayteBagSel",
            "FayteStash"
        }) {
            CATS.put(c, CRAFTING);
        }
        for (String c : new String[] {
            "FayteHighlights",
            "FayteDeath",
            "FayteTimers",
            "FayteTimersWnd",
            "FayteBuffTimers",
            "Bufflist",
            "FayteKinReq",
            "FayteTrade",
            "FayteWatchdog"
        }) {
            CATS.put(c, ALERTS);
        }
        for (String c : new String[] {"WorldMapMarkers", "WorldMapData", "WorldMapWnd", "MapView", "FayteMapStore"}) {
            CATS.put(c, MAP);
        }
    }

    private static String category() {
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            String cn = e.getClassName();
            if (!cn.startsWith("haven.")) {
                continue;
            }
            cn = cn.substring(6);
            int d = cn.indexOf('$');
            if (d >= 0) {
                cn = cn.substring(0, d);
            }
            if (cn.equals("FayteMsg")) {
                continue;
            }
            String c = CATS.get(cn);
            return c == null ? "Arcana" : c;
        }
        return "Arcana";
    }

    private static final Map<String, WeakReference<ChatUI.Channel>> own = new HashMap<>();

    private static ChatUI.Channel channel(GameUI gui, String name) {
        WeakReference<ChatUI.Channel> r = own.get(name);
        ChatUI.Channel c = r == null ? null : r.get();
        if (c == null || c.parent == null || c.parent != gui.chat) {
            c = new ChatUI.Log(gui.chat, name);
            own.put(name, new WeakReference<>(c));
        }
        return c;
    }

    public static boolean ownchannel() {
        return Utils.getprefb("fayte_own_channel", false);
    }

    public static void setownchannel(boolean v) {
        Utils.setprefb("fayte_own_channel", v);
    }

    public static String resname(Gob gob) {
        if (gob == null) {
            return null;
        }
        try {
            Drawable d = gob.getattr(Drawable.class);
            Indir<Resource> r = null;
            if (d instanceof ResDrawable) {
                r = ((ResDrawable) d).res;
            } else if (d instanceof Composite) {
                r = ((Composite) d).base;
            }
            return r == null ? null : r.get().name;
        } catch (Loading e) {
            return null;
        }
    }
}
