package solution.kilo;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.lang.IO.println;

public class OnlyUnzip {

    private static final Path ZIP_FILE = Path.of("personal-data.zip");

    private static final int IO_BUF_SIZE = 1 << 20;

    static void main() throws IOException {
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

            return "done";
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
}
