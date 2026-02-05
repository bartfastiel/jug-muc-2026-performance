package bartfastiel.performance;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.System.err;
import static java.lang.System.out;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.walk;

public final class JavadocPerfScan {

    private static final String ORACLE_DOC_URL =
            "https://www.oracle.com/java/technologies/javase-jdk25-doc-downloads.html";

    private static final List<PatternDef> PATTERNS = List.of(
            new PatternDef(
                    "Big-O",
                    Pattern.compile("(?<![\\p{Alnum}_])O\\s*\\(\\s*[^)\\n]{1,80}\\)"),
                    List.of("O("),
                    false
            ),
            new PatternDef(
                    "Constant time",
                    Pattern.compile("(?<![\\p{Alnum}_])constant[-\\s]+time(?![\\p{Alnum}_])", Pattern.CASE_INSENSITIVE),
                    List.of("constant"),
                    true
            ),
            new PatternDef(
                    "Linear time",
                    Pattern.compile("(?<![\\p{Alnum}_])linear[-\\s]+time(?![\\p{Alnum}_])", Pattern.CASE_INSENSITIVE),
                    List.of("linear"),
                    true
            ),
            new PatternDef(
                    "Logarithmic",
                    Pattern.compile("(?<![\\p{Alnum}_])logarithmic(?![\\p{Alnum}_])", Pattern.CASE_INSENSITIVE),
                    List.of("logarithmic"),
                    true
            ),
            new PatternDef(
                    "Amortized",
                    Pattern.compile("(?<![\\p{Alnum}_])amortized(?![\\p{Alnum}_])", Pattern.CASE_INSENSITIVE),
                    List.of("amortized"),
                    true
            ),
            new PatternDef(
                    "Time/Space complexity",
                    Pattern.compile("(?<![\\p{Alnum}_])(time|space)[-\\s]+complexity(?![\\p{Alnum}_])", Pattern.CASE_INSENSITIVE),
                    List.of("complexity", "time complexity", "space complexity"),
                    true
            )
    );

    public record AnalysisResult(
            Map<String, PackageResult> packages,
            int scannedFiles,
            int matchedFiles
    ) {}

    public record PackageResult(
            String name,
            Map<String, ClassResult> classes
    ) {}

    public record ClassResult(
            String className,
            List<MatchResult> matches
    ) {}

    public record MatchResult(
            String patternName,
            String excerpt
    ) {}

    public record PatternDef(
            String name,
            Pattern pattern,
            List<String> probes,
            boolean probeCaseInsensitive
    ) {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        Path root = Paths.get(args[0]);

        if (!isDirectory(root)) {
            err.println("Not a directory: " + root);
            return;
        }

        AnalysisResult result = analyze(root);

        printResult(result);
    }

    private static void printUsage() {
        out.println("""
                Usage:
                  java JavadocPerfScan <path-to-extracted-javadoc>

                Example:
                  java JavadocPerfScan ~/Downloads/jdk-25.0.2_doc-all

                Download JavaDoc:
                  """ + ORACLE_DOC_URL);
    }

    public static AnalysisResult analyze(Path extractedJavadocRoot) throws IOException {

        Map<String, PackageResult> packages = new TreeMap<>();

        AtomicInteger scanned = new AtomicInteger(0);
        AtomicInteger matchedFiles = new AtomicInteger(0);

        try (Stream<Path> files = walk(extractedJavadocRoot)) {
            files
                    .filter(JavadocPerfScan::isRealClassDoc)
                    .parallel()
                    .forEach(file -> {
                        scanned.incrementAndGet();
                        try {
                            if (!mightContainAnything(file)) {
                                return;
                            }

                            String text = readString(file);

                            boolean anyMatch =
                                    analyzeFileIntoResult(file, extractedJavadocRoot, text, packages);

                            if (anyMatch) {
                                matchedFiles.incrementAndGet();
                            }

                        } catch (Exception ignored) {
                        }
                    });
        }

        return new AnalysisResult(packages, scanned.get(), matchedFiles.get());
    }

    private static boolean analyzeFileIntoResult(
            Path file,
            Path javaBase,
            String text,
            Map<String, PackageResult> packages
    ) {
        String pkg = derivePackage(file, javaBase);
        if (pkg == null) return false;

        String className = stripHtmlSuffix(file.getFileName().toString());

        boolean matchedAny = false;

        for (PatternDef def : PATTERNS) {
            Matcher m = def.pattern().matcher(text);

            while (m.find()) {
                matchedAny = true;

                String ex = excerpt(text, m.start(), m.end(), 20);

                PackageResult pr =
                        packages.computeIfAbsent(pkg,
                                k -> new PackageResult(k, new TreeMap<>()));

                ClassResult cr =
                        pr.classes().computeIfAbsent(className,
                                k -> new ClassResult(k, new ArrayList<>()));

                cr.matches().add(new MatchResult(def.name(), ex));
            }
        }

        return matchedAny;
    }

    private static boolean mightContainAnything(Path file) throws IOException {

        byte[] data = readAllBytes(file);

        for (PatternDef def : PATTERNS) {
            for (String probe : def.probes()) {

                byte[] p = probe.getBytes(US_ASCII);

                int idx =
                        def.probeCaseInsensitive()
                                ? indexOfAsciiFoldLower(data, toAsciiLower(p))
                                : indexOfExact(data, p);

                if (0 <= idx) return true;
            }
        }

        return false;
    }

    private static int indexOfExact(byte[] data, byte[] needle) {

        if (needle.length == 0) return 0;

        for (int i = 0; i + needle.length <= data.length; i++) {

            boolean ok = true;

            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    ok = false;
                    break;
                }
            }

            if (ok) return i;
        }

        return -1;
    }

    private static int indexOfAsciiFoldLower(byte[] data, byte[] needleLower) {

        if (needleLower.length == 0) return 0;

        for (int i = 0; i + needleLower.length <= data.length; i++) {

            boolean ok = true;

            for (int j = 0; j < needleLower.length; j++) {

                byte b = data[i + j];

                if (toAsciiLower(b) != needleLower[j]) {
                    ok = false;
                    break;
                }
            }

            if (ok) return i;
        }

        return -1;
    }

    private static byte[] toAsciiLower(byte[] in) {

        byte[] outBytes = new byte[in.length];

        for (int i = 0; i < in.length; i++) {
            outBytes[i] = toAsciiLower(in[i]);
        }

        return outBytes;
    }

    private static byte toAsciiLower(byte b) {
        if ('A' <= b && b <= 'Z') return (byte) (b + 32);
        return b;
    }

    public static boolean isRealClassDoc(Path file) {

        String fn = file.getFileName().toString();

        if (!fn.endsWith(".html")) return false;
        if ("package-summary.html".equals(fn)) return false;
        if ("module-summary.html".equals(fn)) return false;

        if (containsPathSegment(file, "class-use")) return false;
        if (containsPathSegment(file, "doc-files")) return false;

        String base = stripHtmlSuffix(fn);
        if (base.isEmpty()) return false;

        char c = base.charAt(0);
        if (!Character.isUpperCase(c)) return false;

        return true;
    }

    private static boolean containsPathSegment(Path path, String segment) {
        for (Path p : path) {
            if (segment.equals(p.toString())) return true;
        }
        return false;
    }

    private static String derivePackage(Path file, Path root) {

        Path rel;

        try {
            rel = root.relativize(file);
        } catch (Exception e) {
            return null;
        }

        if (rel.getNameCount() < 2) return null;

        Path parent = rel.getParent();
        if (parent == null) return null;

        return parent.toString()
                .replace(FileSystems.getDefault().getSeparator(), ".");
    }

    private static String stripHtmlSuffix(String s) {
        return s.endsWith(".html")
                ? s.substring(0, s.length() - 5)
                : s;
    }

    private static String excerpt(String text, int start, int end, int radius) {

        int a = Math.max(0, start - radius);
        int b = Math.min(text.length(), end + radius);

        String slice = text.substring(a, b);

        return slice.replaceAll("\\s+", " ").trim();
    }

    private static void printResult(AnalysisResult r) {

        out.println();
        out.println("Scanned files : " + r.scannedFiles());
        out.println("Matched files : " + r.matchedFiles());
        out.println();

        for (PackageResult p : r.packages().values()) {

            out.println(p.name());

            for (ClassResult c : p.classes().values()) {

                out.println("  " + c.className());

                for (MatchResult m : c.matches()) {
                    out.println("    [" + m.patternName() + "] " + m.excerpt());
                }
            }

            out.println();
        }
    }
}
