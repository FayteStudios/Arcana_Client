package fayte.editor;

import haven.FayteIconLibrary;
import haven.FayteLog;
import haven.FaytePaths;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

public class ImagePicker extends JDialog {
    public static final int ICONS = 0;
    public static final int WIKI = 1;
    public static final int YOURS = 2;
    private static final int MAXSHOWN = 600;
    private static final Map<String, ImageIcon> thumbs = new ConcurrentHashMap<>();
    private static final ExecutorService loader = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Thumbnails");
        t.setDaemon(true);
        return t;
    });
    private final double ui;
    private final File yourimages;
    private final File sharedimages;
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextField search = new JTextField();
    private final JLabel info = new JLabel(" ");
    private final JComboBox<String> iconsize =
            new JComboBox<>(new String[] {"Text size", "24 px", "32 px", "48 px", "64 px"});
    private final JComboBox<String> picsize =
            new JComboBox<>(new String[] {"Fit width", "64 px", "120 px", "200 px", "300 px", "400 px"});
    private final JTextField caption = new JTextField(18);
    private final List<JList<File>> lists = new ArrayList<>();
    private final List<List<File>> sources = new ArrayList<>();
    private String result = null;

    public ImagePicker(Frame owner, double ui, File yourimages, File sharedimages, int tab) {
        super(owner, "Images", true);
        this.ui = ui;
        this.yourimages = yourimages;
        this.sharedimages = sharedimages;
        String[] names = {"Icons", "Wiki pictures", "Your pictures"};

        for (int i = 0; i < 3; i++) {
            sources.add(new ArrayList<>());
            JList<File> l = grid();
            lists.add(l);
            JScrollPane sp = new JScrollPane(l);
            sp.getVerticalScrollBar().setUnitIncrement(px(30));
            tabs.addTab(names[i], sp);
        }
        tabs.addChangeListener(ev -> refilter());
        JPanel top = new JPanel(new BorderLayout(px(6), 0));
        top.add(new JLabel("Search:"), BorderLayout.WEST);
        top.add(search, BorderLayout.CENTER);
        JButton rebuild = new JButton("Rebuild icon library");
        rebuild.setToolTipText("Read every icon from the game's files again");
        rebuild.addActionListener(ev -> this.rebuild(rebuild));
        top.add(rebuild, BorderLayout.EAST);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                ImagePicker.this.refilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                ImagePicker.this.refilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                ImagePicker.this.refilter();
            }
        });
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, px(6), 0));
        bottom.add(new JLabel("Icon size:"));
        bottom.add(iconsize);
        bottom.add(new JLabel("  Picture width:"));
        bottom.add(picsize);
        bottom.add(new JLabel("  Caption:"));
        bottom.add(caption);
        JButton file = new JButton("From file\u2026");
        file.addActionListener(ev -> fromfile());
        JButton insert = new JButton("Insert");
        insert.addActionListener(ev -> this.insert());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(ev -> dispose());
        bottom.add(file);
        bottom.add(insert);
        bottom.add(cancel);
        JPanel south = new JPanel(new BorderLayout());
        south.add(info, BorderLayout.NORTH);
        south.add(bottom, BorderLayout.CENTER);
        JPanel root = new JPanel(new BorderLayout(0, px(6)));
        root.setBorder(BorderFactory.createEmptyBorder(px(8), px(8), px(8), px(8)));
        root.add(top, BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        load();
        tabs.setSelectedIndex(tab);
        refilter();
        setSize(px(900), px(640));
        setLocationRelativeTo(owner);
    }

    private int px(int v) {
        return (int) Math.round(v * ui);
    }

    private JList<File> grid() {
        JList<File> l = new JList<>(new DefaultListModel<>());
        l.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        l.setVisibleRowCount(-1);
        l.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        l.setFixedCellWidth(px(120));
        l.setFixedCellHeight(px(110));
        l.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> jl, Object v, int i, boolean sel, boolean foc) {
                super.getListCellRendererComponent(jl, v, i, sel, foc);
                File f = (File) v;
                String n = f.getName();
                n = n.substring(0, n.lastIndexOf('.') > 0 ? n.lastIndexOf('.') : n.length());
                setText("<html><center>"
                        + (n.length() > 28 ? n.substring(0, 27) + "\u2026" : n)
                                .replace("&", "&amp;")
                                .replace("<", "&lt;")
                        + "</center></html>");
                setHorizontalAlignment(CENTER);
                setVerticalTextPosition(BOTTOM);
                setHorizontalTextPosition(CENTER);
                setIcon(ImagePicker.this.thumb(f, jl));
                setToolTipText(f.getName());
                return this;
            }
        });
        l.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && l.getSelectedValue() != null) {
                    ImagePicker.this.insert();
                }
            }
        });
        return l;
    }

    private ImageIcon thumb(File f, JList<?> l) {
        String k = f.getPath();
        ImageIcon t = thumbs.get(k);
        if (t == null) {
            thumbs.put(k, new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)));
            int max = px(64);
            loader.submit(() -> {
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) {
                        double s =
                                Math.min(1.0, Math.min((double) max / img.getWidth(), (double) max / img.getHeight()));
                        if (s >= 1.0 && img.getWidth() < max / 2) {
                            s = Math.min(2.0, (double) max / 2 / img.getWidth());
                        }
                        int tw = Math.max(1, (int) (img.getWidth() * s));
                        int th = Math.max(1, (int) (img.getHeight() * s));
                        BufferedImage sc = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = sc.createGraphics();
                        g.setRenderingHint(
                                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                        g.drawImage(img, 0, 0, tw, th, null);
                        g.dispose();
                        thumbs.put(k, new ImageIcon(sc));
                        SwingUtilities.invokeLater(l::repaint);
                    }
                } catch (Exception e) {
                    FayteLog.once("ImagePicker.thumb", e);
                }
            });
            return thumbs.get(k);
        } else {
            return t;
        }
    }

    private static void add(File dir, List<File> into) {
        File[] fs = dir == null ? null : dir.listFiles((d, n) -> n.matches("(?i).*\\.(png|jpe?g|gif)$"));
        if (fs != null) {
            Collections.addAll(into, fs);
        }
    }

    private void load() {
        for (List<File> s : sources) {
            s.clear();
        }
        add(FaytePaths.icons(), sources.get(ICONS));
        add(FaytePaths.wikiimages(), sources.get(WIKI));
        add(yourimages, sources.get(YOURS));
        add(sharedimages, sources.get(YOURS));

        for (List<File> s : sources) {
            s.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        }
    }

    private void refilter() {
        int t = tabs.getSelectedIndex();
        if (t < 0) {
            return;
        }
        String q = search.getText().trim().toLowerCase();
        DefaultListModel<File> m = (DefaultListModel<File>) lists.get(t).getModel();
        m.clear();
        int n = 0;
        int total = 0;

        for (File f : sources.get(t)) {
            if (q.isEmpty() || f.getName().toLowerCase().contains(q)) {
                total++;
                if (n < MAXSHOWN) {
                    m.addElement(f);
                    n++;
                }
            }
        }
        String what = t == ICONS ? "icons" : "pictures";
        if (sources.get(t).isEmpty()) {
            info.setText(
                    t == ICONS
                            ? "No icons yet. Click \"Rebuild icon library\"."
                            : (t == WIKI
                                    ? "No wiki pictures downloaded yet."
                                    : "No pictures of your own yet. Use \"From file\u2026\"."));
        } else {
            info.setText(
                    total > n
                            ? "Showing " + n + " of " + total + " " + what + ". Search to narrow it down."
                            : total + " " + what + ". Double-click or Insert to add one.");
        }
        iconsize.setEnabled(t == ICONS);
        picsize.setEnabled(t != ICONS);
        caption.setEnabled(t != ICONS);
    }

    private void rebuild(JButton b) {
        b.setEnabled(false);
        new Thread(
                        () -> {
                            FayteIconLibrary.build(s -> SwingUtilities.invokeLater(() -> info.setText(s)));
                            SwingUtilities.invokeLater(() -> {
                                load();
                                refilter();
                                b.setEnabled(true);
                            });
                        },
                        "Icon library")
                .start();
    }

    private void fromfile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Pictures (png, jpg, gif)", "png", "jpg", "jpeg", "gif"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File src = fc.getSelectedFile();
            File dst = new File(yourimages, src.getName().replace(' ', '_'));
            try {
                if (dst.exists() && !src.getCanonicalFile().equals(dst.getCanonicalFile())) {
                    String n = dst.getName();
                    int dot = n.lastIndexOf('.');
                    String stem = dot > 0 ? n.substring(0, dot) : n;
                    String ext = dot > 0 ? n.substring(dot) : "";
                    for (int k = 2; dst.exists(); k++) {
                        dst = new File(yourimages, stem + "_" + k + ext);
                    }
                }
            } catch (Exception e) {
                FayteLog.once("ImagePicker.import", e);
            }

            try {
                yourimages.mkdirs();
                if (!src.getCanonicalFile().equals(dst.getCanonicalFile())) {
                    Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                load();
                tabs.setSelectedIndex(YOURS);
                search.setText(dst.getName());
                refilter();
                lists.get(YOURS).setSelectedValue(dst, true);
            } catch (Exception e) {
                info.setText("Could not copy the picture: " + e.getMessage());
            }
        }
    }

    private void insert() {
        int t = tabs.getSelectedIndex();
        File f = lists.get(t).getSelectedValue();
        if (f == null) {
            info.setText("Pick an image first.");
            return;
        }
        String n = f.getName();
        if (t == ICONS) {
            String name = n.substring(0, n.length() - 4);
            String sz = iconsize.getSelectedIndex() == 0
                    ? ""
                    : "|" + ((String) iconsize.getSelectedItem()).replace(" px", "");
            result = "{{icon|" + name + sz + "}}";
        } else {
            String sz =
                    picsize.getSelectedIndex() == 0 ? "" : "|" + ((String) picsize.getSelectedItem()).replace(" ", "");
            String cap = caption.getText().trim();
            result = "[[File:" + n + sz + (cap.isEmpty() ? "" : "|" + cap) + "]]";
        }
        dispose();
    }

    public String result() {
        return result;
    }
}
