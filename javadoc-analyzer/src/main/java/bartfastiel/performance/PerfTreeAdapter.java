package bartfastiel.performance;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public final class PerfTreeAdapter {

    private PerfTreeAdapter() {}

    private record HitInfo(
            int hits,
            List<String> excerpts
    ) {}

    public static JavadocPackageTreeView.Node buildTree(
            Path javadocRoot,
            JavadocPerfScan.AnalysisResult result
    ) throws IOException {

        var root =
                new JavadocPackageTreeView.Node(
                        "(root)",
                        "(root)",
                        JavadocPackageTreeView.NodeKind.PACKAGE
                );

        // 1) full class tree
        try (Stream<Path> files = Files.walk(javadocRoot)) {

            files.filter(JavadocPerfScan::isRealClassDoc)
                    .forEach(file -> {

                        String pkg =
                                derivePackage(file, javadocRoot);

                        if (pkg == null) return;

                        String cls =
                                stripHtml(file.getFileName().toString());

                        var pkgNode =
                                ensurePackagePath(root, pkg);

                        var typeNode =
                                new JavadocPackageTreeView.Node(
                                        cls,
                                        pkg + "." + cls,
                                        JavadocPackageTreeView.NodeKind.TYPE
                                );

                        pkgNode.children.add(typeNode);
                    });
        }

        // 2) hit map
        Map<String, HitInfo> hitMap = new HashMap<>();

        for (var pkg : result.packages().values()) {
            for (var cls : pkg.classes().values()) {

                int hits;
                hits = cls.matches().size();

                List<String> excerpts;
                excerpts = cls.matches().stream()
                        .limit(3)
                        .map(JavadocPerfScan.MatchResult::excerpt)
                        .toList();

                hitMap.put(
                        pkg.name() + "." + cls.className(),
                        new HitInfo(hits, excerpts)
                );
            }
        }

        // 3) assign hits
        assignHits(root, hitMap);

        // 4) aggregate
        aggregateHits(root);

        return root;
    }

    private static void assignHits(
            JavadocPackageTreeView.Node n,
            Map<String, HitInfo> hitMap
    ) {

        if (n.kind == JavadocPackageTreeView.NodeKind.TYPE) {
            HitInfo info;
            info = hitMap.get(n.fullName);

            if (info != null) {
                n.hitsLocal = info.hits;
                n.excerpts.addAll(info.excerpts);
            }
        }

        for (var c : n.children) {
            assignHits(c, hitMap);
        }
    }

    private static int aggregateHits(JavadocPackageTreeView.Node n) {

        int sum = n.hitsLocal;

        for (var c : n.children) {
            sum += aggregateHits(c);
        }

        n.hitsAgg = sum;
        return sum;
    }

    private static JavadocPackageTreeView.Node ensurePackagePath(
            JavadocPackageTreeView.Node root,
            String pkg
    ) {

        var cur = root;

        String[] parts = pkg.split("\\.");
        StringBuilder sb = new StringBuilder();

        for (String part : parts) {

            if (!sb.isEmpty()) sb.append(".");
            sb.append(part);

            String full = sb.toString();

            JavadocPackageTreeView.Node next = null;

            for (var ch : cur.children) {
                if (ch.kind == JavadocPackageTreeView.NodeKind.PACKAGE
                        && ch.fullName.equals(full)) {
                    next = ch;
                    break;
                }
            }

            if (next == null) {
                next =
                        new JavadocPackageTreeView.Node(
                                part,
                                full,
                                JavadocPackageTreeView.NodeKind.PACKAGE
                        );
                cur.children.add(next);
            }

            cur = next;
        }

        return cur;
    }

    private static String stripHtml(String fn) {
        return fn.endsWith(".html")
                ? fn.substring(0, fn.length() - 5)
                : fn;
    }

    private static String derivePackage(Path file, Path root) {

        Path rel;

        try {
            rel = root.relativize(file);
        } catch (Exception e) {
            return null;
        }

        Path parent = rel.getParent();
        if (parent == null) return null;

        return parent.toString()
                .replace(FileSystems.getDefault().getSeparator(), ".");
    }
}
