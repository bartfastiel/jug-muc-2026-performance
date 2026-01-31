package bartfastiel.performance;

import java.io.IOException;
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

    // =====================================================================
    // CONFIG
    // =====================================================================

    private static final String ORACLE_DOC_URL =
            "https://www.oracle.com/java/technologies/javase-jdk25-doc-downloads.html";

    /**
     * Define what you scan here: name + compiled Pattern + probes for fast pre-scan.
     *
     * Why probes?
     * - We avoid decoding + regex for most files.
     * - Probes should be small, highly selective substrings.
     */
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

    // =====================================================================
    // RECORDS: MODEL
    // =====================================================================

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

    /**
     * PatternDef:
     * - pattern is already compiled at call site (flags per pattern).
     * - probes are literal snippets for a fast byte pre-scan.
     * - probeCaseInsensitive controls ASCII-fold matching for probes.
     */
    public record PatternDef(
            String name,
            Pattern pattern,
            List<String> probes,
            boolean probeCaseInsensitive
    ) {}

    // =====================================================================
    // MAIN
    // =====================================================================

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        Path root;
        root = Paths.get(args[0]);

        if (!isDirectory(root)) {
            err.println("Not a directory: " + root);
            return;
        }

        AnalysisResult result;
        result = analyze(root);

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

    // =====================================================================
    // CORE API (reuse later for Graphics2D)
    // =====================================================================

    public static AnalysisResult analyze(Path extractedJavadocRoot) throws IOException {
        Path javaBase;
        javaBase = findJavaBase(extractedJavadocRoot);
        if (javaBase == null) {
            throw new IllegalStateException("Could not find java.base under " + extractedJavadocRoot);
        }

        Map<String, PackageResult> packages;
        packages = new TreeMap<>();

        AtomicInteger scanned;
        scanned = new AtomicInteger(0);

        AtomicInteger matchedFiles;
        matchedFiles = new AtomicInteger(0);

        try (Stream<Path> files = walk(javaBase)) {
            files
                    .filter(JavadocPerfScan::isRealClassDoc)
                    .parallel()
                    .forEach(file -> {
                        scanned.incrementAndGet();
                        try {
                            if (!mightContainAnything(file)) {
                                return;
                            }

                            String text;
                            text = readString(file);

                            boolean anyMatch;
                            anyMatch = analyzeFileIntoResult(file, javaBase, text, packages);
                            if (anyMatch) {
                                matchedFiles.incrementAndGet();
                            }
                        } catch (Exception ignored) {
                            // robust scanning: ignore individual file failures
                        }
                    });
        }

        return new AnalysisResult(packages, scanned.get(), matchedFiles.get());
    }

    // =====================================================================
    // FILE ANALYSIS
    // =====================================================================

    private static boolean analyzeFileIntoResult(
            Path file,
            Path javaBase,
            String text,
            Map<String, PackageResult> packages
    ) {
        String pkg;
        pkg = derivePackage(file, javaBase);
        if (pkg == null) return false;

        String className;
        className = stripHtmlSuffix(file.getFileName().toString());

        boolean matchedAny;
        matchedAny = false;

        for (PatternDef def : PATTERNS) {
            Matcher m;
            m = def.pattern().matcher(text);

            while (m.find()) {
                matchedAny = true;

                String ex;
                ex = excerpt(text, m.start(), m.end(), 20);

                PackageResult pr;
                pr = packages.computeIfAbsent(pkg, k -> new PackageResult(k, new TreeMap<>()));

                ClassResult cr;
                cr = pr.classes().computeIfAbsent(className, k -> new ClassResult(k, new ArrayList<>()));

                cr.matches().add(new MatchResult(def.name(), ex));
            }
        }

        return matchedAny;
    }

    // =====================================================================
    // FAST PRE-SCAN (PROBES, NO REGEX, NO DECODE)
    // =====================================================================

    private static boolean mightContainAnything(Path file) throws IOException {
        byte[] data;
        data = readAllBytes(file);

        for (PatternDef def : PATTERNS) {
            for (String probe : def.probes()) {
                byte[] p;
                p = probe.getBytes(US_ASCII);

                int idx;
                idx = def.probeCaseInsensitive()
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
            boolean ok;
            ok = true;

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

    /**
     * Finds needleLower in data, comparing ASCII-case-insensitively by folding data bytes to lower.
     * needleLower must already be ASCII-lowercased.
     */
    private static int indexOfAsciiFoldLower(byte[] data, byte[] needleLower) {
        if (needleLower.length == 0) return 0;

        for (int i = 0; i + needleLower.length <= data.length; i++) {
            boolean ok;
            ok = true;

            for (int j = 0; j < needleLower.length; j++) {
                byte b;
                b = data[i + j];

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
        byte[] outBytes;
        outBytes = new byte[in.length];

        for (int i = 0; i < in.length; i++) {
            outBytes[i] = toAsciiLower(in[i]);
        }
        return outBytes;
    }

    private static byte toAsciiLower(byte b) {
        if ('A' <= b && b <= 'Z') return (byte) (b + 32);
        return b;
    }

    // =====================================================================
    // FILTERING & PATH HANDLING
    // =====================================================================

    private static boolean isRealClassDoc(Path file) {
        String fn;
        fn = file.getFileName().toString();

        if (!fn.endsWith(".html")) return false;
        if ("package-summary.html".equals(fn)) return false;
        if ("module-summary.html".equals(fn)) return false;

        // exclude javadoc helper folders
        if (containsPathSegment(file, "class-use")) return false;
        if (containsPathSegment(file, "doc-files")) return false;

        // many index pages are not class docs; keep only "ClassName.html"
        // heuristic: class docs usually start with uppercase letter
        String base;
        base = stripHtmlSuffix(fn);
        if (base.isEmpty()) return false;

        char c;
        c = base.charAt(0);
        if (!Character.isUpperCase(c)) return false;

        return true;
    }

    private static boolean containsPathSegment(Path path, String segment) {
        for (Path p : path) {
            if (segment.equals(p.toString())) return true;
        }
        return false;
    }

    private static String derivePackage(Path file, Path javaBase) {
        Path rel;
        try {
            rel = javaBase.relativize(file);
        } catch (Exception e) {
            return null;
        }

        List<String> parts;
        parts = new ArrayList<>();

        for (Path p : rel) parts.add(p.toString());

        int i;
        i = parts.indexOf("java");
        if (i < 0) i = parts.indexOf("javax");
        if (i < 0) return null;

        if (parts.size() - 1 <= i) return null;
        return String.join(".", parts.subList(i, parts.size() - 1));
    }

    private static Path findJavaBase(Path root) throws IOException {
        Path direct;
        direct = root.resolve("docs").resolve("api").resolve("java.base");
        if (isDirectory(direct)) return direct;

        try (Stream<Path> s = walk(root, 6)) {
            Optional<Path> found;
            found = s
                    .filter(p -> "java.base".equals(p.getFileName().toString()))
                    .findFirst();
            return found.orElse(null);
        }
    }

    private static String stripHtmlSuffix(String s) {
        if (s.endsWith(".html")) return s.substring(0, s.length() - 5);
        return s;
    }

    // =====================================================================
    // TEXT UTIL
    // =====================================================================

    private static String excerpt(String text, int start, int end, int radius) {
        int a;
        a = Math.max(0, start - radius);

        int b;
        b = Math.min(text.length(), end + radius);

        String slice;
        slice = text.substring(a, b);

        // keep it readable in console
        return slice.replaceAll("\\s+", " ").trim();
    }

    // =====================================================================
    // OUTPUT
    // =====================================================================

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
