package haven;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FayteTextArea extends Widget {
    private static final int PAD = 5;
    private FontMetrics fm;
    private int lh;
    private Text.Foundry fnd = FayteSkin.labelf;
    private String text = "";
    private int caret = 0;
    private int scroll = 0;
    private List<int[]> lines = new ArrayList<>();
    private int laidw = -1;
    private String laidtext = null;
    private final Map<String, Text> cache = new HashMap<>();
    public Runnable changed = null;

    public FayteTextArea(Coord c, Coord sz, Widget parent) {
        super(c, sz, parent);
        setfont(FayteSkin.labelf);
        setcanfocus(true);
    }

    public void setsize(int pt) {
        setfont(new Text.Foundry(new Font("SansSerif", Font.PLAIN, pt), FayteSkin.TEXT).aa(true));
    }

    private void setfont(Text.Foundry f) {
        fnd = f;
        Graphics g = TexI.mkbuf(new Coord(4, 4)).getGraphics();
        g.setFont(f.font);
        fm = g.getFontMetrics();
        lh = fm.getAscent() + fm.getDescent() + 2;
        cache.clear();
        laidtext = null;
    }

    public String text() {
        return text;
    }

    public void settext(String t) {
        text = t == null ? "" : t;
        caret = Math.min(caret, text.length());
        scroll = 0;
        laidtext = null;
    }

    private void layout() {
        int w = sz.x - PAD * 2;
        if (w == laidw && text.equals(laidtext)) {
            return;
        }
        laidw = w;
        laidtext = text;
        List<int[]> l = new ArrayList<>();
        int start = 0;
        int n = text.length();
        while (start <= n) {
            int nl = text.indexOf('\n', start);
            int end = nl < 0 ? n : nl;
            int ls = start;
            while (true) {
                int fit = ls;
                int lastsp = -1;
                while (fit < end && fm.stringWidth(text.substring(ls, fit + 1)) <= w) {
                    if (text.charAt(fit) == ' ') {
                        lastsp = fit;
                    }
                    fit++;
                }
                if (fit >= end) {
                    l.add(new int[] {ls, end});
                    break;
                }
                int brk = lastsp >= ls ? lastsp + 1 : Math.max(fit, ls + 1);
                l.add(new int[] {ls, brk});
                ls = brk;
            }
            if (nl < 0) {
                break;
            }
            start = nl + 1;
        }
        lines = l;
    }

    private int lineof(int pos) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (pos >= lines.get(i)[0]) {
                return i;
            }
        }
        return 0;
    }

    private int posat(int line, int x) {
        int[] ln = lines.get(Math.max(0, Math.min(lines.size() - 1, line)));
        int best = ln[0];
        for (int p = ln[0]; p <= ln[1]; p++) {
            if (fm.stringWidth(text.substring(ln[0], p)) <= x) {
                best = p;
            }
        }
        return best;
    }

    private Text line(String s) {
        Text t = cache.get(s);
        if (t == null) {
            if (cache.size() > 300) {
                cache.clear();
            }
            t = fnd.render(s, FayteSkin.TEXT);
            cache.put(s, t);
        }
        return t;
    }

    private int rows() {
        return Math.max(1, (sz.y - PAD * 2) / lh);
    }

    private void showcaret() {
        int cl = lineof(caret);
        if (cl < scroll) {
            scroll = cl;
        } else if (cl >= scroll + rows()) {
            scroll = cl - rows() + 1;
        }
    }

    @Override
    public void draw(GOut g) {
        layout();
        FayteSkin.box(
                g,
                Coord.z,
                sz,
                FayteSkin.mix(FayteSkin.PANEL, Color.BLACK, 0.2),
                hasfocus ? FayteSkin.TEXT : FayteSkin.BORDER);
        int rows = this.rows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - rows)));
        for (int i = 0; i < rows && scroll + i < lines.size(); i++) {
            int[] ln = lines.get(scroll + i);
            String s = text.substring(ln[0], ln[1]);
            if (!s.isEmpty()) {
                g.image(line(s).tex(), new Coord(PAD, PAD + i * lh));
            }
        }
        if (hasfocus && (System.currentTimeMillis() % 1000) < 600) {
            int cl = lineof(caret);
            if (cl >= scroll && cl < scroll + rows) {
                int[] ln = lines.get(cl);
                int x = PAD + fm.stringWidth(text.substring(ln[0], Math.min(caret, ln[1])));
                g.chcolor(FayteSkin.TEXT);
                g.frect(new Coord(x, PAD + (cl - scroll) * lh), new Coord(1, lh - 2));
                g.chcolor();
            }
        }
    }

    private void insert(String s) {
        text = text.substring(0, caret) + s + text.substring(caret);
        caret += s.length();
        edited();
    }

    private void edited() {
        layout();
        showcaret();
        if (changed != null) {
            changed.run();
        }
    }

    @Override
    public boolean type(char c, KeyEvent ev) {
        if (c == '\n' || c == '\r') {
            insert("\n");
            return true;
        } else if (c >= 32 && c != 127 && !ev.isControlDown()) {
            insert(String.valueOf(c));
            return true;
        }
        return true;
    }

    @Override
    public boolean keydown(KeyEvent ev) {
        layout();
        int k = ev.getKeyCode();
        int cl = lineof(caret);
        switch (k) {
            case KeyEvent.VK_BACK_SPACE:
                if (caret > 0) {
                    text = text.substring(0, caret - 1) + text.substring(caret);
                    caret--;
                    edited();
                }
                return true;
            case KeyEvent.VK_DELETE:
                if (caret < text.length()) {
                    text = text.substring(0, caret) + text.substring(caret + 1);
                    edited();
                }
                return true;
            case KeyEvent.VK_LEFT:
                caret = Math.max(0, caret - 1);
                showcaret();
                return true;
            case KeyEvent.VK_RIGHT:
                caret = Math.min(text.length(), caret + 1);
                showcaret();
                return true;
            case KeyEvent.VK_UP:
            case KeyEvent.VK_DOWN: {
                int x = fm.stringWidth(text.substring(lines.get(cl)[0], caret));
                int nl = cl + (k == KeyEvent.VK_UP ? -1 : 1);
                if (nl >= 0 && nl < lines.size()) {
                    caret = posat(nl, x);
                }
                showcaret();
                return true;
            }
            case KeyEvent.VK_HOME:
                caret = lines.get(cl)[0];
                return true;
            case KeyEvent.VK_END:
                caret = lines.get(cl)[1];
                return true;
            case KeyEvent.VK_V:
                if (ev.isControlDown()) {
                    try {
                        Object d =
                                Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                        if (d instanceof String) {
                            insert(((String) d).replace("\r", ""));
                        }
                    } catch (Exception e) {
                        FayteLog.once("FayteTextArea.keydown", e);
                    }
                }
                return true;
            case KeyEvent.VK_C:
                if (ev.isControlDown()) {
                    try {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                    } catch (Exception e) {
                        FayteLog.once("FayteTextArea.keydown", e);
                    }
                }
                return true;
            case KeyEvent.VK_ESCAPE:
                return false;
            default:
                return true;
        }
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        parent.setfocus(this);
        layout();
        int l = scroll + Math.max(0, (c.y - PAD) / lh);
        if (l >= lines.size()) {
            caret = text.length();
        } else {
            caret = posat(l, c.x - PAD);
        }
        return true;
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        scroll += amount * 2;
        return true;
    }
}
