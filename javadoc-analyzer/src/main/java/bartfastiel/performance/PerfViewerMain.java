package bartfastiel.performance;

import javax.swing.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class PerfViewerMain {

    static void main(String[] args) throws Exception {

        if (args.length != 1) {
            System.err.println("Usage: java " + PerfViewerMain.class.getName() + " <path-to-javadoc-root>");
            System.exit(1);
        }

        Path root = Paths.get(args[0]);

        var result =
                JavadocPerfScan.analyze(root);

        var tree =
                PerfTreeAdapter.buildTree(root, result);

        int max =
                result.packages().values().stream()
                        .flatMap(p -> p.classes().values().stream())
                        .mapToInt(c -> c.matches().size())
                        .max()
                        .orElse(1);

        SwingUtilities.invokeLater(() -> {

            var view =
                    new JavadocPackageTreeView(tree);

            view.setMaxHits(max);

            JFrame f =
                    new JFrame("OpenJDK Performance Hotspots");

            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setSize(1600, 1000);

            f.setContentPane(new JScrollPane(view));
            f.setVisible(true);
        });
    }
}
