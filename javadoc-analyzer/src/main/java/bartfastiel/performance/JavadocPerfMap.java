package bartfastiel.performance;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Javadoc Performance Heatmap (Java SE / JDK 25 HTML-Javadoc, offline)
 * - Single file, no external deps.
 * - Swing UI: choose extracted directory OR open Oracle download page.
 * - Auto scan java.base/{java,javax} HTML pages for performance claims (Big-O and key phrases).
 *
 * Build/run:
 *   javac JavadocPerfMap.java
 *   java JavadocPerfMap
 */
public class JavadocPerfMap {

    // ---- Config: patterns for "performance claims"
    private static final Pattern BIG_O = Pattern.compile("\\bO\\s*\\(\\s*[^\\)\\n\\r]{1,40}\\s*\\)");
    private static final List<String> KEY_PHRASES = List.of(
            "constant time", "amortized", "logarithmic", "linear time", "worst-case", "average",
            "time complexity", "space complexity", "performance", "cost", "complexity",
            "in constant time", "in linear time", "in logarithmic time", "runs in", "typically",
            "expected time", "on average"
    );

    private static final DecimalFormat DF = new DecimalFormat("#,##0");

    // ---- Data models
    static final class FileFinding {
        Path file;
        String pkg;                 // derived package (java.util, javax.crypto, ...)
        int hits;                   // total hits (Big-O + phrase hits)
        int bigOCount;              // number of Big-O occurrences
        Set<String> bigOs = new TreeSet<>();
        List<String> snippets = new ArrayList<>(); // small excerpts (optional)
        int bytesRead;
    }

    static final class PkgSummary {
        String pkg;
        int hits;
        int bigOCount;
        Set<String> bigOs = new TreeSet<>();
        int fileCount;
        int bytesRead;

        double density() {
            if (bytesRead < 1) return 0.0;
            return (double) hits / (double) bytesRead;
        }
    }

    // ---- UI
    private final JFrame frame = new JFrame("Javadoc Performance Map (java.base)");
    private final JLabel status = new JLabel("Select extracted Javadoc folder (JDK 25 doc ZIP extracted) …");
    private final JButton chooseDirBtn = new JButton("Choose extracted Javadoc folder…");
    private final JButton openOracleBtn = new JButton("Open Oracle download page");
    private final JTextField oracleUrl = new JTextField(
            "https://www.oracle.com/java/technologies/javase-jdk25-doc-downloads.html"
    );

    private final HeatmapPanel heatmapPanel = new HeatmapPanel();
    private final JTable table = new JTable();
    private final FindingsTableModel tableModel = new FindingsTableModel();

    private final JCheckBox onlyBigO = new JCheckBox("Only Big-O hits", false);
    private final JCheckBox includePhrases = new JCheckBox("Include phrase hits", true);

    private final AtomicInteger scannedFiles = new AtomicInteger(0);

    // Analysis results
    private volatile List<FileFinding> findings = List.of();
    private volatile Map<String, PkgSummary> pkgSummaries = Map.of();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new JavadocPerfMap().start());
    }

    private void start() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(12, 12));

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setBorder(new EmptyBorder(10, 10, 0, 10));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(chooseDirBtn);
        buttons.add(openOracleBtn);

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        options.add(includePhrases);
        options.add(onlyBigO);

        JPanel urlPanel = new JPanel(new BorderLayout(8, 0));
        urlPanel.add(new JLabel("Oracle URL:"), BorderLayout.WEST);
        urlPanel.add(oracleUrl, BorderLayout.CENTER);

        JPanel topRow = new JPanel(new BorderLayout(8, 8));
        topRow.add(buttons, BorderLayout.WEST);
        topRow.add(options, BorderLayout.EAST);

        top.add(topRow, BorderLayout.NORTH);
        top.add(urlPanel, BorderLayout.CENTER);
        top.add(status, BorderLayout.SOUTH);

        chooseDirBtn.addActionListener(e -> chooseAndAnalyze());
        openOracleBtn.addActionListener(e -> openInBrowser(oracleUrl.getText().trim()));

        onlyBigO.addActionListener(e -> refreshView());
        includePhrases.addActionListener(e -> refreshView());

        table.setModel(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(heatmapPanel),
                new JScrollPane(table));
        split.setResizeWeight(0.62);

        frame.add(top, BorderLayout.NORTH);
        frame.add(split, BorderLayout.CENTER);

        frame.setSize(1200, 820);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void chooseAndAnalyze() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Select extracted Javadoc root folder (contains docs/api/...)");
        int res = fc.showOpenDialog(frame);
        if (res != JFileChooser.APPROVE_OPTION) {
            status.setText("No folder selected.");
            return;
        }
        Path selected = fc.getSelectedFile().toPath();
        startAnalysis(selected);
    }

    private void startAnalysis(Path selectedRoot) {
        scannedFiles.set(0);
        status.setText("analyzing Javadoc … (starting)");
        chooseDirBtn.setEnabled(false);

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            List<FileFinding> localFindings = new ArrayList<>();
            Map<String, PkgSummary> localPkgs = new HashMap<>();

            @Override
            protected Void doInBackground() {
                Path base = findJavaBaseRoot(selectedRoot);

                if (base == null) {
                    showText("Could not locate java.base under selected folder. Expected something like docs/api/java.base/ …");
                    return null;
                }

                showText("java.base root: " + base);

                // Scan only {java,javax} under java.base if present, else whole java.base
                List<Path> rootsToScan = new ArrayList<>();
                Path javaDir = base.resolve("java");
                Path javaxDir = base.resolve("javax");
                if (Files.isDirectory(javaDir)) rootsToScan.add(javaDir);
                if (Files.isDirectory(javaxDir)) rootsToScan.add(javaxDir);
                if (rootsToScan.isEmpty()) rootsToScan.add(base);

                for (Path r : rootsToScan) {
                    String t = "Scanning: " + r;
                    showText(t);
                    scanHtmlTree(r, base, localFindings, localPkgs, this::showText);
                }

                // sort findings by hits desc
                localFindings.sort((a, b) -> Integer.compare(b.hits, a.hits));
                return null;
            }

            private void showText(String t) {
                publish(t);
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) status.setText("analyzing Javadoc … " + chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                chooseDirBtn.setEnabled(true);

                // publish results to UI
                findings = localFindings;
                pkgSummaries = localPkgs;

                refreshView();

                int totalHits = findings.stream().mapToInt(f -> f.hits).sum();
                int totalBigO = findings.stream().mapToInt(f -> f.bigOCount).sum();
                status.setText("Done. Files scanned: " + DF.format(scannedFiles.get())
                        + " | Findings files: " + DF.format(findings.size())
                        + " | Hits: " + DF.format(totalHits)
                        + " | Big-O: " + DF.format(totalBigO)
                        + " | Packages: " + DF.format(pkgSummaries.size()));
            }
        };

        worker.execute();
    }

    private void refreshView() {
        // filter results based on UI toggles
        boolean onlyO = onlyBigO.isSelected();
        boolean phrases = includePhrases.isSelected();

        List<FileFinding> filtered = new ArrayList<>();
        for (FileFinding f : findings) {
            int hits = countHitsForFlags(f, onlyO, phrases);
            if (hits < 1) continue;
            FileFinding copy = shallowCopy(f);
            copy.hits = hits;
            filtered.add(copy);
        }
        filtered.sort((a, b) -> Integer.compare(b.hits, a.hits));
        tableModel.setData(filtered);

        Map<String, PkgSummary> pkgFiltered = new HashMap<>();
        for (FileFinding f : filtered) {
            String pkg = f.pkg == null ? "(unknown)" : f.pkg;
            PkgSummary s = pkgFiltered.computeIfAbsent(pkg, k -> {
                PkgSummary ps = new PkgSummary();
                ps.pkg = k;
                return ps;
            });
            s.hits += f.hits;
            s.bigOCount += f.bigOCount;
            s.bigOs.addAll(f.bigOs);
            s.fileCount += 1;
            s.bytesRead += f.bytesRead;
        }

        heatmapPanel.setPackages(pkgFiltered);
        heatmapPanel.repaint();
    }

    private static int countHitsForFlags(FileFinding f, boolean onlyBigO, boolean includePhrases) {
        if (onlyBigO) return f.bigOCount;
        if (!includePhrases) return f.bigOCount;
        return f.hits;
    }

    private static FileFinding shallowCopy(FileFinding f) {
        FileFinding c = new FileFinding();
        c.file = f.file;
        c.pkg = f.pkg;
        c.hits = f.hits;
        c.bigOCount = f.bigOCount;
        c.bigOs = new TreeSet<>(f.bigOs);
        c.snippets = f.snippets;
        c.bytesRead = f.bytesRead;
        return c;
    }

    // ---- Locate java.base root
    private static Path findJavaBaseRoot(Path selectedRoot) {
        // Common Oracle layout: <root>/docs/api/java.base/
        Path candidate1 = selectedRoot.resolve("docs").resolve("api").resolve("java.base");
        if (Files.isDirectory(candidate1)) return candidate1;

        // Sometimes: <root>/api/java.base/
        Path candidate2 = selectedRoot.resolve("api").resolve("java.base");
        if (Files.isDirectory(candidate2)) return candidate2;

        // Otherwise: search for directory "java.base" that has "java" or "javax" child and looks like Javadoc
        try {
            final Path[] found = {null};
            Files.walkFileTree(selectedRoot, EnumSet.noneOf(FileVisitOption.class), 6, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    Path name = dir.getFileName();
                    if (name != null && "java.base".equals(name.toString())) {
                        Path java = dir.resolve("java");
                        Path javax = dir.resolve("javax");
                        if (Files.isDirectory(java) || Files.isDirectory(javax)) {
                            found[0] = dir;
                            return FileVisitResult.TERMINATE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return found[0];
        } catch (IOException ignored) {
            return null;
        }
    }

    // ---- Scan HTML files
    private void scanHtmlTree(Path rootToScan, Path javaBaseRoot, List<FileFinding> out, Map<String, PkgSummary> pkgs,
                              java.util.function.Consumer<String> statusUpdater) {
        try {
            Files.walkFileTree(rootToScan, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    statusUpdater.accept("Scanning: " + file);
                    String fn = file.getFileName().toString();
                    if (!fn.endsWith(".html")) return FileVisitResult.CONTINUE;

                    scannedFiles.incrementAndGet();

                    FileFinding f = analyzeHtmlFile(file, javaBaseRoot);
                    if (f.hits > 0) {
                        out.add(f);
                        PkgSummary ps = pkgs.computeIfAbsent(f.pkg == null ? "(unknown)" : f.pkg, k -> {
                            PkgSummary s = new PkgSummary();
                            s.pkg = k;
                            return s;
                        });
                        ps.hits += f.hits;
                        ps.bigOCount += f.bigOCount;
                        ps.bigOs.addAll(f.bigOs);
                        ps.fileCount += 1;
                        ps.bytesRead += f.bytesRead;
                    }

                    if ((scannedFiles.get() % 250) == 0) {
                        SwingUtilities.invokeLater(() ->
                                status.setText("analyzing Javadoc … scanned " + DF.format(scannedFiles.get()) + " files"));
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> status.setText("Scan error: " + e.getMessage()));
        }
    }

    private static FileFinding analyzeHtmlFile(Path html, Path javaBaseRoot) {
        FileFinding ff = new FileFinding();
        ff.file = html;
        ff.pkg = derivePackage(html, javaBaseRoot);

        String raw = readTextLenient(html, 2_000_000); // cap at 2MB per file
        ff.bytesRead = raw.length();

        // quick strip to reduce noise but keep visible text
        String text = stripHtmlQuick(raw);

        int hits = 0;

        // Big-O
        Matcher m = BIG_O.matcher(text);
        while (m.find()) {
            ff.bigOCount++;
            ff.bigOs.add(m.group().replaceAll("\\s+", " ").trim());
            hits++;
            if (ff.snippets.size() < 6) ff.snippets.add(snippet(text, m.start(), m.end()));
        }

        // phrases
        String lower = text.toLowerCase(Locale.ROOT);
        for (String p : KEY_PHRASES) {
            int c = countOccurrences(lower, p.toLowerCase(Locale.ROOT));
            if (c > 0) {
                hits += c;
                if (ff.snippets.size() < 6) {
                    int idx = lower.indexOf(p.toLowerCase(Locale.ROOT));
                    if (idx >= 0) ff.snippets.add(snippet(text, idx, Math.min(text.length(), idx + p.length())));
                }
            }
        }

        ff.hits = hits;
        return ff;
    }

    private static String derivePackage(Path html, Path javaBaseRoot) {
        // Example: .../java.base/java/util/ArrayList.html -> java.util
        //          .../java.base/javax/crypto/Cipher.html -> javax.crypto
        Path rel;
        try {
            rel = javaBaseRoot.relativize(html);
        } catch (Exception e) {
            rel = html.getFileName();
        }

        List<String> parts = new ArrayList<>();
        for (Path p : rel) parts.add(p.toString());

        // find "java" or "javax"
        int i = parts.indexOf("java");
        if (i < 0) i = parts.indexOf("javax");
        if (i < 0) return "(unknown)";

        // package parts are directories after java/javax until last element (file)
        String root = parts.get(i);
        List<String> pkgParts = new ArrayList<>();
        pkgParts.add(root);

        for (int k = i + 1; k < parts.size() - 1; k++) {
            String s = parts.get(k);
            if (s.isEmpty()) continue;
            if (s.endsWith(".html")) break;
            pkgParts.add(s);
        }
        return String.join(".", pkgParts);
    }

    private static String readTextLenient(Path file, int maxChars) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            String s = dec.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            if (maxChars < s.length()) return s.substring(0, maxChars);
            return s;
        } catch (IOException e) {
            return "";
        }
    }

    private static String stripHtmlQuick(String html) {
        // Remove script/style blocks, then tags; keep some spacing.
        String s = html;
        s = s.replaceAll("(?is)<script.*?>.*?</script>", " ");
        s = s.replaceAll("(?is)<style.*?>.*?</style>", " ");
        s = s.replaceAll("(?is)<head.*?>.*?</head>", " ");
        s = s.replaceAll("(?is)<[^>]+>", " ");
        s = s.replace("&nbsp;", " ");
        s = s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        s = s.replaceAll("\\n+", "\n");
        return s;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while (true) {
            idx = haystack.indexOf(needle, idx);
            if (idx < 0) return count;
            count++;
            idx += needle.length();
        }
    }

    private static String snippet(String text, int start, int end) {
        int a = Math.max(0, start - 70);
        int b = Math.min(text.length(), end + 70);
        String sn = text.substring(a, b).replaceAll("\\s+", " ").trim();
        if (sn.length() > 170) sn = sn.substring(0, 170) + "…";
        return sn;
    }

    private static void openInBrowser(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {
        }
    }

    // ---- Table model
    static final class FindingsTableModel extends AbstractTableModel {
        private final String[] cols = {"Hits", "Big-O", "Package", "HTML File", "Big-O expressions"};
        private List<FileFinding> data = List.of();

        void setData(List<FileFinding> d) {
            data = d == null ? List.of() : d;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int col) { return cols[col]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            FileFinding f = data.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> f.hits;
                case 1 -> f.bigOCount;
                case 2 -> f.pkg;
                case 3 -> f.file == null ? "" : f.file.toString();
                case 4 -> String.join(", ", f.bigOs);
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0 || columnIndex == 1) return Integer.class;
            return String.class;
        }
    }

    // ---- Heatmap/Treemap panel
    static final class HeatmapPanel extends JPanel {
        private Map<String, PkgSummary> pkgs = Map.of();
        private final List<RectItem> rects = new ArrayList<>();
        private RectItem hover;

        HeatmapPanel() {
            setPreferredSize(new Dimension(1000, 600));
            setBackground(Color.WHITE);
            setToolTipText(" "); // enable tooltips
            ToolTipManager.sharedInstance().setInitialDelay(100);
            ToolTipManager.sharedInstance().setDismissDelay(8000);
            setOpaque(true);
        }

        void setPackages(Map<String, PkgSummary> m) {
            pkgs = (m == null) ? Map.of() : m;
            layoutRects();
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            if (hover == null) return null;
            PkgSummary s = hover.summary;
            StringBuilder sb = new StringBuilder("<html>");
            sb.append("<b>").append(escapeHtml(s.pkg)).append("</b><br/>");
            sb.append("hits: ").append(s.hits).append(" | big-O: ").append(s.bigOCount).append(" | files: ").append(s.fileCount).append("<br/>");
            if (!s.bigOs.isEmpty()) {
                sb.append("Big-O: ").append(escapeHtml(String.join(", ", s.bigOs))).append("<br/>");
            }
            sb.append("density: ").append(String.format(Locale.ROOT, "%.4f", s.density())).append(" hits/char");
            sb.append("</html>");
            return sb.toString();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            layoutRects();

            // Background title
            g2.setColor(new Color(20, 20, 20));
            g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
            g2.drawString("java.base – Performance claims in Javadoc (package heatmap)", 14, 26);

            // If empty
            if (rects.isEmpty()) {
                g2.setFont(getFont().deriveFont(14f));
                g2.setColor(new Color(80, 80, 80));
                g2.drawString("No findings yet. Select a folder and wait for analysis.", 14, 56);
                g2.dispose();
                return;
            }

            // Compute max for color scale
            int maxHits = 1;
            double maxDensity = 0.0;
            for (RectItem it : rects) {
                maxHits = Math.max(maxHits, it.summary.hits);
                maxDensity = Math.max(maxDensity, it.summary.density());
            }
            if (maxDensity <= 0.0) maxDensity = 1e-9;

            int topY = 36;
            Rectangle area = new Rectangle(12, topY + 16, getWidth() - 24, getHeight() - (topY + 28));
            g2.setColor(new Color(240, 240, 240));
            g2.fill(area);

            // Draw rects
            g2.setFont(getFont().deriveFont(12f));
            for (RectItem it : rects) {
                Rectangle r = it.rect;
                PkgSummary s = it.summary;

                float intensity = (float) (s.density() / maxDensity);
                intensity = clamp(intensity, 0.05f, 1.0f);

                // grayscale-ish intensity (no hard-coded palette, just shades)
                int shade = (int) (245 - intensity * 180);
                shade = Math.max(30, Math.min(245, shade));
                Color fill = new Color(shade, shade, shade);

                g2.setColor(fill);
                g2.fillRect(r.x, r.y, r.width, r.height);

                // Border (highlight on hover)
                g2.setColor(it == hover ? new Color(20, 20, 20) : new Color(220, 220, 220));
                g2.drawRect(r.x, r.y, r.width, r.height);

                // Label if enough space
                if (r.width > 140 && r.height > 46) {
                    g2.setColor(shade < 130 ? Color.WHITE : new Color(20, 20, 20));
                    String title = s.pkg + "  (" + s.hits + " hits)";
                    g2.drawString(ellipsize(title, r.width - 10, g2.getFontMetrics()), r.x + 6, r.y + 16);

                    // show Big-O summary
                    if (!s.bigOs.isEmpty()) {
                        String b = String.join(", ", s.bigOs);
                        g2.setFont(getFont().deriveFont(11f));
                        g2.drawString(ellipsize(b, r.width - 10, g2.getFontMetrics()), r.x + 6, r.y + 32);
                        g2.setFont(getFont().deriveFont(12f));
                    }
                }
            }

            // Footer legend
            g2.setColor(new Color(80, 80, 80));
            g2.setFont(getFont().deriveFont(12f));
            g2.drawString("Area ~ hits, color ~ hit density (hits per char). Hover for details.", 14, getHeight() - 10);

            g2.dispose();
        }

        @Override
        public void addNotify() {
            super.addNotify();
            enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
        }

        @Override
        protected void processMouseMotionEvent(MouseEvent e) {
            RectItem h = findRect(e.getPoint());
            if (h != hover) {
                hover = h;
                repaint();
            }
            super.processMouseMotionEvent(e);
        }

        private RectItem findRect(Point p) {
            for (RectItem it : rects) {
                if (it.rect.contains(p)) return it;
            }
            return null;
        }

        private void layoutRects() {
            rects.clear();
            if (pkgs == null || pkgs.isEmpty()) return;

            // Sort packages by hits desc
            List<PkgSummary> list = new ArrayList<>(pkgs.values());
            list.sort((a, b) -> Integer.compare(b.hits, a.hits));

            // Take top N for readability; still reflects map well
            int N = Math.min(70, list.size());
            list = list.subList(0, N);

            int w = Math.max(100, getWidth());
            int h = Math.max(100, getHeight());

            Rectangle area = new Rectangle(12, 52, w - 24, h - 84);

            int total = list.stream().mapToInt(s -> Math.max(1, s.hits)).sum();

            // Simple slice-and-dice treemap (alternating horizontal/vertical splits)
            Rectangle cur = new Rectangle(area);
            boolean horizontal = true;

            int used = 0;
            for (int i = 0; i < list.size(); i++) {
                PkgSummary s = list.get(i);
                int weight = Math.max(1, s.hits);
                double frac = (double) weight / (double) total;
                Rectangle r;

                if (i == list.size() - 1) {
                    r = new Rectangle(cur);
                } else if (horizontal) {
                    int hh = (int) Math.round(area.height * frac);
                    hh = Math.max(18, Math.min(cur.height, hh));
                    r = new Rectangle(cur.x, cur.y, cur.width, hh);
                    cur = new Rectangle(cur.x, cur.y + hh, cur.width, cur.height - hh);
                } else {
                    int ww = (int) Math.round(area.width * frac);
                    ww = Math.max(60, Math.min(cur.width, ww));
                    r = new Rectangle(cur.x, cur.y, ww, cur.height);
                    cur = new Rectangle(cur.x + ww, cur.y, cur.width - ww, cur.height);
                }

                horizontal = !horizontal;
                rects.add(new RectItem(r, s));
                used += weight;
            }
        }

        private static String ellipsize(String s, int maxWidth, FontMetrics fm) {
            if (fm.stringWidth(s) <= maxWidth) return s;
            String ell = "…";
            int w = fm.stringWidth(ell);
            int i = s.length();
            while (0 < i && w + fm.stringWidth(s.substring(0, i)) > maxWidth) i--;
            if (i <= 0) return ell;
            return s.substring(0, i) + ell;
        }

        private static float clamp(float v, float lo, float hi) {
            return Math.max(lo, Math.min(hi, v));
        }

        private static String escapeHtml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        static final class RectItem {
            Rectangle rect;
            PkgSummary summary;
            RectItem(Rectangle r, PkgSummary s) { this.rect = r; this.summary = s; }
        }
    }
}
