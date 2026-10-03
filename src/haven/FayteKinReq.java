package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

public class FayteKinReq {
    private static final Map<Window, Boolean> seen = new WeakHashMap<>();
    private static final List<Req> pending = new ArrayList<>();

    static void reset() {
        pending.clear();
        seen.clear();
    }

    private static long last = 0L;
    private static final Pattern KIN = Pattern.compile("(?s).*\\b(kin|kinship|kinsman|kinswoman)\\b.*");

    public static class Req {
        public final Window w;
        public final String text;
        public final List<Button> btns = new ArrayList<>();

        Req(Window w, String text) {
            this.w = w;
            this.text = text;
        }
    }

    public static boolean on() {
        return FayteSkin.on() && FayteModules.ALMANAC.on();
    }

    private static void collect(Widget w, StringBuilder txt, List<Button> btns) {
        for (Widget c = w.child; c != null; c = c.next) {
            if (c instanceof Label) {
                String t = String.valueOf(((Label) c).texts).trim();
                if (!t.isEmpty() && !t.equals("null")) {
                    txt.append(txt.length() > 0 ? " " : "").append(t);
                }
            } else if (c instanceof Button && ((Button) c).text != null) {
                btns.add((Button) c);
            }
            collect(c, txt, btns);
        }
    }

    public static synchronized int count() {
        pending.removeIf(r -> !r.w.attached());
        return pending.size();
    }

    public static synchronized Req first() {
        count();
        return pending.isEmpty() ? null : pending.get(0);
    }

    public static synchronized void answer(Req r, Button b) {
        pending.remove(r);
        if (b != null) {
            b.click();
        }
    }

    public static synchronized void original(Req r) {
        pending.remove(r);
        r.w.show();
        r.w.raise();
    }

    public static void tick(GameUI gui) {
        long now = System.currentTimeMillis();
        if (!on() || now - last < 250L) {
            return;
        }
        last = now;
        for (Widget w = gui.child; w != null; w = w.next) {
            if (w.getClass() != Window.class || seen.containsKey(w)) {
                continue;
            }
            Window win = (Window) w;
            String cap = win.cap == null ? "" : win.cap.text;
            StringBuilder txt = new StringBuilder();
            List<Button> btns = new ArrayList<>();
            collect(win, txt, btns);
            String all = (cap + " " + txt).toLowerCase();
            if (txt.length() == 0 && btns.isEmpty()) {
                continue;
            }
            seen.put(win, true);
            if (!KIN.matcher(all).matches() || btns.isEmpty()) {
                continue;
            }
            StringBuilder bl = new StringBuilder();
            for (Button b : btns) {
                bl.append(bl.length() > 0 ? ", " : "").append(b.text.text);
            }
            FayteLog.log("Kin request: window \"" + cap + "\" text \"" + txt + "\" buttons [" + bl + "]");
            Req r = new Req(win, txt.length() > 0 ? txt.toString() : cap);
            r.btns.addAll(btns);
            synchronized (FayteKinReq.class) {
                pending.add(r);
            }
            win.hide();
            FayteMsg.say("Kin request: answer it in Almanac \u2192 Pilgrims.");
        }
    }

    public static class Notice extends Widget {
        private static final Color GOLD = new Color(0xC8, 0xAA, 0x62);
        public static final int H = FayteSkin.s(58);
        private final List<Object[]> hits = new ArrayList<>();

        public Notice(Widget parent) {
            super(Coord.z, new Coord(10, H), parent);
        }

        @Override
        public void draw(GOut g) {
            Req r = first();
            hits.clear();
            if (r == null) {
                return;
            }
            FayteSkin.box(g, Coord.z, sz, FayteSkin.mix(FayteSkin.PANEL, GOLD, 0.12), GOLD);
            int n = count();
            g.image(
                    FayteSkin.titlef
                            .render("Kin request" + (n > 1 ? " (1 of " + n + ")" : ""), GOLD)
                            .tex(),
                    new Coord(8, 4));
            Text t = FayteSkin.labelf.renderwrap(r.text, FayteSkin.TEXT, sz.x - FayteSkin.s(300));
            g.image(t.tex(), new Coord(8, FayteSkin.s(24)));
            int x = sz.x - 8;
            List<Object[]> bs = new ArrayList<>();
            bs.add(new Object[] {"Open original", null});
            for (int i = r.btns.size() - 1; i >= 0; i--) {
                bs.add(new Object[] {r.btns.get(i).text.text, r.btns.get(i)});
            }
            for (Object[] b : bs) {
                Text bt = FayteSkin.labelf.render((String) b[0], FayteSkin.TEXT);
                int bw = bt.sz().x + FayteSkin.s(20);
                x -= bw;
                Coord bc = new Coord(x, (sz.y - FayteSkin.s(26)) / 2);
                Coord bsz = new Coord(bw, FayteSkin.s(26));
                FayteSkin.box(g, bc, bsz, FayteSkin.PANEL, b[1] == null ? FayteSkin.BORDER : GOLD);
                g.aimage(bt.tex(), bc.add(bsz.div(2)), 0.5, 0.5);
                hits.add(new Object[] {bc, bsz, b[1], r});
                x -= FayteSkin.s(6);
            }
        }

        @Override
        public boolean mousedown(Coord c, int button) {
            if (button != 1) {
                return first() != null;
            }
            for (Object[] h : hits) {
                if (c.isect((Coord) h[0], (Coord) h[1])) {
                    Req r = (Req) h[3];
                    if (h[2] == null) {
                        original(r);
                    } else {
                        answer(r, (Button) h[2]);
                    }
                    return true;
                }
            }
            return first() != null;
        }
    }
}
