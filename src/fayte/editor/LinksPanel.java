package fayte.editor;

import haven.FayteEntries;
import haven.FayteIconLibrary;
import haven.FayteLog;
import haven.FaytePaths;
import haven.FayteSkin;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class LinksPanel extends JPanel {
    private static final Map<String, Image> icons = new ConcurrentHashMap<>();
    private final EntryEditor ed;
    private volatile GameCatalog cat;
    private final DefaultListModel<GameCatalog.Thing> model = new DefaultListModel<>();
    private final JList<GameCatalog.Thing> list = new JList<>(model);
    private final JTextArea info = new JTextArea();
    private final JButton checkbtn = new JButton("Mark as checked");
    private String title;

    public LinksPanel(EntryEditor ed) {
        super(new BorderLayout(0, ed.px(6)));
        this.ed = ed;
        info.setEditable(false);
        info.setLineWrap(true);
        info.setWrapStyleWord(true);
        info.setOpaque(false);
        info.setFocusable(false);
        info.setText("Loading the list of things in the game\u2026");
        add(info, BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(ed.px(40));
        list.setCellRenderer(new LinksPanel.Row(ed, false));
        add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel b = new JPanel(new FlowLayout(FlowLayout.LEFT, ed.px(6), 0));
        JButton link = new JButton("Link something\u2026");
        link.setToolTipText("Choose a thing in the game that should open this entry");
        link.addActionListener(ev -> this.link());
        JButton unlink = new JButton("Unlink");
        unlink.setToolTipText("Remove a link you added (automatic matches can't be removed, only redirected)");
        unlink.addActionListener(ev -> this.unlink());
        checkbtn.setToolTipText("Record that you've checked this entry opens from the right things in the game");
        checkbtn.addActionListener(ev -> togglecheck());
        b.add(link);
        b.add(unlink);
        b.add(checkbtn);
        add(b, BorderLayout.SOUTH);
        new Thread(
                        () -> {
                            GameCatalog c = GameCatalog.build();
                            SwingUtilities.invokeLater(() -> {
                                cat = c;
                                show(title);
                            });
                        },
                        "Game catalog")
                .start();
    }

    static Image icon(GameCatalog.Thing t, int size) {
        String k = t.key + "@" + size;
        Image img = icons.get(k);
        if (img == null) {
            BufferedImage b = null;

            try {
                File f = t.iconfile() != null
                        ? new File(FayteIconLibrary.resdir(), t.iconfile() + ".png")
                        : new File(FaytePaths.icons(), FaytePaths.safename(t.label) + ".png");
                if (f.exists()) {
                    b = ImageIO.read(f);
                }
            } catch (Exception e) {
                FayteLog.once("LinksPanel.icon", e);
            }
            if (b == null) {
                img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            } else {
                double s = (double) size / Math.max(b.getWidth(), b.getHeight());
                BufferedImage o = new BufferedImage(
                        Math.max(1, (int) (b.getWidth() * s)),
                        Math.max(1, (int) (b.getHeight() * s)),
                        BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = o.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(b, 0, 0, o.getWidth(), o.getHeight(), null);
                g.dispose();
                img = o;
            }
            icons.put(k, img);
        }
        return img;
    }

    static class Row extends JComponent implements ListCellRenderer<GameCatalog.Thing> {
        private final EntryEditor ed;
        private final boolean showtarget;
        private GameCatalog.Thing t;
        private boolean sel;

        Row(EntryEditor ed, boolean showtarget) {
            this.ed = ed;
            this.showtarget = showtarget;
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends GameCatalog.Thing> l, GameCatalog.Thing v, int i, boolean sel, boolean foc) {
            t = v;
            this.sel = sel;
            setFont(l.getFont());
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(sel ? new Color(0x35, 0x55, 0x75) : FayteSkin.PANEL);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (t == null) {
                return;
            }
            int is = ed.px(32);
            Image img = icon(t, is);
            int pad = ed.px(4);
            g.drawImage(img, pad + (is - img.getWidth(null)) / 2, (getHeight() - img.getHeight(null)) / 2, null);
            int x = pad * 2 + is;
            FontMetrics fm = g.getFontMetrics(getFont());
            g.setFont(getFont());
            g.setColor(FayteSkin.TEXT);
            int y1 = getHeight() / 2 - ed.px(2);
            g.drawString(t.label, x, y1);
            g.setColor(new Color(0x8A, 0x8A, 0x8A));
            String how = showtarget
                    ? (t.target == null ? "opens nothing yet" : "opens " + t.target)
                    : (t.alias ? "your link" : "automatic");
            g.drawString(t.kind + " \u00b7 " + t.key + " \u00b7 " + how, x, y1 + fm.getHeight());
        }
    }

    public void show(String title) {
        this.title = title;
        model.clear();
        boolean ck = title != null && ed.checked.contains(title.toLowerCase());
        checkbtn.setText(ck ? "Checked \u2713 (click to undo)" : "Mark as checked");
        if (cat == null) {
            return;
        }
        List<GameCatalog.Thing> ts = cat.linkedto(title);

        for (GameCatalog.Thing t : ts) {
            model.addElement(t);
        }
        info.setText(
                title == null || title.isEmpty()
                        ? "Open an entry to see which things in the game lead to it."
                        : (ts.isEmpty()
                                ? "Nothing in the game opens \"" + title
                                        + "\" yet. Use \"Link something\u2026\" to connect the right things."
                                : ts.size() + " thing" + (ts.size() == 1 ? "" : "s") + " in the game open \"" + title
                                        + "\" (middle-click Inspect and the Almanac). Check they're the right ones;"
                                        + " unlink or relink any that aren't."));
    }

    public void refresh() {
        if (cat != null) {
            FayteEntries.load();
            cat.resolve();
        }
        show(title);
    }

    private void link() {
        if (title == null || title.trim().isEmpty()) {
            ed.status("Open or name an entry first.");
            return;
        } else if (cat == null) {
            ed.status("Still loading the list of things in the game\u2026");
            return;
        }
        JDialog d = new JDialog(ed, "Link something to \"" + title + "\"", true);
        JTextField q = new JTextField();
        DefaultListModel<GameCatalog.Thing> m = new DefaultListModel<>();
        JList<GameCatalog.Thing> l = new JList<>(m);
        l.setFixedCellHeight(ed.px(40));
        l.setCellRenderer(new LinksPanel.Row(ed, true));
        JLabel count = new JLabel(" ");
        Runnable fill = () -> {
            DefaultListModel<GameCatalog.Thing> nm = new DefaultListModel<>();
            String s = q.getText().trim().toLowerCase();
            int n = 0;
            int total = 0;

            for (GameCatalog.Thing t : cat.things) {
                if (s.isEmpty()
                        || t.label.toLowerCase().contains(s)
                        || t.key.toLowerCase().contains(s)) {
                    total++;
                    if (n < 400) {
                        nm.addElement(t);
                        n++;
                    }
                }
            }
            l.setModel(nm);
            count.setText(
                    total > n
                            ? "Showing " + n + " of " + total + ". Type to narrow it down."
                            : total + " things. Double-click to link.");
        };
        q.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                fill.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                fill.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                fill.run();
            }
        });
        q.setText(title.split(" ")[0]);
        fill.run();
        GameCatalog.Thing[] picked = {null};
        l.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && l.getSelectedValue() != null) {
                    picked[0] = l.getSelectedValue();
                    d.dispose();
                }
            }
        });
        JButton ok = new JButton("Link");
        ok.addActionListener(ev -> {
            picked[0] = l.getSelectedValue();
            d.dispose();
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(ev -> d.dispose());
        JPanel top = new JPanel(new BorderLayout(ed.px(6), 0));
        top.add(new JLabel("Search the game:"), BorderLayout.WEST);
        top.add(q, BorderLayout.CENTER);
        JPanel bot = new JPanel(new BorderLayout());
        bot.add(count, BorderLayout.WEST);
        JPanel bb = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bb.add(ok);
        bb.add(cancel);
        bot.add(bb, BorderLayout.EAST);
        JPanel root = new JPanel(new BorderLayout(0, ed.px(6)));
        root.setBorder(BorderFactory.createEmptyBorder(ed.px(8), ed.px(8), ed.px(8), ed.px(8)));
        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(l), BorderLayout.CENTER);
        root.add(bot, BorderLayout.SOUTH);
        d.setContentPane(root);
        d.setSize(ed.px(760), ed.px(560));
        d.setLocationRelativeTo(ed);
        d.setVisible(true);
        GameCatalog.Thing t = picked[0];
        if (t != null) {
            if (t.target != null && !t.target.equalsIgnoreCase(title)) {
                int r = JOptionPane.showConfirmDialog(
                        ed,
                        "\"" + t.label + "\" currently opens \"" + t.target + "\". Make it open \"" + title
                                + "\" instead?",
                        EntryEditor.TITLE,
                        JOptionPane.YES_NO_OPTION);
                if (r != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            ed.addalias(t.key, title);
            refresh();
            ed.status("Linked " + t.label + " (" + t.key + ") to " + title + ".");
        }
    }

    private void unlink() {
        GameCatalog.Thing t = list.getSelectedValue();
        if (t == null) {
            ed.status("Pick one of the linked things first.");
        } else if (!t.alias) {
            ed.status(t.label
                    + " opens this entry automatically, by its name. To send it elsewhere, open the right"
                    + " entry and use \"Link something\u2026\" there.");
        } else {
            ed.removealias(t.key);
            refresh();
            ed.status("Removed your link for " + t.label + ".");
        }
    }

    private void togglecheck() {
        if (title == null || title.trim().isEmpty()) {
            return;
        }
        String k = title.toLowerCase();
        if (!ed.checked.remove(k)) {
            ed.checked.add(k);
        }
        ed.savechecked();
        show(title);
    }

    static List<String> lines(File f) {
        List<String> ret = new ArrayList<>();
        if (f.exists()) {
            try {
                for (String l : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    ret.add(l.replace("\r", ""));
                }
            } catch (Exception e) {
                FayteLog.once("LinksPanel.lines", e);
            }
        }
        return ret;
    }
}
