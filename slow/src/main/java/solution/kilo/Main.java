package solution.kilo;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.lang.IO.println;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class Main {

    private static final Path ZIP_FILE = Path.of("personal-data.zip");
    private static final int DAYS = 12 * 31;

    private static final int MONTH_TENS = 26;
    private static final int MONTH_ONES = 27;
    private static final int DAY_TENS = 29;
    private static final int DAY_ONES = 30;

    // Read buffer while inflating
    private static final int IO_BUF_SIZE = 1 << 20; // 1 MiB

    public static void main() throws IOException {
        println(run(ZIP_FILE));
    }

    public static String run(Path zipPath) throws IOException {
        try (var zipFile = new ZipFile(zipPath.toFile());
             var arena = Arena.ofShared()) {

            var entry = zipFile.stream()
                    .filter(e -> !e.isDirectory())
                    .findFirst()
                    .orElseThrow();

            var uncompressedSize = entry.getSize();
            if (uncompressedSize < 0) {
                throw new IOException("ZIP entry size is unknown (-1). Use a Zip that stores size in the central directory.");
            }
            println("Uncompressing " + uncompressedSize + " bytes from ZIP entry " + entry.getName());

            var csv = arena.allocate(uncompressedSize);

            inflateIntoSegment(zipFile, entry, csv);

            var partiesPerDay = countBirthdaysParallel(csv);

            Integer mostCommonBirthday = null;
            var maxParties = 0;
            for (var i = 0; i < partiesPerDay.length; i++) {
                if (partiesPerDay[i] > maxParties) {
                    maxParties = partiesPerDay[i];
                    mostCommonBirthday = i;
                }
            }
            if (mostCommonBirthday == null) {
                return "No birthdays found";
            }
            var month = (mostCommonBirthday / 31) + 1;
            var day = (mostCommonBirthday % 31) + 1;
            return "Most common birthday is " + String.format("%02d-%02d", month, day) +
                    " with " + maxParties +
                    " persons celebrating it.";
        }
    }

    private static void inflateIntoSegment(ZipFile zipFile, ZipEntry entry, MemorySegment target) throws IOException {
        try (var in = zipFile.getInputStream(entry)) {
            var buf = new byte[IO_BUF_SIZE];
            long offset = 0;
            int n;

            while ((n = in.read(buf)) != -1) {
                if (n == 0) {
                    continue;
                }
                if (target.byteSize() < offset + n) {
                    throw new IOException("Inflated data larger than expected (zip entry size mismatch).");
                }

                var src = MemorySegment.ofArray(buf).asSlice(0, n);
                target.asSlice(offset, n).copyFrom(src);

                offset += n;
            }

            if (offset != target.byteSize()) {
                // Not always fatal, but usually indicates ZIP metadata mismatch.
                throw new IOException("Inflated bytes (" + offset + ") != expected size (" + target.byteSize() + ").");
            }
        }
    }

    private static int[] countBirthdaysParallel(MemorySegment csv) {
        var size = csv.byteSize();

        // read first line to determine line length
        var lineLengthCount = 0L;
        for (; lineLengthCount < size; lineLengthCount++) {
            var b = getByte(csv, lineLengthCount);
            if (b == '\n') {
                break;
            }
        }
        final var finalLineLength = lineLengthCount + 1;

        if (size % finalLineLength != 0) {
            throw new IllegalStateException("CSV size " + size + " is not multiple of line length " + finalLineLength);
        }

        var processors = Runtime.getRuntime().availableProcessors();
        var persons = size / finalLineLength - 1; // minus header line
        var chunks = (int) max(1, min(processors, persons));
        var personsPerChunk = persons / chunks;

        return IntStream.range(0, chunks)
                .parallel()
                .mapToObj(chunkIndex -> {
                    var startPerson = chunkIndex * personsPerChunk + 1;
                    long endPerson;
                    if (chunkIndex == chunks - 1) {
                        endPerson = (int) persons;
                    } else {
                        endPerson = (chunkIndex + 1) * personsPerChunk;
                    }
                    return processChunk(csv, startPerson, endPerson, finalLineLength);
                })
                .collect(
                        () -> new int[DAYS],
                        Main::mergeCounts,
                        Main::mergeCounts
                );
    }

    private static int[] processChunk(MemorySegment csv, long startPerson, long endPerson, long lineLength) {
        var partiesPerDay = new int[DAYS];

        for (var personIndex = startPerson; personIndex <= endPerson; personIndex++) {
            var lineOffset = personIndex * lineLength;

            var monthTens = getByte(csv, lineOffset + MONTH_TENS) - '0';
            var monthOnes = getByte(csv, lineOffset + MONTH_ONES) - '0';
            var dayTens = getByte(csv, lineOffset + DAY_TENS) - '0';
            var dayOnes = getByte(csv, lineOffset + DAY_ONES) - '0';

            var month = monthTens * 10 + monthOnes;
            var day = dayTens * 10 + dayOnes;

            var dayIndex = (month - 1) * 31 + (day - 1);
            partiesPerDay[dayIndex]++;
        }

        return partiesPerDay;
    }

    private static byte getByte(MemorySegment seg, long offset) {
        return seg.get(ValueLayout.JAVA_BYTE, offset);
    }

    private static void mergeCounts(int[] a, int[] b) {
        for (var i = 0; i < a.length; i++) {
            a[i] += b[i];
        }
    }
}
