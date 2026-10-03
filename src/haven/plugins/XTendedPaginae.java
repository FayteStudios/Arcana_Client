package haven.plugins;

import haven.Config;
import haven.FayteMsg;
import haven.GameUI;
import haven.Glob;
import haven.HotkeyListWindow;
import haven.FayteModules;
import haven.Resource;
import haven.Resource.JarSource;
import haven.UI;
import haven.WikiBrowser;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

public class XTendedPaginae {

    static Map<String, Plugin> dictionary = new HashMap<>();

    public static void loadXTendedPaginae(UI ui) {
        loadBaseXTendedPaginae(ui);
        loadPluginXTendedPaginae(ui);
    }

    public static void registerPlugin(String name, Plugin plugin) {
        dictionary.put(name, plugin);
    }

    static void loadPluginXTendedPaginae(UI ui) {
        File plugin_folder = new File(Config.pluginfolder);
        File[] plugin_jars = plugin_folder.listFiles();
        if(plugin_jars==null)
            return;
        URL[] plugin_urls = new URL[plugin_jars.length];
        for (int i = 0; i < plugin_urls.length; i++) {
            try {
                plugin_urls[i] = plugin_jars[i].toURI().toURL();
            } catch (MalformedURLException ex) {
                //ignore this, shouldn't even happen
            }
        }
        URLClassLoader ucl = new URLClassLoader(plugin_urls);
        ServiceLoader<Plugin> sl = ServiceLoader.load(Plugin.class, ucl);
        Iterator<Plugin> plugins = sl.iterator();
        while (plugins.hasNext()) {
            Plugin plugin = plugins.next();
            Resource.addplugin(plugin);
            plugin.load(ui);
        }
    }

    static void loadBaseXTendedPaginae(UI ui) {
        Glob glob = ui.sess.glob;
        Collection<Glob.Pagina> p = FayteModules.STYLE.on() ? new java.util.ArrayList<Glob.Pagina>() : glob.paginae;
        p.add(glob.paginafor(Resource.load("paginae/act/add")));
        p.add(glob.paginafor(Resource.load("paginae/add/wiki")));
        dictionary.put("wiki", new Plugin() {
            public void execute(UI ui) {
                WikiBrowser.toggle();
            }
        });
        p.add(glob.paginafor(Resource.load("paginae/add/craft")));
        dictionary.put("craft", new Plugin() {
            public void execute(UI ui) {
                ui.gui.toggleCraftWnd();
            }
        });
        p.add(glob.paginafor(Resource.load("paginae/add/radius")));
        dictionary.put("radius", new Plugin() {
            public void execute(UI ui) {
                Config.toggleRadius();
            }
        });
        p.add(glob.paginafor(Resource.load("paginae/add/hotkey")));
        dictionary.put("hotkey", new Plugin() {
            public void execute(UI ui) {
                HotkeyListWindow.instance(ui).toggle();
            }
        });
        addcmd(p, glob, "fayte");
        addcmd(p, glob, "gatherwindows");
        addcmd(p, glob, "modules");
        for(String cmd : FayteModules.xtended())
            addcmd(p, glob, cmd);
    }

    static void addcmd(Collection<Glob.Pagina> p, Glob glob, final String cmd) {
        p.add(glob.paginafor(Resource.load("paginae/add/" + cmd)));
        dictionary.put(cmd, new Plugin() {
            public void execute(UI ui) {
                try {
                    ui.cons.run(new String[]{cmd});
                } catch (Exception e) {
                    FayteMsg.say(e.getMessage(), GameUI.MsgType.BAD);
                }
            }
        });
    }

    public static boolean useXTended(UI ui, String[] ad) {
        boolean handled = true;
        if (ad[1].equals("act")) {
            String[] args = new String[ad.length - 2];
            System.arraycopy(ad, 2, args, 0, args.length);
            ui.gui.wdgmsg("act", (Object[]) args);
        } else if (dictionary.containsKey(ad[1])) {
            dictionary.get(ad[1]).execute(ui);
        } else {
            handled = false;
        }

        return handled;
    }
}
