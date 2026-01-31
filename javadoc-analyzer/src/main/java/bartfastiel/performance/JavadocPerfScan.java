import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class JavadocPerfScan {

    // ========= CONFIG =========

    private static final List<byte[]> TOKENS = List.of(
            "O(".getBytes(StandardCharsets.US_ASCII),
            "constant time".getBytes(StandardCharsets.US_ASCII),
            "linear time".getBytes(StandardCharsets.US_ASCII),
            "logarithmic".getBytes(StandardCharsets.US_ASCII),
            "amortized".getBytes(StandardCharsets.US_ASCII),
            "complexity".getBytes(StandardCharsets.US_ASCII)
    );

    private static final String ORACLE_DOC_URL =
            "https://www.oracle.com/java/technologies/javase-jdk25-doc-downloads.html";

    // ========= DATA MODEL =========

    public static final class AnalysisResult {
        public final Map<String, PackageStats> packages = new TreeMap<>();
        public int scannedFiles;
        public int matchedFiles;
    }

    public static final class PackageStats {
        public final String packageName;
        public int hits;
        public final Set<String> bigOs = new TreeSet<>();
        public final Set<String> classes = new TreeSet<>();

        PackageStats(String packageName) {
            this.packageName = packageName;
        }
    }

    // ========= MAIN =========

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            System.err.println("Not a directory: " + root);
            return;
        }

        AnalysisResult result = analyze(root);
        printResult(result);
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  java JavadocPerfScan <path-to-extracted-javadoc>

                Example:
                  java JavadocPerfScan ~/Downloads/jdk-25.0.2_doc-all

                Download JavaDoc:
                  """ + ORACLE_DOC_URL);
    }

    // ========= CORE ANALYSIS =========

    public static AnalysisResult analyze(Path extractedRoot) throws IOException {
        Path javaBase = findJavaBase(extractedRoot);
        if (javaBase == null) {
            throw new IllegalStateException("Could not find java.base under " + extractedRoot);
        }

        AnalysisResult result = new AnalysisResult();
        AtomicInteger scanned = new AtomicInteger();
        AtomicInteger matched = new AtomicInteger();

        try (Stream<Path> files = Files.walk(javaBase)) {
            files
                    .filter(p -> p.toString().endsWith(".html"))
                    .parallel()
                    .forEach(file -> {
                        scanned.incrementAndGet();
                        try {
                            if (!containsAnyToken(file)) {
                                return;
                            }

                            String text = Files.readString(file);
                            FileHit hit = extractHit(file, javaBase, text);
                            if (hit == null) {
                                return;
                            }

                            matched.incrementAndGet();
                            PackageStats stats = result.packages
                                    .computeIfAbsent(hit.pkg, PackageStats::new);

                            synchronized (stats) {
                                stats.hits++;
                                stats.classes.add(hit.clazz);
                                stats.bigOs.addAll(hit.bigOs);
                            }

                        } catch (Exception ignored) {
                        }
                    });
        }

        result.scannedFiles = scanned.get();
        result.matchedFiles = matched.get();
        return result;
    }

    // ========= FAST BYTE SCAN =========

    private static boolean containsAnyToken(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        outer:
        for (int i = 0; i < data.length; i++) {
            for (byte[] token : TOKENS) {
                if (matchesAt(data, i, token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesAt(byte[] data, int pos, byte[] token) {
        if (pos + token.length > data.length) return false;
        for (int i = 0; i < token.length; i++) {
            if (data[pos + i] != token[i]) return false;
        }
        return true;
    }

    // ========= SEMANTIC EXTRACTION =========

    private static final class FileHit {
        String pkg;
        String clazz;
        Set<String> bigOs = new TreeSet<>();
    }

    private static FileHit extractHit(Path file, Path javaBase, String text) {
        String pkg = derivePackage(file, javaBase);
        if (pkg == null) return null;

        FileHit hit = new FileHit();
        hit.pkg = pkg;
        hit.clazz = file.getFileName().toString().replace(".html", "");

        // crude but fast Big-O extraction
        int idx = 0;
        while ((idx = text.indexOf("O(", idx)) >= 0) {
            int end = Math.min(text.length(), idx + 20);
            hit.bigOs.add(text.substring(idx, end).replaceAll("\\s+", " "));
            idx += 2;
        }

        return hit;
    }

    private static String derivePackage(Path file, Path javaBase) {
        Path rel;
        try {
            rel = javaBase.relativize(file);
        } catch (Exception e) {
            return null;
        }

        List<String> parts = new ArrayList<>();
        for (Path p : rel) parts.add(p.toString());

        int i = parts.indexOf("java");
        if (i < 0) i = parts.indexOf("javax");
        if (i < 0) return null;

        return String.join(".", parts.subList(i, parts.size() - 1));
    }

    // ========= PATH DISCOVERY =========

    private static Path findJavaBase(Path root) throws IOException {
        Path direct = root.resolve("docs/api/java.base");
        if (Files.isDirectory(direct)) return direct;

        try (Stream<Path> s = Files.walk(root, 6)) {
            return s
                    .filter(p -> p.getFileName().toString().equals("java.base"))
                    .findFirst()
                    .orElse(null);
        }
    }

    // ========= OUTPUT =========

    private static void printResult(AnalysisResult r) {
        System.out.println();
        System.out.println("Scanned files : " + r.scannedFiles);
        System.out.println("Matched files : " + r.matchedFiles);
        System.out.println();

        r.packages.values().stream()
                .sorted(Comparator.comparingInt((PackageStats p) -> p.hits).reversed())
                .forEach(p -> {
                    System.out.println(p.packageName);
                    System.out.println("  hits    : " + p.hits);
                    if (!p.bigOs.isEmpty()) {
                        System.out.println("  Big-O   : " + String.join(", ", p.bigOs));
                    }
                    System.out.println("  classes : " + p.classes.size());
                    System.out.println();
                });
    }
}
