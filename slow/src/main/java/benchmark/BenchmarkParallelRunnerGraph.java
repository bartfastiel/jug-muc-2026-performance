import benchmark.BenchmarkParallelRunner;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

import static java.lang.IO.println;

void main(String[] args) throws Exception {
    var path = args.length > 0 ? args[0] : "results/measured.csv";
    SwingUtilities.invokeLater(() -> start(path));
    BenchmarkParallelRunner.main();
}

private static void start(String csvPath) {
    var frame = new JFrame("CSV Plot");
    frame.setUndecorated(true);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.setLayout(new BorderLayout());

    var plot = new PlotPanel();
    var controls = new ControlPanel(plot);

    frame.add(controls, BorderLayout.NORTH);
    frame.add(plot, BorderLayout.CENTER);

    frame.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                plot.shutdown();
                frame.dispose();
                System.exit(0);
            }
        }
    });

    var gd = GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .getDefaultScreenDevice();
    gd.setFullScreenWindow(frame);

    plot.startTailer(csvPath);

    new Timer(1000 / 30, e -> plot.repaint()).start();
}

// ========================================================================
// DATA
// ========================================================================

static final class Series {
    double[] xs = new double[4096];
    double[] ys = new double[4096];
    int size;
    boolean hasAny;
    double minX, maxX, minY, maxY;

    void add(double x, double y) {
        if (xs.length == size) {
            var n = xs.length + (xs.length >> 1);
            xs = Arrays.copyOf(xs, n);
            ys = Arrays.copyOf(ys, n);
        }
        xs[size] = x;
        ys[size] = y;
        size++;

        if (!hasAny) {
            minX = maxX = x;
            minY = maxY = y;
            hasAny = true;
        } else {
            if (x < minX) minX = x;
            if (maxX < x) maxX = x;
            if (y < minY) minY = y;
            if (maxY < y) maxY = y;
        }
    }
}

// ========================================================================
// CONTROL PANEL
// ========================================================================

static final class ControlPanel extends JPanel {
    ControlPanel(PlotPanel plot) {
        setBackground(new Color(20, 20, 20));
        setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));

        add(axisControls("Y", plot.yMin, plot.yMax, plot.yAutoMax));
        add(axisControls("X", plot.xMin, plot.xMax, plot.xAutoMax));
    }

    private JPanel axisControls(String label,
                                JTextField min,
                                JTextField max,
                                PlotPanel.JDarkCheckBox auto) {

        var p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setBackground(getBackground());

        p.add(label(label + " Min"));
        p.add(min);
        p.add(label("Max"));
        p.add(max);
        p.add(auto);

        return p;
    }

    private JLabel label(String s) {
        var l = new JLabel(s);
        l.setForeground(new Color(60, 60, 60));
        return l;
    }
}

// ========================================================================
// PLOT PANEL
// ========================================================================

static final class PlotPanel extends JPanel {

    private final Series series = new Series();
    private volatile boolean running;
    private volatile String errorMessage;

    private Thread tailer;
    private int mouseX = -1;

    // Axis controls (public for ControlPanel)
    final JTextField xMin = field("0");
    final JTextField xMax = field("");
    final JDarkCheckBox xAutoMax = autoBox();

    final JTextField yMin = field("0");
    final JTextField yMax = field("");
    final JDarkCheckBox yAutoMax = autoBox();

    private final Insets pad = new Insets(40, 80, 60, 30);
    private final Font font = new Font("SansSerif", Font.PLAIN, 16);

    HashMap<String, Integer> headerIndex;

    private final CSVParser parser =
            new CSVParserBuilder()
                    .withSeparator(',')
                    .withQuoteChar('"')
                    .build();

    PlotPanel() {
        setBackground(Color.BLACK);
        setFont(font);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
            }
        });
    }

    static JTextField field(String s) {
        var f = new JTextField(s, 6);
        f.setForeground(Color.LIGHT_GRAY);
        f.setBackground(new Color(30, 30, 30));
        f.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
        return f;
    }

    static JDarkCheckBox autoBox() {
        // our own custom dark-mode-checkbox (simple outline + eventually a checkmark) - custom because
        // the default JCheckBox looks awful in dark mode
        return new JDarkCheckBox("Auto Max", true);
    }

    static class JDarkCheckBox extends JComponent {
        @Getter
        private boolean selected;
        private final String text;

        JDarkCheckBox(String text, boolean initiallySelected) {
            this.text = text;
            this.selected = initiallySelected;
            setPreferredSize(new Dimension(100, 24));
            addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    selected = !selected;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(new Color(30, 30, 30));
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (selected) {
                g2.setColor(new Color(100, 100, 100));
                g2.drawRect(4, 4, 16, 16);
                g2.drawLine(6, 12, 12, 18);
                g2.drawLine(12, 18, 20, 6);
            } else {
                g2.setColor(Color.WHITE);
                g2.drawRect(4, 4, 16, 16);
            }
            g2.drawString(text, 26, 16);
        }
    }

    void startTailer(String path) {
        running = true;
        tailer = new Thread(() -> tail(path), "csv-tailer");
        tailer.setDaemon(true);
        tailer.start();
    }

    void shutdown() {
        running = false;
        if (tailer != null) tailer.interrupt();
    }

    private void tail(String path) {
        var f = new File(path);
        long pos = 0;
        var header = false;
        var content = false;

        errorMessage = "Warte auf CSV-Datei: " + f.getAbsolutePath();
        while (running) {
            try {
                if (!f.exists()) {
                    sleep(300);
                    continue;
                }
                if (!f.canRead()) {
                    errorMessage = "Keine Leseberechtigung für CSV-Datei";
                    sleep(300);
                    continue;
                }
                if (!content) {
                    errorMessage = "CSV-Datei gefunden, warte auf Daten...";
                } else {
                    errorMessage = null;
                }

                try (var raf = new RandomAccessFile(f, "r")) {
                    if (raf.length() < pos) {
                        pos = 0;
                        header = false;
                    }
                    raf.seek(pos);
                    String line;
                    while ((line = raf.readLine()) != null) {
                        if (!header) {
                            header = true;
                            readHeader(line);
                            errorMessage = "Warte auf Daten in CSV-Datei...";
                            continue;
                        }
                        parse(line);
                        content = true;
                        errorMessage = null;
                    }
                    pos = raf.getFilePointer();
                }
                sleep(80);
            } catch (IOException e) {
                errorMessage = "Fehler beim Lesen der CSV-Datei";
                sleep(300);
            }
        }
    }

    private String[] parseRow(String line) throws IOException {
        return parser.parseLine(line);
    }

    private void readHeader(String line) {
        try {
            var row = parseRow(line);

            headerIndex = new HashMap<>();
            for (int i = 0; i < row.length; i++) {
                headerIndex.put(row[i], i);
            }

        } catch (Exception e) {
            throw new RuntimeException("Header kaputt: " + line, e);
        }
    }

    private void parse(String line) {
        try {
            var row = parseRow(line);
            if (row == null) return;

            var scoreCol = headerIndex.get("Score");
            var paramCol = headerIndex.get("Param: numberOfPersons");
            println("scoreCol: " + scoreCol + ", paramCol: " + paramCol);

            if (scoreCol == null || paramCol == null) return;

            var y = Double.parseDouble(row[scoreCol].replace(',', '.'));
            var x = Double.parseDouble(row[paramCol]);

            synchronized (series) {
                series.add(x, y);
            }

        } catch (Exception e) {
            throw new RuntimeException("Parse-Fehler: " + line, e);
        }
    }


    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    // ====================================================================
    // RENDER
    // ====================================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        var g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);

        int w = getWidth(), h = getHeight();

        if (errorMessage != null) {
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 24));
            var fm = g2.getFontMetrics();
            var tw = fm.stringWidth(errorMessage);
            g2.drawString(errorMessage, (w - tw) / 2, h / 2);
            return;
        }

        int L = pad.left, T = pad.top;
        int R = w - pad.right, B = h - pad.bottom;
        int PW = R - L, PH = B - T;

        g2.setColor(new Color(25, 25, 25));
        g2.fillRect(L, T, PW, PH);

        if (!series.hasAny) return;

        double dataMaxX, dataMaxY;
        synchronized (series) {
            dataMaxX = series.maxX;
            dataMaxY = series.maxY;
        }

        var x0 = parseOr(xMin, 0);
        var y0 = parseOr(yMin, 0);

        var x1 = xAutoMax.isSelected()
                ? dataMaxX * 1.05
                : parseOr(xMax, dataMaxX);

        var y1 = yAutoMax.isSelected()
                ? dataMaxY * 1.05
                : parseOr(yMax, dataMaxY);

        if (!xAutoMax.isSelected() && xMax.getText().isEmpty())
            xMax.setText(format(dataMaxX));
        if (!yAutoMax.isSelected() && yMax.getText().isEmpty())
            yMax.setText(format(dataMaxY));

        if (x1 <= x0) x1 = x0 + 1;
        if (y1 <= y0) y1 = y0 + 1;

        drawGridAndTicks(g2, L, T, B, PW, PH, x0, x1, y0, y1);

        var ps = Math.max(1, (int) Math.round(Math.min(w, h) * 0.005));
        var half = ps / 2;

        g2.setColor(new Color(100, 200, 255, 180));
        synchronized (series) {
            for (var i = 0; i < series.size; i++) {
                var px = L + (int) ((series.xs[i] - x0) / (x1 - x0) * PW);
                var py = B - (int) ((series.ys[i] - y0) / (y1 - y0) * PH);
                g2.fillRect(px - half, py - half, ps, ps);
            }
        }

        drawAxesLabels(g2, L, T, B, PW, PH);
        drawHover(g2, L, B, PW, x0, x1);
    }

    // ====================================================================

    private void drawGridAndTicks(Graphics2D g2, int L, int T, int B,
                                  int PW, int PH,
                                  double x0, double x1,
                                  double y0, double y1) {

        var xStep = niceStepPx(x1 - x0, PW, 120);
        var yStep = niceStepPx(y1 - y0, PH, 100);

        g2.setColor(new Color(60, 60, 60));

        for (var x = ceil(x0, xStep); x <= x1; x += xStep) {
            var px = L + (int) ((x - x0) / (x1 - x0) * PW);
            g2.drawLine(px, T, px, B);
        }
        for (var y = ceil(y0, yStep); y <= y1; y += yStep) {
            var py = B - (int) ((y - y0) / (y1 - y0) * PH);
            g2.drawLine(L, py, L + PW, py);
        }

        var fm = g2.getFontMetrics();
        g2.setColor(Color.LIGHT_GRAY);

        for (var x = ceil(x0, xStep); x <= x1; x += xStep) {
            var px = L + (int) ((x - x0) / (x1 - x0) * PW);
            var s = format(x);
            g2.drawString(s, px - fm.stringWidth(s) / 2, B + 22);
        }

        for (var y = ceil(y0, yStep); y <= y1; y += yStep) {
            var py = B - (int) ((y - y0) / (y1 - y0) * PH);
            var s = format(y);
            g2.drawString(s, L - 8 - fm.stringWidth(s), py + fm.getAscent() / 2);
        }

        g2.setColor(Color.WHITE);
        g2.drawRect(L, T, PW, PH);
    }

    private void drawAxesLabels(Graphics2D g2, int L, int T, int B,
                                int PW, int PH) {
        g2.setColor(Color.WHITE);
        g2.drawString("Anzahl Personen", L + PW / 2 - 80, B + 42);

        var old = g2.getTransform();
        g2.rotate(-Math.PI / 2);
        g2.drawString("Laufzeit [ms]", -T - PH / 2 - 60, L - 50);
        g2.setTransform(old);
    }

    private void drawHover(Graphics2D g2, int L, int B, int PW,
                           double x0, double x1) {
        if (mouseX < L || mouseX > L + PW) return;

        var xVal = x0 + (mouseX - L) / (double) PW * (x1 - x0);

        var sb = new StringBuilder("X ≈ ").append(format(xVal)).append('\n');
        var count = 0;

        synchronized (series) {
            for (var i = 0; i < series.size; i++) {
                if (Math.abs(series.xs[i] - xVal) < (x1 - x0) * 0.002) {
                    sb.append(format(series.ys[i])).append(" ms\n");
                    count++;
                }
            }
        }
        if (count == 0) return;

        var lines = sb.toString().split("\n");
        var fm = g2.getFontMetrics();
        var w = 0;
        for (var s : lines) w = Math.max(w, fm.stringWidth(s));
        var h = fm.getHeight() * lines.length + 6;

        var x = getWidth() - w - 30;
        var y = 40;

        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRect(x - 6, y - 6, w + 12, h);
        g2.setColor(Color.WHITE);

        for (var i = 0; i < lines.length; i++)
            g2.drawString(lines[i], x, y + (i + 1) * fm.getHeight());
    }

    // ====================================================================

    private static double parseOr(JTextField f, double def) {
        try {
            if (f.getText().isEmpty()) return def;
            return Double.parseDouble(f.getText().replace(',', '.'));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double niceStepPx(double range, int px, int minPx) {
        return niceStep(range, Math.max(1, px / minPx));
    }

    private static double niceStep(double range, int ticks) {
        var r = range / ticks;
        var p = Math.pow(10, Math.floor(Math.log10(r)));
        var n = r / p;
        if (n < 1.5) return p;
        if (n < 3) return 2 * p;
        if (n < 7) return 5 * p;
        return 10 * p;
    }

    private static double ceil(double v, double step) {
        return Math.ceil(v / step) * step;
    }

    private static String format(double v) {
        if (v >= 1000) return String.format("%.0f", v);
        if (v >= 100) return String.format("%.0f", v);
        if (v >= 10) return String.format("%.0f", v);
        return String.format("%.2f", v);
    }
}
