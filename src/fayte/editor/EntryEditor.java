package fayte.editor;

import haven.Coord;
import haven.FayteEntries;
import haven.FayteEntryLayout;
import haven.FayteIconLibrary;
import haven.FayteLog;
import haven.FayteMissing;
import haven.FaytePaths;
import haven.FayteSkin;
import haven.FayteText;
import haven.FayteWikiData;
import haven.FayteWikiEntry;
import haven.FayteWikiGlance;
import haven.FayteWikiText;
import haven.FayteWikiTidy;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Path2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

public class EntryEditor extends JFrame {
    public static final String TITLE = "Entry Editor";
    public static final String YOURS = "yours";
    public static final String SHARED = "shared";
    public static final String WIKI = "wiki";
    public static final String MISSING = "missing";
    public static final String HIDDEN = "hidden";
    public static final String DISMISSED = "dismissed";
    private static final int PREVIEWW = 520;
    private static final Map<String, String> INFOBOXES = new LinkedHashMap<>();
    private final double ui;
    private final File shared;
    private final List<EntryEditor.Item> all = new ArrayList<>();
    private DefaultListModel<EntryEditor.Item> model = new DefaultListModel<>();
    private final JList<EntryEditor.Item> list = new JList<EntryEditor.Item>(model) {
        private static final long serialVersionUID = 1L;

        @Override
        protected void processMouseEvent(MouseEvent ev) {
            int i = locationToIndex(ev.getPoint());
            Rectangle r = i < 0 ? null : getCellBounds(i, i);
            if (r != null
                    && r.contains(ev.getPoint())
                    && ev.getX() - r.x < EntryEditor.this.px(STARW)
                    && SwingUtilities.isLeftMouseButton(ev)) {
                if (ev.getID() == MouseEvent.MOUSE_PRESSED) {
                    EntryEditor.this.toggledone(getModel().getElementAt(i).title);
                }
                ev.consume();
                return;
            }
            super.processMouseEvent(ev);
        }
    };
    private static final int STARW = 22;
    final Set<String> done = new HashSet<>();
    private final JButton donebtn = new JButton();
    final Set<String> checked = new HashSet<>();
    private final JTextField search = new JTextField();
    private final JComboBox<String> filter = new JComboBox<>(
            new String[] {"All", "Unfinished", "Done", "Yours", "Shared", "Wiki", "Missing", "Hidden", "Dismissed"});
    private final JTextField titlef = new JTextField();
    private final JTextArea area = new JTextArea();
    private final JComboBox<String> target;
    private final JLabel status = new JLabel(" ");
    private final JTextArea banner = new JTextArea(" ");
    private final JButton rawbtn = new JButton("Raw wiki");
    private final JButton revertbtn = new JButton("Revert to wiki");
    private final JButton restorebtn = new JButton("Restore");
    private boolean showingraw = false;
    private final EntryEditor.Preview preview = new EntryEditor.Preview();
    private final JComboBox<String> zoom = new JComboBox<>(new String[] {"Fit", "100%", "150%", "200%"});
    private JScrollPane previewpane;
    private LinksPanel links;
    private final Timer refresh;
    private EntryEditor.Item cur;
    private String basetitle = "";
    private String basetext = "";
    private final UndoManager areaundo = new UndoManager();
    private final UndoManager titleundo = new UndoManager();

    static {
        INFOBOXES.put(
                "Crafted",
                "{{Crafted\n| Title=Crafting\n| Objects required=Name;1, Name;1\n| Skills required=\n| Weight=\n}}\n");
        INFOBOXES.put("Foraged", "{{Foraged\n| Title=Foraging\n| Where found=\n| Weight=\n}}\n");
        INFOBOXES.put(
                "Food",
                "{{SpecialFood\n"
                        + "| Title=Food\n"
                        + "| Heals=0,0,0,0\n"
                        + "| Uses=1\n"
                        + "| Min Blood= | Max Blood=\n"
                        + "| Min Phlegm= | Max Phlegm=\n"
                        + "| Min Yellow Bile= | Max Yellow Bile=\n"
                        + "| Min Black Bile= | Max Black Bile=\n"
                        + "| Gluttony Time=\n"
                        + "| Food Groups=\n"
                        + "}}\n");
        INFOBOXES.put(
                "Clothing",
                "{{Clothing\n| Title=Clothing\n| Equipment Slot=\n| Thermal Value=\n| Artificer Slots=\n}}\n");
        INFOBOXES.put(
                "Structure",
                "{{Structure\n"
                        + "| Title=Structure\n"
                        + "| Size=\n"
                        + "| Objects required=Name;1\n"
                        + "| Needs lighting?=\n"
                        + "| Liftable?=\n"
                        + "| Repaired with=\n"
                        + "}}\n");
        INFOBOXES.put(
                "Creature",
                "{{Creature\n"
                        + "| Title=Creature\n"
                        + "| Where found=\n"
                        + "| Skill to attack=\n"
                        + "| Blood (health)=\n"
                        + "| Items gained=\n"
                        + "| Rare items=\n"
                        + "}}\n");
        INFOBOXES.put(
                "Skill",
                "{{Skill\n"
                        + "| Title=Skill\n"
                        + "| Description=\n"
                        + "| Crafts unlocked=\n"
                        + "| Builds unlocked=\n"
                        + "| Skills unlocked=\n"
                        + "}}\n");
        INFOBOXES.put("Artifact", "{{Artifact\n| Title=Artifact\n| Difficulty=\n| Proficiency Type=\n}}\n");
    }

    public static class Item {
        public final String title;
        public final String source;
        public final String kind;
        public final String res;

        Item(String title, String source, String kind, String res) {
            this.title = title;
            this.source = source;
            this.kind = kind;
            this.res = res;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public EntryEditor(File shared) {
        super(TITLE);
        this.shared = shared;
        ui = Math.max(1.0, Toolkit.getDefaultToolkit().getScreenResolution() / 96.0);
        target = new JComboBox<>(
                shared != null
                        ? new String[] {"Save to: your folder", "Save to: shared (repo)"}
                        : new String[] {"Save to: your folder"});
        zoom.setSelectedIndex(0);
        refresh = new Timer(300, ev -> updpreview());
        refresh.setRepeats(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (EntryEditor.this.confirmdiscard()) {
                    EntryEditor.this.dispose();
                    System.exit(0);
                }
            }
        });
        loadchecked();
        loaddone();
        build();
        reload();
        setSize((int) (1500 * ui), (int) (900 * ui));
        setLocationRelativeTo(null);
    }

    int px(int v) {
        return (int) Math.round(v * ui);
    }

    private void build() {
        JPanel left = new JPanel(new BorderLayout(0, px(4)));
        JPanel lt = new JPanel(new BorderLayout(px(4), 0));
        lt.add(search, BorderLayout.CENTER);
        lt.add(filter, BorderLayout.EAST);
        left.add(lt, BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new EntryEditor.Row());
        list.setFixedCellHeight(px(22));
        list.setPrototypeCellValue(null);
        list.addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting() && list.getSelectedValue() != null && list.getSelectedValue() != cur) {
                open(list.getSelectedValue());
            }
        });
        left.add(new JScrollPane(list), BorderLayout.CENTER);
        left.add(status, BorderLayout.SOUTH);
        search.getDocument().addDocumentListener(onchange(this::refilter));
        filter.addActionListener(ev -> refilter());
        JPanel center = new JPanel(new BorderLayout(0, px(4)));
        JPanel top = new JPanel(new BorderLayout(px(4), 0));
        top.add(new JLabel("Title:"), BorderLayout.WEST);
        top.add(titlef, BorderLayout.CENTER);
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        JPanel bn = new JPanel(new BorderLayout(px(6), 0));
        banner.setOpaque(true);
        banner.setEditable(false);
        banner.setLineWrap(true);
        banner.setWrapStyleWord(true);
        banner.setFocusable(false);
        banner.setFont(titlef.getFont());
        banner.setBorder(BorderFactory.createEmptyBorder(px(6), px(10), px(6), px(10)));
        bn.add(banner, BorderLayout.CENTER);
        JPanel bb = new JPanel(new FlowLayout(FlowLayout.RIGHT, px(4), 0));
        rawbtn.setToolTipText("Switch between the tidied text and the wiki's original source");
        rawbtn.addActionListener(ev -> toggleraw());
        revertbtn.setToolTipText("Delete your version so the game uses the wiki page again");
        revertbtn.addActionListener(ev -> revert());
        restorebtn.setToolTipText("Bring this wiki page back into the game");
        restorebtn.addActionListener(ev -> restore());
        bb.add(rawbtn);
        bb.add(revertbtn);
        bb.add(restorebtn);
        bn.add(bb, BorderLayout.EAST);
        north.add(bn);
        north.add(Box.createVerticalStrut(px(4)));
        north.add(top);
        bn.setAlignmentX(0.0F);
        top.setAlignmentX(0.0F);
        center.add(north, BorderLayout.NORTH);
        area.setFont(new Font("Consolas", Font.PLAIN, px(15)));
        area.setBackground(FayteSkin.PANEL);
        area.setForeground(FayteSkin.TEXT);
        area.setCaretColor(FayteSkin.TEXT);
        area.setSelectionColor(new Color(0x35, 0x55, 0x75));
        area.setMargin(new Insets(px(6), px(8), px(6), px(8)));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setTabSize(2);
        area.getDocument().addDocumentListener(onchange(() -> {
            refresh.restart();
        }));
        titlef.getDocument().addDocumentListener(onchange(() -> {
            refresh.restart();
        }));
        JPanel edit = new JPanel(new BorderLayout(px(4), 0));
        JScrollPane ins = new JScrollPane(
                insertpanel(), JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        ins.setBorder(null);
        edit.add(ins, BorderLayout.WEST);
        edit.add(new JScrollPane(area), BorderLayout.CENTER);
        center.add(edit, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, px(6), 0));
        JButton save = new JButton("Save");
        save.addActionListener(ev -> this.save());
        undoable(area, areaundo);
        undoable(titlef, titleundo);
        JButton undo = new JButton("Undo");
        undo.setToolTipText("Undo (Ctrl+Z)");
        undo.addActionListener(ev -> this.undo(areaundo, true));
        JButton redo = new JButton("Redo");
        redo.setToolTipText("Redo (Ctrl+Y)");
        redo.addActionListener(ev -> this.undo(areaundo, false));
        JButton nw = new JButton("New");
        nw.addActionListener(ev -> newentry("New Entry", ""));
        JButton del = new JButton("Delete");
        del.addActionListener(ev -> delete());
        JButton alias = new JButton("Alias\u2026");
        alias.addActionListener(ev -> this.alias());
        JButton wiki = new JButton("Open on wiki");
        wiki.addActionListener(ev -> openwiki());
        bottom.add(undo);
        bottom.add(redo);
        bottom.add(nw);
        donebtn.addActionListener(ev -> {
            String t = titlef.getText().trim();
            if (!t.isEmpty()) {
                toggledone(t);
            }
        });
        bottom.add(donebtn);
        bottom.add(target);
        bottom.add(save);
        bottom.add(del);
        bottom.add(alias);
        bottom.add(wiki);
        center.add(bottom, BorderLayout.SOUTH);
        JPanel right = new JPanel(new BorderLayout(0, px(4)));
        JPanel rt = new JPanel(new FlowLayout(FlowLayout.LEFT, px(6), 0));
        rt.add(new JLabel("Preview (as in the game)"));
        rt.add(zoom);
        zoom.addActionListener(ev -> updpreview());
        right.add(rt, BorderLayout.NORTH);
        JScrollPane ps = new JScrollPane(preview);
        previewpane = ps;
        ps.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        ps.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent ev) {
                if (EntryEditor.this.zoom.getSelectedIndex() == 0) {
                    EntryEditor.this.refresh.restart();
                }
            }
        });
        ps.getVerticalScrollBar().setUnitIncrement(px(24));
        right.add(ps, BorderLayout.CENTER);
        links = new LinksPanel(this);
        JTabbedPane rtabs = new JTabbedPane();
        rtabs.addTab("Preview", right);
        rtabs.addTab("In-game links", links);
        JSplitPane inner = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, center, rtabs);
        inner.setResizeWeight(0.5);
        addComponentListener(new ComponentAdapter() {
            private boolean done = false;

            @Override
            public void componentShown(ComponentEvent ev) {
                if (!done) {
                    done = true;
                    SwingUtilities.invokeLater(() -> inner.setDividerLocation(0.5));
                }
            }
        });
        JSplitPane outer = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, inner);
        outer.setDividerLocation(px(300));
        getContentPane().add(outer);
    }

    private JPanel insertpanel() {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(BorderFactory.createEmptyBorder(px(2), px(2), px(2), px(8)));
        JLabel title = new JLabel("Insert");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() * 1.1F));
        title.setForeground(FayteSkin.TEXT);
        title.setAlignmentX(0.0F);
        col.add(title);
        group(col, "Text");
        item(col, tool("Heading", "Add a section heading", () -> insertblock("==Heading==\n")));
        item(col, tool("Bullet", "Start a bullet point", () -> insertblock("* ")));
        item(col, tool("Link\u2026", "Link the selected words to another entry", this::link));
        group(col, "Pictures");
        item(col, tool("Icon\u2026", "Put a small game icon in the text", this::icon));
        item(col, tool("Picture\u2026", "Add a picture with an optional caption", this::picture));
        item(
                col,
                tool(
                        "Header icons\u2026",
                        "Add an icon next to the entry's title (use several for variants)",
                        this::headericon));
        group(col, "Layout");
        item(col, menu("Row / Grid", "Put pictures, icons or text side by side", new String[][] {
            {"Row of 2", "{{Row\n| [[File:first.png]]\n| [[File:second.png]]\n}}\n"},
            {"Row of 3", "{{Row\n| first cell\n| second cell\n| third cell\n}}\n"},
            {"Grid, 3 columns", "{{Grid\n| columns=3\n| cell 1\n| cell 2\n| cell 3\n| cell 4\n| cell 5\n| cell 6\n}}\n"
            },
        }));
        item(col, menu("Table", "Insert a table (header cells start with !, data cells with |)", new String[][] {
            {
                "Blank table",
                "{|\n! Header !! Header !! Header\n|-\n| cell || cell || cell\n|-\n| cell || cell || cell\n|}\n"
            },
            {
                "Food chart",
                "{|\n"
                        + "! !! Blood !! Phlegm !! Yellow !! Black\n"
                        + "|-\n"
                        + "! Heals\n"
                        + "| 0 || 0 || 0 || 0\n"
                        + "|-\n"
                        + "! Gluttony min\n"
                        + "| 0 || 0 || 0 || 0\n"
                        + "|-\n"
                        + "! Gluttony max\n"
                        + "| 0 || 0 || 0 || 0\n"
                        + "|}\n"
            },
            {
                "Chart with highlighted cells",
                "{|\n"
                        + "|+ Gluttony ranges\n"
                        + "! !! Blood !! Phlegm !! Yellow !! Black\n"
                        + "|-\n"
                        + "! Blood min\n"
                        + "| style=\"background:#e6b8b8\" | 0 || 0 || 0 || 0\n"
                        + "|-\n"
                        + "! Blood max\n"
                        + "| style=\"background:#e6b8b8\" | 0 || 0 || 0 || 0\n"
                        + "|-\n"
                        + "! Phlegm min\n"
                        + "| 0 || style=\"background:#6fa0ff\" | 0 || 0 || 0\n"
                        + "|-\n"
                        + "! Phlegm max\n"
                        + "| 0 || style=\"background:#6fa0ff\" | 0 || 0 || 0\n"
                        + "|-\n"
                        + "! Yellow min\n"
                        + "| 0 || 0 || style=\"background:#f0d860\" | 0 || 0\n"
                        + "|-\n"
                        + "! Yellow max\n"
                        + "| 0 || 0 || style=\"background:#f0d860\" | 0 || 0\n"
                        + "|-\n"
                        + "! Black min\n"
                        + "| 0 || 0 || 0 || style=\"background:#b8c8e6\" | 0\n"
                        + "|-\n"
                        + "! Black max\n"
                        + "| 0 || 0 || 0 || style=\"background:#b8c8e6\" | 0\n"
                        + "|}\n"
            },
        }));
        group(col, "Boxes");
        item(
                col,
                tool(
                        "Note box",
                        "A highlighted box for warnings or tips",
                        () -> insertblock("{{Notice|Note|Your text here.}}\n")));
        item(
                col,
                tool(
                        "Fact box",
                        "A box with your own header and labels (change Title to rename it)",
                        () -> insertblock("{{Facts\n| Title=At a glance\n| Found in=\n| Used for=\n}}\n")));
        String[][] ib = new String[INFOBOXES.size()][];
        int i = 0;

        for (Map.Entry<String, String> en : INFOBOXES.entrySet()) {
            ib[i++] = new String[] {en.getKey(), en.getValue()};
        }
        item(col, menu("Info box", "Insert one of the wiki's info boxes", ib));
        col.add(Box.createVerticalGlue());
        return col;
    }

    private void group(JPanel col, String name) {
        col.add(Box.createVerticalStrut(px(12)));
        JLabel l = new JLabel(name.toUpperCase());
        l.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D() * 0.85F));
        l.setForeground(new Color(0x8A, 0x8A, 0x8A));
        l.setAlignmentX(0.0F);
        col.add(l);
        col.add(Box.createVerticalStrut(px(4)));
    }

    private void item(JPanel col, JButton b) {
        b.setAlignmentX(0.0F);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        Dimension d = new Dimension(px(150), px(28));
        b.setPreferredSize(d);
        b.setMaximumSize(d);
        b.setMinimumSize(d);
        col.add(b);
        col.add(Box.createVerticalStrut(px(4)));
    }

    private JButton menu(String label, String tip, String[][] entries) {
        JButton b = new JButton(label + "  \u25b8");
        b.setToolTipText(tip);
        JPopupMenu m = new JPopupMenu();

        for (String[] en : entries) {
            JMenuItem mi = new JMenuItem(en[0]);
            mi.addActionListener(ev -> insertblock(en[1]));
            m.add(mi);
        }
        b.addActionListener(ev -> m.show(b, b.getWidth(), 0));
        return b;
    }

    private JButton tool(String label, String tip, Runnable r) {
        JButton b = new JButton(label);
        b.setToolTipText(tip);
        b.addActionListener(ev -> r.run());
        return b;
    }

    private DocumentListener onchange(Runnable r) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        };
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static void write(File f, String text) throws Exception {
        f.getParentFile().mkdirs();
        FaytePaths.write(f, text.getBytes(StandardCharsets.UTF_8));
    }

    private void scan(File dir, String source, List<EntryEditor.Item> into) {
        File[] fs = dir == null ? null : dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                String n = f.getName();
                if (f.isFile() && n.endsWith(FayteEntries.EXT) && !n.startsWith("_")) {
                    into.add(new EntryEditor.Item(
                            n.substring(0, n.length() - FayteEntries.EXT.length()), source, "", null));
                }
            }
        }
    }

    private void reload() {
        FayteEntries.load();
        all.clear();
        scan(FaytePaths.entries(), YOURS, all);
        scan(shared, SHARED, all);
        FayteWikiData.Store s = FayteWikiData.get();
        if (s != null) {
            Set<String> own = new HashSet<>();
            for (EntryEditor.Item it : all) {
                own.add(it.title.toLowerCase());
            }
            for (String t : s.pages.keySet()) {
                if (!own.contains(t.toLowerCase())) {
                    all.add(new EntryEditor.Item(t, FayteEntries.hidden(t) ? HIDDEN : WIKI, "", null));
                }
            }
        }
        File mf = new File(FaytePaths.fayte(), "missing_entries.txt");
        if (mf.exists()) {
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(new FileInputStream(mf), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    boolean dismissed = line.startsWith(FayteMissing.DONE);
                    if (dismissed) {
                        line = line.substring(FayteMissing.DONE.length());
                    }
                    String[] p = line.split("\t");
                    if (!line.startsWith("#") && p.length >= 2) {
                        String res = p.length > 2 ? p[2] : null;
                        boolean fixed = FayteWikiData.find(p[1]) != null
                                || (res != null && !res.isEmpty() && FayteEntries.alias(res) != null);
                        if (!fixed) {
                            all.add(new EntryEditor.Item(p[1], dismissed ? DISMISSED : MISSING, p[0], res));
                        }
                    }
                }
            } catch (Exception e) {
                status("Could not read the missing list: " + e.getMessage());
            }
        }
        Collections.sort(all, (a, b) -> {
            int c = rank(a.source) - rank(b.source);
            return c != 0 ? c : a.title.compareToIgnoreCase(b.title);
        });
        refilter();
    }

    private static int rank(String source) {
        switch (source) {
            case YOURS:
                return 0;
            case SHARED:
                return 1;
            case MISSING:
                return 2;
            case HIDDEN:
                return 4;
            case DISMISSED:
                return 5;
            default:
                return 3;
        }
    }

    private void refilter() {
        String q = search.getText().trim().toLowerCase();
        String f = ((String) filter.getSelectedItem()).toLowerCase();
        DefaultListModel<EntryEditor.Item> m = new DefaultListModel<>();

        for (EntryEditor.Item it : all) {
            boolean dn = done.contains(it.title.toLowerCase());
            boolean away = it.source.equals(HIDDEN) || it.source.equals(DISMISSED);
            boolean ok = (!away && (f.equals("all") || (f.equals("unfinished") && !dn) || (f.equals("done") && dn)))
                    || it.source.equals(f);
            if (ok && (q.isEmpty() || it.title.toLowerCase().contains(q))) {
                m.addElement(it);
            }
        }
        model = m;
        list.setModel(m);
        if (cur != null && m.contains(cur)) {
            list.setSelectedValue(cur, false);
        }
        status(m.size() + " entries");
    }

    private class Row extends JComponent implements ListCellRenderer<EntryEditor.Item> {
        private EntryEditor.Item it;
        private boolean sel;

        @Override
        public Component getListCellRendererComponent(
                JList<? extends EntryEditor.Item> l, EntryEditor.Item v, int i, boolean sel, boolean foc) {
            it = v;
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
            if (it == null) {
                return;
            }
            FontMetrics fm = g.getFontMetrics(getFont());
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            boolean dn = EntryEditor.this.done.contains(it.title.toLowerCase());
            star(g, EntryEditor.this.px(STARW) / 2, getHeight() / 2, EntryEditor.this.px(7), dn);
            int x = EntryEditor.this.px(STARW + 2);
            g.setFont(getFont());
            g.setColor(dn ? new Color(0xB8, 0xB2, 0xA4) : FayteSkin.TEXT);
            g.drawString(it.title, x, y);
            x += fm.stringWidth(it.title) + EntryEditor.this.px(6);
            String tag = "\u00b7 " + (it.source.equals(MISSING) ? "missing " + it.kind : it.source)
                    + (EntryEditor.this.checked.contains(it.title.toLowerCase()) ? "  \u2713" : "");
            g.setColor(new Color(0x8A, 0x8A, 0x8A));
            g.drawString(tag, x, y);
        }
    }

    void status(String s) {
        status.setText(s);
    }

    private boolean isdirty() {
        return !titlef.getText().equals(basetitle) || !area.getText().equals(basetext);
    }

    private void accept() {
        basetitle = titlef.getText();
        basetext = area.getText();
    }

    private boolean confirmdiscard() {
        if (!isdirty()) {
            return true;
        } else {
            int r = JOptionPane.showConfirmDialog(
                    this,
                    "Discard your unsaved changes to \"" + titlef.getText() + "\"?",
                    TITLE,
                    JOptionPane.YES_NO_OPTION);
            return r == JOptionPane.YES_OPTION;
        }
    }

    private void undoable(JTextComponent c, UndoManager um) {
        um.setLimit(500);
        c.getDocument().addUndoableEditListener(um);
        int mod = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        c.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, mod), "fayte-undo");
        c.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, mod), "fayte-redo");
        c.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, mod | InputEvent.SHIFT_MASK), "fayte-redo");
        c.getActionMap().put("fayte-undo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                EntryEditor.this.undo(um, true);
            }
        });
        c.getActionMap().put("fayte-redo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                EntryEditor.this.undo(um, false);
            }
        });
    }

    private void undo(UndoManager um, boolean back) {
        try {
            if (back && um.canUndo()) {
                um.undo();
            } else if (!back && um.canRedo()) {
                um.redo();
            }
        } catch (CannotUndoException | CannotRedoException e) {
        }
    }

    private void settext(String title, String text) {
        titlef.setText(title);
        updonebtn();
        area.setText(text);
        area.setCaretPosition(0);
        areaundo.discardAllEdits();
        titleundo.discardAllEdits();
        accept();
        updpreview();
        if (links != null) {
            links.show(title);
        }
    }

    static void star(Graphics2D g, int cx, int cy, int r, boolean on) {
        Path2D.Double p = new Path2D.Double();

        for (int k = 0; k < 10; k++) {
            double a = -Math.PI / 2 + k * Math.PI / 5;
            double rr = k % 2 == 0 ? r : r * 0.45;
            double px = cx + Math.cos(a) * rr;
            double py = cy + Math.sin(a) * rr;
            if (k == 0) {
                p.moveTo(px, py);
            } else {
                p.lineTo(px, py);
            }
        }
        p.closePath();
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (on) {
            g.setColor(new Color(0xE8, 0xB9, 0x3A));
            g.fill(p);
        } else {
            g.setColor(new Color(0x55, 0x5B, 0x66));
            g.draw(p);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    File donefile() {
        return shared != null ? new File(shared.getParentFile(), "done.txt") : new File(FaytePaths.fayte(), "done.txt");
    }

    void loaddone() {
        done.clear();

        for (String l : LinksPanel.lines(donefile())) {
            if (!l.trim().isEmpty() && !l.startsWith("#")) {
                done.add(l.trim().toLowerCase());
            }
        }
    }

    void toggledone(String title) {
        String k = title.trim().toLowerCase();
        boolean now = !done.remove(k);
        if (now) {
            done.add(k);
        }
        List<String> ls = new ArrayList<>(done);
        Collections.sort(ls);
        StringBuilder sb = new StringBuilder("# Entries marked done in the Entry Editor (the star in the list).\n");

        for (String l : ls) {
            sb.append(l).append('\n');
        }
        try {
            write(donefile(), sb.toString());
        } catch (Exception ex) {
            status("Could not save the done list: " + ex.getMessage());
        }
        updonebtn();
        String f = ((String) filter.getSelectedItem()).toLowerCase();
        if (f.equals("unfinished") || f.equals("done")) {
            refilter();
        } else {
            list.repaint();
        }
        status((now ? "Marked done: " : "Not done any more: ") + title.trim());
    }

    private void updonebtn() {
        boolean dn = done.contains(titlef.getText().trim().toLowerCase());
        donebtn.setText(dn ? "\u2605 Done" : "\u2606 Mark done");
        donebtn.setToolTipText(
                dn ? "This entry is marked done. Click to undo." : "Mark this entry as done (the star in the list)");
    }

    File checkedfile() {
        return shared != null
                ? new File(shared.getParentFile(), "checked.txt")
                : new File(FaytePaths.fayte(), "checked.txt");
    }

    void loadchecked() {
        checked.clear();

        for (String l : LinksPanel.lines(checkedfile())) {
            if (!l.trim().isEmpty() && !l.startsWith("#")) {
                checked.add(l.trim().toLowerCase());
            }
        }
    }

    void savechecked() {
        List<String> ls = new ArrayList<>(checked);
        Collections.sort(ls);
        StringBuilder sb = new StringBuilder(
                "# Entries checked in the Entry Editor: they open from the right things in the game.\n");

        for (String l : ls) {
            sb.append(l).append('\n');
        }
        try {
            write(checkedfile(), sb.toString());
        } catch (Exception e) {
            status("Could not save the checked list: " + e.getMessage());
        }
        list.repaint();
    }

    private File aliasfile() {
        return target.getSelectedIndex() == 1 && shared != null
                ? new File(shared.getParentFile(), FayteEntries.ALIASES)
                : new File(FaytePaths.fayte(), FayteEntries.ALIASES);
    }

    void addalias(String key, String title) {
        removealias(key);
        File af = aliasfile();

        try {
            String old = af.exists() ? read(af) : "";
            write(af, old + (old.isEmpty() || old.endsWith("\n") ? "" : "\n") + key.trim() + " = " + title + "\n");
        } catch (Exception e) {
            status("Could not add the alias: " + e.getMessage());
        }
    }

    void removealias(String key) {
        String k = key.trim().toLowerCase().replace('_', ' ');
        File[] fs = {
            new File(FaytePaths.fayte(), FayteEntries.ALIASES),
            shared == null ? null : new File(shared.getParentFile(), FayteEntries.ALIASES)
        };

        for (File f : fs) {
            if (f != null && f.exists()) {
                List<String> keep = new ArrayList<>();
                boolean changed = false;

                for (String l : LinksPanel.lines(f)) {
                    int eq = l.indexOf('=');
                    if (!l.trim().startsWith("#")
                            && eq > 0
                            && l.substring(0, eq)
                                    .trim()
                                    .toLowerCase()
                                    .replace('_', ' ')
                                    .equals(k)) {
                        changed = true;
                    } else {
                        keep.add(l);
                    }
                }
                if (changed) {
                    try {
                        write(f, String.join("\n", keep).replaceAll("\n+$", "") + "\n");
                    } catch (Exception e) {
                        status("Could not update " + f + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    private static final Color INFO = new Color(0x2B, 0x3A, 0x4E);
    private static final Color MINE = new Color(0x2E, 0x45, 0x33);
    private static final Color NEW = new Color(0x4A, 0x3D, 0x22);

    private void banner(String text, Color bg, boolean raw, boolean revert) {
        banner.setText(text.replaceAll("<[^>]+>", ""));
        banner.setBackground(bg);
        banner.setForeground(FayteSkin.TEXT);
        rawbtn.setVisible(raw);
        revertbtn.setVisible(revert);
        restorebtn.setVisible(cur != null && (cur.source.equals(HIDDEN) || cur.source.equals(DISMISSED)));
    }

    private boolean onwiki(String title) {
        FayteWikiData.Store s = FayteWikiData.get();
        return s != null && s.pages.containsKey(title);
    }

    private String tidied(String title) {
        FayteWikiData.Entry e = FayteWikiData.get().entries().get(title);
        return e == null ? FayteWikiData.get().pages.get(title).text : FayteWikiTidy.tidy(e);
    }

    private void toggleraw() {
        if (cur == null || !cur.source.equals(WIKI) || !confirmdiscard()) {
            return;
        }
        showingraw = !showingraw;
        settext(cur.title, showingraw ? FayteWikiData.get().pages.get(cur.title).text : tidied(cur.title));
        rawbtn.setText(showingraw ? "Tidy text" : "Raw wiki");
    }

    private void revert() {
        if (cur == null || !(cur.source.equals(YOURS) || cur.source.equals(SHARED))) {
            return;
        }
        File f = new File(cur.source.equals(YOURS) ? FaytePaths.entries() : shared, cur.title + FayteEntries.EXT);
        int r = JOptionPane.showConfirmDialog(
                this,
                "Delete your version of \"" + cur.title + "\" so the game shows the wiki page again?",
                TITLE,
                JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION && f.delete()) {
            String t = cur.title;
            FayteEntries.hide(t, false);
            cur = null;
            reload();
            select(t, WIKI);
            status("Reverted " + t + " to the wiki page.");
        }
    }

    private void open(EntryEditor.Item it) {
        if (!confirmdiscard()) {
            list.setSelectedValue(cur, true);
            return;
        }
        cur = it;
        showingraw = false;
        rawbtn.setText("Raw wiki");

        try {
            if (it.source.equals(YOURS) || it.source.equals(SHARED)) {
                boolean mine = it.source.equals(YOURS);
                settext(it.title, read(new File(mine ? FaytePaths.entries() : shared, it.title + FayteEntries.EXT)));
                target.setSelectedIndex(mine ? 0 : Math.min(1, target.getItemCount() - 1));
                boolean ow = onwiki(it.title);
                banner(
                        (mine ? "<b>Your version.</b> " : "<b>Shared version</b> (in the repo). ")
                                + (ow
                                        ? "The game shows this instead of the wiki page. Save to update it."
                                        : "This entry isn't on the wiki; the game shows it as written."),
                        MINE,
                        false,
                        ow);
                status(mine ? "Your entry" : "Shared entry");
            } else if (it.source.equals(HIDDEN)) {
                settext(it.title, tidied(it.title));
                banner(
                        "<b>Deleted wiki page.</b> The game treats this as having no entry. Save to make your own"
                                + " version, or Restore to bring the wiki page back.",
                        NEW,
                        false,
                        false);
                status("Deleted from the game");
            } else if (it.source.equals(DISMISSED)) {
                blank(guess(it.title), skeleton(it.kind));
                cur = it;
                banner(
                        "<b>Dismissed</b> " + it.kind + ". It stays off the Missing list. Restore puts it back.",
                        NEW,
                        false,
                        false);
                status("Dismissed " + it.kind);
            } else if (it.source.equals(WIKI)) {
                settext(it.title, tidied(it.title));
                banner(
                        "<b>Wiki page</b>, tidied to what the game shows. The game already uses it as-is; you only"
                                + " need to save if you change something. Saving keeps your copy and leaves the wiki"
                                + " untouched.",
                        INFO,
                        true,
                        false);
                status("From the wiki");
            } else {
                blank(guess(it.title), skeleton(it.kind));
                cur = it;
                banner(
                        "<b>No entry yet</b> for this " + it.kind
                                + (it.res != null && !it.res.isEmpty() ? " (" + esc(it.res) + ")" : "")
                                + ". Write one here, or use <b>Alias\u2026</b> if it's really another page.",
                        NEW,
                        false,
                        false);
                status("Missing " + it.kind);
            }
        } catch (Exception e) {
            status("Could not open: " + e.getMessage());
        }
    }

    private static String guess(String name) {
        StringBuilder sb = new StringBuilder();

        for (String w : name.split("\\s+")) {
            if (!w.isEmpty()) {
                sb.append(sb.length() > 0 ? " " : "")
                        .append(Character.toUpperCase(w.charAt(0)))
                        .append(w.substring(1));
            }
        }
        return sb.toString();
    }

    private static String skeleton(String kind) {
        String box = "";
        if (kind.equals("world object")) {
            box = INFOBOXES.get("Structure");
        } else if (kind.equals("creature")) {
            box = INFOBOXES.get("Creature");
        } else if (kind.equals("item")) {
            box = INFOBOXES.get("Crafted");
        } else if (kind.equals("skill")) {
            box = INFOBOXES.get("Skill");
        } else if (kind.equals("recipe")) {
            box = INFOBOXES.get("Crafted");
        }
        return FayteWikiTidy.basics(null) + box + "\n==About==\nWhat it is and what it's for, in a sentence or two.\n";
    }

    private void newentry(String title, String text) {
        if (!confirmdiscard()) {
            return;
        }
        blank(title, text);
    }

    private void blank(String title, String text) {
        banner("<b>New entry.</b> Give it the exact in-game name as its title, then save.", NEW, false, false);
        cur = null;
        list.clearSelection();
        settext(title, text);
        titlef.requestFocus();
        titlef.selectAll();
    }

    private File targetdir() {
        return target.getSelectedIndex() == 1 && shared != null ? shared : FaytePaths.entries();
    }

    private void save() {
        String title = titlef.getText().trim();
        if (title.isEmpty()) {
            status("Give the entry a title first.");
            return;
        }
        File f = new File(targetdir(), FaytePaths.safename(title) + FayteEntries.EXT);
        if (f.exists() && (cur == null || !f.getName().equalsIgnoreCase(cur.title + FayteEntries.EXT))) {
            int r = JOptionPane.showConfirmDialog(
                    this,
                    "\"" + f.getName() + "\" already exists there. Replace it?",
                    TITLE,
                    JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            write(f, area.getText());
            accept();
            if (links != null) {
                links.refresh();
            }
            boolean isshared = targetdir() == shared;
            reload();
            selectsaved(title, isshared ? SHARED : YOURS);
            if (links != null) {
                links.show(title);
            }
            status("Saved to " + f.getParentFile()
                    + (isshared
                            ? ". Run build.bat so the client includes it."
                            : ". The game picks it up within 5 seconds."));
        } catch (Exception e) {
            status("Could not save: " + e.getMessage());
        }
    }

    private void select(String title, String source) {
        pick(title, source, false);
    }

    private void selectsaved(String title, String source) {
        pick(title, source, true);
    }

    private void pick(String title, String source, boolean keep) {
        for (int i = 0; i < model.size(); i++) {
            EntryEditor.Item it = model.get(i);
            if (it.title.equalsIgnoreCase(title) && it.source.equals(source)) {
                if (keep) {
                    cur = it;
                }
                boolean already = list.getSelectedIndex() == i;
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                if (!keep && already && cur != it) {
                    open(it);
                }
                return;
            }
        }
    }

    private void restore() {
        if (cur != null && cur.source.equals(DISMISSED)) {
            String t = cur.title;
            FayteMissing.dismiss(cur.kind, t, false);
            cur = null;
            reload();
            select(t, MISSING);
            status("Restored " + t + " to the Missing list.");
            return;
        }
        if (cur == null || !cur.source.equals(HIDDEN)) {
            return;
        }
        String t = cur.title;
        FayteEntries.hide(t, false);
        cur = null;
        reload();
        select(t, WIKI);
        status("Restored " + t + ".");
    }

    private void delete() {
        if (cur == null || cur.source.equals(HIDDEN) || cur.source.equals(DISMISSED)) {
            status("Nothing to delete here.");
            return;
        }
        String t = cur.title;
        if (cur.source.equals(MISSING)) {
            int r = JOptionPane.showConfirmDialog(
                    this,
                    "Remove \"" + t
                            + "\" from the Missing list?\nIt moves to the Dismissed filter and won't be listed again.",
                    TITLE,
                    JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) {
                FayteMissing.dismiss(cur.kind, t, true);
                cur = null;
                reload();
                settext("", "");
                status("Dismissed " + t);
            }
            return;
        }
        if (cur.source.equals(WIKI)) {
            int r = JOptionPane.showConfirmDialog(
                    this,
                    "Delete \"" + t
                            + "\"?\n"
                            + "The game will treat it as having no entry. You can bring it back from the Hidden"
                            + " filter.",
                    TITLE,
                    JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) {
                FayteEntries.hide(t, true);
                cur = null;
                reload();
                settext("", "");
                status("Deleted " + t + " (hidden from the game)");
            }
            return;
        }
        File f = new File(cur.source.equals(YOURS) ? FaytePaths.entries() : shared, t + FayteEntries.EXT);
        int r = JOptionPane.showConfirmDialog(
                this,
                "Delete \"" + t + "\"?"
                        + (onwiki(t)
                                ? "\nThe wiki page of the same name is hidden too, so the game shows no entry."
                                : ""),
                TITLE,
                JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION && f.delete()) {
            if (onwiki(t)) {
                FayteEntries.hide(t, true);
            }
            cur = null;
            reload();
            settext("", "");
            status("Deleted " + f.getName());
        }
    }

    private String pickentry(String title, String initial) {
        JPanel p = new JPanel(new BorderLayout(0, px(4)));
        JTextField q = new JTextField(initial == null ? "" : initial);
        DefaultListModel<String> m = new DefaultListModel<>();
        JList<String> l = new JList<>(m);
        Runnable fill = () -> {
            m.clear();
            String s = q.getText().trim().toLowerCase();
            int n = 0;

            for (EntryEditor.Item it : all) {
                if (!it.source.equals(MISSING)
                        && (s.isEmpty() || it.title.toLowerCase().contains(s))
                        && !m.contains(it.title)) {
                    m.addElement(it.title);
                    if (++n > 300) {
                        break;
                    }
                }
            }
            if (m.size() > 0) {
                l.setSelectedIndex(0);
            }
        };
        q.getDocument().addDocumentListener(onchange(fill));
        fill.run();
        p.add(q, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(l);
        sp.setPreferredSize(new Dimension(px(360), px(320)));
        p.add(sp, BorderLayout.CENTER);
        boolean[] picked = {false};
        l.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && l.getSelectedValue() != null) {
                    picked[0] = true;
                    SwingUtilities.getWindowAncestor(l).dispose();
                }
            }
        });
        int r = JOptionPane.showConfirmDialog(this, p, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r == JOptionPane.OK_OPTION || picked[0]) {
            return l.getSelectedValue() != null
                    ? l.getSelectedValue()
                    : (q.getText().trim().isEmpty() ? null : q.getText().trim());
        } else {
            return null;
        }
    }

    private void link() {
        String sel = area.getSelectedText();
        String t = pickentry("Link to which entry?", sel);
        if (t != null) {
            String ins = sel == null || sel.isEmpty()
                    ? "[[" + t + "]]"
                    : (sel.equalsIgnoreCase(t) ? "[[" + sel + "]]" : "[[" + t + "|" + sel + "]]");
            area.replaceSelection(ins);
            area.requestFocus();
        }
    }

    private void pickimage(int tab) {
        File yours = new File(targetdir(), "images");
        File sharedimg = shared == null ? null : new File(shared, "images");
        ImagePicker p = new ImagePicker(this, ui, yours, sharedimg, tab);
        p.setVisible(true);
        String r = p.result();
        if (r != null) {
            FayteEntries.load();
            if (r.startsWith("[[File:")) {
                insertblock(r + "\n");
            } else {
                area.replaceSelection(r + " ");
                area.requestFocus();
            }
        }
    }

    private void headericon() {
        File yours = new File(targetdir(), "images");
        ImagePicker p =
                new ImagePicker(this, ui, yours, shared == null ? null : new File(shared, "images"), ImagePicker.ICONS);
        p.setVisible(true);
        String r = p.result();
        if (r == null || !r.startsWith("{{icon|")) {
            return;
        }
        String name = r.substring(7, r.length() - 2).split("\\|")[0];
        String text = area.getText();
        Matcher m = Pattern.compile("(?im)^\\{\\{\\s*Icons\\s*(\\|[^\\n]*)?\\}\\}[ \\t]*$")
                .matcher(text);
        if (m.find()) {
            String line = m.group();
            String nl = line.substring(0, line.lastIndexOf("}}")) + "|" + name + "}}";
            area.replaceRange(nl, m.start(), m.end());
        } else {
            area.insert("{{Icons|" + name + "}}\n", 0);
        }
    }

    private static String normname(String s) {
        return s.trim().toLowerCase().replace(' ', '_');
    }

    private static int closing(String s, int from, String open, String close) {
        int depth = 0;
        int i = from;

        while (i < s.length() - 1) {
            if (s.startsWith(open, i)) {
                depth++;
                i += 2;
            } else if (s.startsWith(close, i)) {
                depth--;
                i += 2;
                if (depth == 0) {
                    return i;
                }
            } else {
                i++;
            }
        }
        return -1;
    }

    private static List<String> args(String inner) {
        List<String> ret = new ArrayList<>();
        int sq = 0;
        int cu = 0;
        StringBuilder cur = new StringBuilder();

        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (inner.startsWith("[[", i)) {
                sq++;
                cur.append("[[");
                i++;
            } else if (inner.startsWith("]]", i)) {
                sq--;
                cur.append("]]");
                i++;
            } else if (inner.startsWith("{{", i)) {
                cu++;
                cur.append("{{");
                i++;
            } else if (inner.startsWith("}}", i)) {
                cu--;
                cur.append("}}");
                i++;
            } else if (c == '|' && sq == 0 && cu == 0) {
                ret.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        ret.add(cur.toString());
        return ret;
    }

    void resize(String ref, int n, int value) {
        String text = area.getText();
        boolean pic = ref.startsWith("pic:");
        String target = normname(ref.substring(ref.indexOf(':') + 1));
        int seen = 0;
        int i = 0;

        while (i < text.length()) {
            int s = pic
                    ? indexofany(text, i, "[[File:", "[[Image:", "[[file:", "[[image:")
                    : text.toLowerCase().indexOf("{{icon|", i);
            if (s < 0) {
                break;
            }
            int end = pic ? closing(text, s, "[[", "]]") : closing(text, s, "{{", "}}");
            if (end < 0) {
                break;
            }
            List<String> a = args(text.substring(s + 2, end - 2));
            String name = pic ? a.get(0).substring(a.get(0).indexOf(':') + 1) : (a.size() > 1 ? a.get(1) : "");
            if (normname(name).equals(target)) {
                if (seen == n) {
                    StringBuilder sb = new StringBuilder();
                    if (pic) {
                        sb.append("[[")
                                .append(a.get(0))
                                .append('|')
                                .append(value)
                                .append("px");
                        for (int k = 1; k < a.size(); k++) {
                            if (!a.get(k).trim().matches("(?i)\\d*x?\\d*px")) {
                                sb.append('|').append(a.get(k));
                            }
                        }
                        sb.append("]]");
                    } else {
                        sb.append("{{")
                                .append(a.get(0))
                                .append('|')
                                .append(name)
                                .append('|')
                                .append(value)
                                .append("}}");
                    }
                    area.replaceRange(sb.toString(), s, end);
                    status((pic ? "Picture width" : "Icon size") + " set to " + value + " px");
                    return;
                }
                seen++;
            }
            i = end;
        }
        status("Could not find that " + (pic ? "picture" : "icon") + " in the text to resize.");
    }

    private static int indexofany(String s, int from, String... pats) {
        int best = -1;

        for (String p : pats) {
            int k = s.indexOf(p, from);
            if (k >= 0 && (best < 0 || k < best)) {
                best = k;
            }
        }
        return best;
    }

    private void icon() {
        pickimage(ImagePicker.ICONS);
    }

    private void picture() {
        pickimage(ImagePicker.WIKI);
    }

    private void insertblock(String text) {
        int pos = area.getCaretPosition();
        String before = area.getText().substring(0, pos);
        String pre = before.isEmpty() || before.endsWith("\n") ? "" : "\n";
        area.replaceSelection(pre + text);
        area.requestFocus();
    }

    private void alias() {
        String key = cur != null && cur.res != null && !cur.res.isEmpty()
                ? cur.res
                : (cur != null ? cur.title : titlef.getText());
        key = JOptionPane.showInputDialog(this, "Game name or resource name to redirect:", key);
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        String t = pickentry("Which entry should \"" + key.trim() + "\" open?", titlef.getText());
        if (t == null) {
            return;
        }
        addalias(key, t);
        reload();
        if (links != null) {
            links.refresh();
        }
        status("Alias added to " + aliasfile() + ": " + key.trim() + " = " + t);
    }

    private void openwiki() {
        String t = titlef.getText().trim();
        if (!t.isEmpty()) {
            try {
                Desktop.getDesktop()
                        .browse(new URI(FayteWikiData.PAGE + URLEncoder.encode(t.replace(' ', '_'), "UTF-8")));
            } catch (Exception e) {
                status("Could not open the browser: " + e.getMessage());
            }
        }
    }

    private void updpreview() {
        String title = titlef.getText().trim();
        String text = area.getText();
        FayteWikiEntry g = null;
        if (!title.isEmpty() || !text.trim().isEmpty()) {
            try {
                FayteWikiData.Entry e = FayteWikiText.parse(title.isEmpty() ? "Untitled" : title, text, null);
                e.custom = true;
                g = FayteWikiGlance.full(e, title);
                if (g.category == null && !e.cats.isEmpty()) {
                    g.category = e.cats.get(0);
                }
            } catch (RuntimeException ex) {
                status("Preview error: " + ex);
            }
        }
        int zi = zoom.getSelectedIndex();
        double z;
        if (zi == 0) {
            int vw = previewpane == null ? 0 : previewpane.getViewport().getWidth();
            z = vw > 50 ? Math.max(1.0, Math.min(3.0, (vw - 2) / (double) PREVIEWW)) : ui;
        } else {
            z = new double[] {1.0, 1.5, 2.0}[zi - 1];
        }
        preview.set(g, z);
    }

    private void follow(String target) {
        for (int pass = 0; pass < 2; pass++) {
            for (EntryEditor.Item it : all) {
                if (!it.source.equals(MISSING) && it.title.equalsIgnoreCase(target)
                        || (pass == 1
                                && FayteWikiData.find(target) != null
                                && it.title.equalsIgnoreCase(FayteWikiData.find(target).title)
                                && !it.source.equals(MISSING))) {
                    filter.setSelectedIndex(0);
                    search.setText("");
                    select(it.title, it.source);
                    return;
                }
            }
        }
        status("No entry for \"" + target + "\" yet.");
    }

    private class Preview extends JPanel {
        private FayteEntryLayout layout;
        private double z = 1.0;
        private FayteEntryLayout.Item hover;
        private FayteEntryLayout.Item over;
        private FayteEntryLayout.Item drag;
        private Coord dragstart;
        private Coord dragsz;

        Preview() {
            setBackground(FayteSkin.PANEL);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    FayteEntryLayout.Item it = Preview.this.handleat(e.getX(), e.getY());
                    if (it != null) {
                        Preview.this.drag = it;
                        Preview.this.dragstart = new Coord(e.getX(), e.getY());
                        Preview.this.dragsz = it.sz;
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    FayteEntryLayout.Item it = Preview.this.drag;
                    if (it != null) {
                        Preview.this.drag = null;
                        Coord ns = Preview.this.dragsz;
                        if (ns != null && !ns.equals(it.sz)) {
                            int v = it.ref.startsWith("pic:") ? ns.x : ns.y;
                            EntryEditor.this.resize(it.ref, it.refn, Math.max(8, v));
                        }
                        Preview.this.repaint();
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (Preview.this.drag != null) {
                        return;
                    }
                    FayteEntryLayout.Item it = Preview.this.at(e.getX(), e.getY());
                    if (it != null && it.link != null && EntryEditor.this.confirmdiscard()) {
                        EntryEditor.this.accept();
                        EntryEditor.this.follow(it.link);
                    }
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    FayteEntryLayout.Item it = Preview.this.drag;
                    if (it != null) {
                        double dx = (e.getX() - Preview.this.dragstart.x) / Preview.this.z;
                        double dy = (e.getY() - Preview.this.dragstart.y) / Preview.this.z;
                        double f = Math.max((it.sz.x + dx) / it.sz.x, (it.sz.y + dy) / it.sz.y);
                        f = Math.max(8.0 / Math.min(it.sz.x, it.sz.y), Math.min(f, (double) PREVIEWW / it.sz.x));
                        Preview.this.dragsz = new Coord((int) Math.round(it.sz.x * f), (int) Math.round(it.sz.y * f));
                        Preview.this.repaint();
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    FayteEntryLayout.Item ov = Preview.this.resizable(e.getX(), e.getY());
                    if (ov != Preview.this.over) {
                        Preview.this.over = ov;
                        Preview.this.repaint();
                    }
                    Preview.this.setCursor(Cursor.getPredefinedCursor(
                            Preview.this.handleat(e.getX(), e.getY()) != null
                                    ? Cursor.SE_RESIZE_CURSOR
                                    : (Preview.this.at(e.getX(), e.getY()) != null
                                            ? Cursor.HAND_CURSOR
                                            : Cursor.DEFAULT_CURSOR)));
                    FayteEntryLayout.Item it = Preview.this.at(e.getX(), e.getY());
                    if (it != Preview.this.hover) {
                        Preview.this.hover = it;
                        Preview.this.setToolTipText(it == null ? null : "Open " + it.link);
                        Preview.this.repaint();
                    }
                }
            });
        }

        void set(FayteWikiEntry g, double z) {
            this.z = z;
            layout = FayteEntryLayout.build(g, PREVIEWW, true);
            hover = null;
            setPreferredSize(new Dimension((int) (PREVIEWW * z), (int) (layout.height * z)));
            revalidate();
            repaint();
        }

        FayteEntryLayout.Item resizable(int mx, int my) {
            if (layout == null) {
                return null;
            }
            Coord p = new Coord((int) (mx / z), (int) (my / z));

            for (FayteEntryLayout.Item it : layout.items) {
                if (it.ref != null && !it.ref.startsWith("hicon:") && p.isect(it.c.sub(4, 4), it.sz.add(8, 8))) {
                    return it;
                }
            }
            return null;
        }

        FayteEntryLayout.Item handleat(int mx, int my) {
            FayteEntryLayout.Item it = resizable(mx, my);
            if (it == null) {
                return null;
            }
            double lx = mx / z;
            double ly = my / z;
            double hx = it.c.x + it.sz.x;
            double hy = it.c.y + it.sz.y;
            double tol = Math.max(6.0, Math.min(12.0, Math.min(it.sz.x, it.sz.y) / 3.0));
            return lx >= hx - tol && lx <= hx + 5 && ly >= hy - tol && ly <= hy + 5 ? it : null;
        }

        FayteEntryLayout.Item at(int mx, int my) {
            if (layout == null) {
                return null;
            }
            Coord p = new Coord((int) (mx / z), (int) (my / z));

            for (FayteEntryLayout.Item it : layout.items) {
                if (it.link != null && p.isect(it.c.sub(0, 2), it.sz.add(0, 4))) {
                    return it;
                }
            }
            return null;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (layout == null) {
                return;
            }
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.scale(z, z);
            g.setColor(FayteSkin.BORDER);
            g.fillRect(0, 0, PREVIEWW, 2);
            g.fillRect(0, 0, 2, layout.height);
            g.fillRect(PREVIEWW - 2, 0, 2, layout.height);
            g.fillRect(0, layout.height - 2, PREVIEWW, 2);

            for (FayteEntryLayout.Item it : layout.items) {
                switch (it.kind) {
                    case TEXT:
                        g.drawImage(it.t.hiresimg(z), it.c.x, it.c.y, it.sz.x, it.sz.y, null);
                        if (hover != null && it.link != null && it.link.equals(hover.link)) {
                            g.setColor(FayteText.LINK);
                            g.fillRect(it.c.x, it.c.y + it.sz.y - 2, it.sz.x, 1);
                        }
                        break;
                    case IMAGE:
                        g.drawImage(it.img, it.c.x, it.c.y, it.sz.x, it.sz.y, null);
                        break;
                    default:
                        if (it.fill != null) {
                            g.setColor(it.fill);
                            g.fillRect(it.c.x, it.c.y, it.sz.x, it.sz.y);
                        }
                        if (it.edge != null) {
                            g.setColor(it.edge);
                            g.fillRect(it.c.x, it.c.y, it.sz.x, FayteSkin.BW);
                            g.fillRect(it.c.x, it.c.y + it.sz.y - FayteSkin.BW, it.sz.x, FayteSkin.BW);
                            g.fillRect(it.c.x, it.c.y, FayteSkin.BW, it.sz.y);
                            g.fillRect(it.c.x + it.sz.x - FayteSkin.BW, it.c.y, FayteSkin.BW, it.sz.y);
                        }
                }
            }
            FayteEntryLayout.Item hl = drag != null ? drag : over;
            if (hl != null) {
                Coord s = drag != null && dragsz != null ? dragsz : hl.sz;
                g.setColor(new Color(0x8F, 0xC1, 0xE3));
                g.setStroke(new BasicStroke((float) (1.0 / z)));
                g.drawRect(hl.c.x, hl.c.y, s.x, s.y);
                g.fillRect(hl.c.x + s.x - 5, hl.c.y + s.y - 5, 8, 8);
                if (drag != null) {
                    g.drawString(s.x + " \u00d7 " + s.y, hl.c.x + 4, hl.c.y + s.y + 14);
                }
            }
            g.dispose();
        }
    }

    private static void scalefonts(double s) {
        Enumeration<Object> keys = UIManager.getLookAndFeelDefaults().keys();
        List<Object> ks = new ArrayList<>();

        while (keys.hasMoreElements()) {
            ks.add(keys.nextElement());
        }
        for (Object k : ks) {
            Object v = UIManager.get(k);
            if (v instanceof Font) {
                Font f = (Font) v;
                UIManager.put(k, new FontUIResource(f.getFamily(), f.getStyle(), (int) Math.round(f.getSize() * s)));
            }
        }
    }

    private static void dark() {
        Color bg = FayteSkin.PANEL;
        Color ctl = new Color(0x22, 0x26, 0x2D);
        Color edge = FayteSkin.BORDER;
        Color fg = FayteSkin.TEXT;
        Color sel = new Color(0x35, 0x55, 0x75);
        UIManager.put("control", ctl);
        UIManager.put("info", ctl);
        UIManager.put("nimbusBase", edge);
        UIManager.put("nimbusBlueGrey", edge);
        UIManager.put("nimbusLightBackground", bg);
        UIManager.put("nimbusFocus", new Color(0x8F, 0xC1, 0xE3));
        UIManager.put("nimbusSelectionBackground", sel);
        UIManager.put("nimbusSelectedText", Color.WHITE);
        UIManager.put("nimbusDisabledText", new Color(0x80, 0x80, 0x80));
        UIManager.put("nimbusInfoBlue", sel);
        UIManager.put("nimbusAlertYellow", new Color(0xD9, 0xB2, 0x6F));
        UIManager.put("text", fg);
        UIManager.put("textForeground", fg);
        UIManager.put("textBackground", bg);
        UIManager.put("background", ctl);
        UIManager.put("menu", ctl);
        UIManager.put("menuText", fg);
        UIManager.put("controlText", fg);
        UIManager.put("infoText", fg);
        UIManager.put("List.background", bg);
        UIManager.put("List.foreground", fg);
        UIManager.put("TextArea.background", bg);
        UIManager.put("TextArea.foreground", fg);
        UIManager.put("TextArea.caretForeground", fg);
        UIManager.put("TextField.caretForeground", fg);
        UIManager.put("ToolTip.background", ctl);
        UIManager.put("ToolTip.foreground", fg);
    }

    public static void main(String[] args) throws Exception {
        dark();

        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            FayteLog.once("EntryEditor.main", e);
        }
        scalefonts(Math.max(1.0, Toolkit.getDefaultToolkit().getScreenResolution() / 96.0));
        File shared = null;
        if (args.length > 0) {
            File d = new File(args[0]);
            if (d.isDirectory()) {
                shared = d.getCanonicalFile();
            }
        }
        FayteEntries.shareddir = shared;
        FayteWikiData.use(FayteWikiData.loadbest());
        File sh = shared;
        SwingUtilities.invokeLater(() -> {
            EntryEditor ed = new EntryEditor(sh);
            ed.setVisible(true);
            if (!FayteIconLibrary.built()) {
                ed.status("Building the icon library from the game's files\u2026");
                new Thread(
                                () -> FayteIconLibrary.build(m -> SwingUtilities.invokeLater(() -> ed.status(m))),
                                "Icon library")
                        .start();
            }
        });
    }
}
