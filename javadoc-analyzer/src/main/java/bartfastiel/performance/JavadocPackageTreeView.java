package bartfastiel.performance;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

public final class JavadocPackageTreeView extends JComponent {

  public enum NodeKind { PACKAGE, TYPE }

  public static final class Node {

    public final String name;
    public final String fullName;
    public final NodeKind kind;

    public final List<Node> children = new ArrayList<>();
    public List<String> excerpts = new ArrayList<>();

    public int hitsLocal;
    public int hitsAgg;

    int subtreeSlots;

    double x, y;
    public List<Hotspot> topHotspots = new ArrayList<>();

    public record Hotspot(String name, int hits) {}

    public Node(String name, String fullName, NodeKind kind) {
      this.name = name;
      this.fullName = fullName;
      this.kind = kind;
    }
  }

  private Node root;
  private boolean layoutDirty = true;

  private List<Node> nodes = List.of();
  private List<Edge> edges = List.of();

  private int maxHits = 1;
  private Node hoveredNode;

  private record Edge(Node a, Node b) {}

  public JavadocPackageTreeView(Node root) {

    this.root = root;

    setOpaque(true);
    setBackground(new Color(0, 0, 0));

    ToolTipManager.sharedInstance().registerComponent(this);
    ToolTipManager.sharedInstance().setInitialDelay(50);
    ToolTipManager.sharedInstance().setReshowDelay(0);
    ToolTipManager.sharedInstance().setDismissDelay(10_000);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (hoveredNode == null) return;

        openInApiDir(hoveredNode);
      }
    });

    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {

        Node hit = findNodeAt(e.getX(), e.getY());

        if (hit == null) {
          setToolTipText(null);
          hoveredNode = null;
          return;
        }

        hoveredNode = hit;

        // Treffer bestimmen: Package = aggregated, Type = local
        int hits =
                hit.kind == NodeKind.PACKAGE
                        ? hit.hitsAgg
                        : hit.hitsLocal;

        // Treffer-Text nur wenn > 0
        String hitsText =
                hits == 0
                        ? ""
                        : "<div style='margin-top:6px;font-size:15px;color:#ff6666;'>"
                        + hits + " Treffer"
                        + "</div>";

        // Excerpts nur bei Klassen
        String excerptHtml =
                (hit.kind == NodeKind.TYPE && !hit.excerpts.isEmpty())
                        ? "<br><div style='font-family:monospace;font-size:12px;color:#ccc;'>"
                        + String.join("<br>", hit.excerpts)
                        + "</div>"
                        : "";

        // Hotspot-Liste nur bei Packages
        String hotspotHtml = "";

        if (hit.kind == NodeKind.PACKAGE && !hit.topHotspots.isEmpty()) {

          StringBuilder sb = new StringBuilder();

          sb.append("<br><div style='font-size:13px;color:#ddd;'>");

          for (var hs : hit.topHotspots) {
            sb.append("&nbsp;&nbsp;")
                    .append(hs.name())
                    .append(" — ")
                    .append(hs.hits())
                    .append(" Treffer<br>");
          }

          sb.append("</div>");

          hotspotHtml = sb.toString();
        }

        setToolTipText("""
<html>
<div style="
    background:#111;
    color:#eee;
    padding:12px;
    border:1px solid #444;
    font-family:sans-serif;
    min-width:280px;
">

  <div style="font-size:22px;font-weight:bold;">
    %s
  </div>

  <div style="font-size:12px;color:#aaa;">
    %s
  </div>

  %s
  %s
  %s

</div>
</html>
""".formatted(
                hit.fullName,
                hit.kind == NodeKind.PACKAGE ? "Package" : "Klasse",
                hitsText,
                hotspotHtml,
                excerptHtml
        ));
      }

    });

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        layoutDirty = true;
        repaint();
      }
    });
  }

  public void setMaxHits(int maxHits) {
    this.maxHits = Math.max(1, maxHits);
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {

    super.paintComponent(g);

    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setColor(getBackground());
    g2.fillRect(0, 0, getWidth(), getHeight());

    if (layoutDirty) {
      rebuildLayout();
      layoutDirty = false;
    }

    // edges
    g2.setStroke(new BasicStroke(
            12.8f,
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND
    ));

    for (Edge e : edges) {

      Color c = mixGrayToRed(intensity(e.b));
      g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 200));

      g2.draw(curve(e.a, e.b));
    }

    // nodes
    for (Node n : nodes) {

      float t = intensity(n);

      if (0.2f < t) {
        g2.setStroke(new BasicStroke(12.0f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
      } else {
        g2.setStroke(new BasicStroke(4.0f));
      }
      Color c = mixGrayToRed(t);

      double r = (n.kind == NodeKind.TYPE) ? 7 : 6;

      var dot =
              new Ellipse2D.Double(
                      n.x - r,
                      n.y - r,
                      2 * r,
                      2 * r
              );

      if (n.kind == NodeKind.TYPE) {
        g2.setColor(c);
        g2.fill(dot);
      } else {
        g2.setColor(c);
        g2.draw(dot);
      }
    }

    g2.dispose();
  }

  // ============================================================
  // Layout
  // ============================================================

  private void rebuildLayout() {

    ArrayList<Node> ns = new ArrayList<>();
    ArrayList<Edge> es = new ArrayList<>();

    collect(root, ns, es);

    computeSubtreeSlots(root);

    int w = getWidth();
    int h = getHeight();

    int maxDepth = computeMaxDepth(root, 0);

    double levelStep =
            (h - 60.0) / Math.max(1, maxDepth);

    layoutTopDown(
            root,
            w / 2.0,
            30,
            w,
            levelStep
    );

    this.nodes = ns;
    this.edges = es;
  }

  private void collect(Node n, List<Node> ns, List<Edge> es) {

    ns.add(n);

    for (Node c : n.children) {
      es.add(new Edge(n, c));
      collect(c, ns, es);
    }
  }

  private int computeSubtreeSlots(Node n) {

    if (n.children.isEmpty()) {
      n.subtreeSlots = 1;
      return 1;
    }

    int sum = 0;

    for (Node c : n.children) {
      sum += computeSubtreeSlots(c);
    }

    n.subtreeSlots = sum;
    return sum;
  }

  private int computeMaxDepth(Node n, int depth) {

    int max = depth;

    for (Node c : n.children) {
      max = Math.max(max, computeMaxDepth(c, depth + 1));
    }

    return max;
  }

  private void layoutTopDown(
          Node n,
          double centerX,
          double y,
          double width,
          double levelStep
  ) {

    n.x = centerX;
    n.y = y;

    if (n.children.isEmpty()) return;

    int totalSlots = 0;
    for (Node c : n.children) {
      totalSlots += c.subtreeSlots;
    }

    double startX = centerX - width / 2.0;
    double curX = startX;

    for (Node c : n.children) {

      double frac =
              c.subtreeSlots / (double) totalSlots;

      double childWidth =
              width * frac;

      double childCenter =
              curX + childWidth / 2.0;

      layoutTopDown(
              c,
              childCenter,
              y + levelStep,
              childWidth,
              levelStep
      );

      curX += childWidth;
    }
  }

  // ============================================================
  // Curves + Color
  // ============================================================

  private Shape curve(Node a, Node b) {

    double dy = b.y - a.y;

    return new CubicCurve2D.Double(
            a.x, a.y,
            a.x, a.y + dy * 0.4,
            b.x, a.y + dy * 0.7,
            b.x, b.y
    );
  }

  private float intensity(Node n) {
    return Math.min(1.0f, n.hitsAgg / (float) maxHits);
  }

  private static Color mixGrayToRed(float t) {

    t = Math.max(0, Math.min(1, t));

    float base = 1f;

    float r = base + (1 - base) * t;
    float g = base * (1 - t * 0.85f);
    float b = base * (1 - t * 0.85f);

    return new Color(r, g, b);
  }

  // ============================================================
  // Hover Hit-Test
  // ============================================================

  private Node findNodeAt(int mx, int my) {

    Node best = null;
    double bestDist = Double.MAX_VALUE;

    for (Node n : nodes) {

      double r = (n.kind == NodeKind.TYPE) ? 12 : 8;

      double dx = mx - n.x;
      double dy = my - n.y;

      double dist2 = dx * dx + dy * dy;

      if (dist2 < r * r && dist2 < bestDist) {
        bestDist = dist2;
        best = n;
      }
    }

    return best;
  }

  private void openInApiDir(Node n) {

    if (n.kind != NodeKind.TYPE) return;

    String full = n.fullName;

    int lastDot = full.lastIndexOf('.');
    if (lastDot < 0) return;

    String pkg = full.substring(0, lastDot);
    String cls = full.substring(lastDot + 1);

    String url =
            "https://apidia.net/java/OpenJDK/25/"
                    + "?pck=java.base-all-classes"
                    + "&cls=" + pkg + "." + cls;

    try {
      Desktop.getDesktop().browse(new java.net.URI(url));
    } catch (Exception ignored) {
    }
  }

}
