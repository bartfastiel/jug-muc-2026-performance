package solution.lima;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static java.lang.IO.println;
import static java.lang.Math.min;

public class Main {

    private static final Path ZIP_FILE = Path.of("personal-data.zip");

    // Offsets within each CSV line for "YYYY-MM-DD"
    private static final int MONTH_TENS = 26;
    private static final int MONTH_ONES = 27;
    private static final int DAY_TENS   = 29;
    private static final int DAY_ONES   = 30;

    private static final int DAYS = 12 * 31;

    // ZIP signatures
    private static final int SIG_EOCD       = 0x06054b50;
    private static final int SIG_CEN        = 0x02014b50;
    private static final int SIG_LOCAL      = 0x04034b50;
    private static final int SIG_ZIP64_LOC  = 0x07064b50;
    private static final int SIG_ZIP64_EOCD = 0x06064b50;

    // Windows mapping/index limits => keep each map <= 1 GiB
    private static final long MAP_CHUNK = 1L << 30; // 1 GiB

    // Decompressed streaming buffer
    private static final int OUT_BUF = 32 << 20; // 32 MiB

    // EOCD: 22 bytes min + 65535 max comment
    private static final int EOCD_TAIL = 22 + 65535 + 1024; // little extra

    public static void main(String[] args) throws Exception {
        println(run(ZIP_FILE));
    }

    public static String run(Path zipPath) throws Exception {
        try (var ch = FileChannel.open(zipPath, StandardOpenOption.READ)) {

            long zipSize = ch.size();
            if (zipSize <= 0) throw new IOException("ZIP file is empty.");

            ZipEntryInfo e = parseSingleEntry(ch, zipSize);

            println("Entry: " + e.name);
            println("Compressed: " + e.compressedSize + " bytes, uncompressed (from CD): " + e.uncompressedSize + " bytes");
            println("Data offset: " + e.dataOffset);

            int[] counts = countBirthdaysStreamingInflater(ch, e.dataOffset, e.compressedSize);

            int bestIdx = -1;
            int best = 0;
            for (int i = 0; i < counts.length; i++) {
                int v = counts[i];
                if (best < v) {
                    best = v;
                    bestIdx = i;
                }
            }
            if (bestIdx < 0) return "No birthdays found";

            int month = (bestIdx / 31) + 1;
            int day   = (bestIdx % 31) + 1;
            return "Most common birthday is " + String.format("%02d-%02d", month, day) +
                    " with " + best +
                    " persons celebrating it.";
        }
    }

    // ===== Streaming inflate + count (true streaming; uses JDK Inflater) =====

    private static int[] countBirthdaysStreamingInflater(FileChannel ch, long dataOffset, long compressedSize)
            throws IOException, DataFormatException {

        var inflater = new Inflater(true); // raw DEFLATE like in ZIP
        try {
            byte[] out = new byte[OUT_BUF];

            long remaining = compressedSize;
            long inPos = dataOffset;

            int[] counts = new int[DAYS];

            boolean inHeader = true;
            long headerLenMinus1 = 0;
            int lineLen = -1;

            int posInLine = 0;
            int mt = 0, mo = 0, dt = 0, d2 = 0;

            while (true) {
                if (inflater.needsInput()) {
                    if (remaining <= 0) break;

                    long mapLen = min(MAP_CHUNK, remaining);
                    MappedByteBuffer in = ch.map(FileChannel.MapMode.READ_ONLY, inPos, mapLen);
                    inflater.setInput(in);

                    inPos += mapLen;
                    remaining -= mapLen;
                }

                int n = inflater.inflate(out);
                if (n == 0) {
                    if (inflater.finished()) break;
                    if (inflater.needsDictionary()) throw new IOException("DEFLATE needs dictionary (unexpected).");
                    continue;
                }

                for (int i = 0; i < n; i++) {
                    int b = out[i] & 0xFF;

                    if (inHeader) {
                        if (b == '\n') {
                            lineLen = (int) (headerLenMinus1 + 1);
                            inHeader = false;
                            posInLine = 0;
                            mt = mo = dt = d2 = 0;
                        } else {
                            headerLenMinus1++;
                        }
                        continue;
                    }

                    if (posInLine == MONTH_TENS)      mt = b - '0';
                    else if (posInLine == MONTH_ONES) mo = b - '0';
                    else if (posInLine == DAY_TENS)   dt = b - '0';
                    else if (posInLine == DAY_ONES)   d2 = b - '0';

                    posInLine++;

                    if (b == '\n') {
                        int month = mt * 10 + mo;
                        int day = dt * 10 + d2;

                        if (0 < month && month <= 12 && 0 < day && day <= 31) {
                            int idx = (month - 1) * 31 + (day - 1);
                            counts[idx]++;
                        }

                        if (lineLen > 0 && posInLine != lineLen) {
                            throw new IOException("Line length mismatch: expected " + lineLen + " got " + posInLine);
                        }

                        posInLine = 0;
                    } else {
                        if (lineLen > 0 && lineLen < posInLine) {
                            throw new IOException("Passed expected line length without newline (expected " + lineLen + ")");
                        }
                    }
                }
            }

            if (inHeader) throw new IOException("Never saw header newline; invalid CSV stream?");
            return counts;
        } finally {
            inflater.end();
        }
    }

    // ===== ZIP parsing (classic + ZIP64) via ByteBuffer (unaligned-safe) =====

    private static final class ZipEntryInfo {
        final String name;
        final long compressedSize;
        final long uncompressedSize;
        final long dataOffset;

        ZipEntryInfo(String name, long c, long u, long dataOffset) {
            this.name = name;
            this.compressedSize = c;
            this.uncompressedSize = u;
            this.dataOffset = dataOffset;
        }
    }

    private static ZipEntryInfo parseSingleEntry(FileChannel ch, long zipSize) throws IOException {
        // 1) map tail, find EOCD
        long tailSize = min(zipSize, (long) EOCD_TAIL);
        long tailOffset = zipSize - tailSize;

        ByteBuffer tail = ch.map(FileChannel.MapMode.READ_ONLY, tailOffset, tailSize).order(ByteOrder.LITTLE_ENDIAN);
        int eocdRel = findEocd(tail);
        if (eocdRel < 0) throw new IOException("EOCD not found.");

        long eocdAbs = tailOffset + eocdRel;

        int totalEntries16 = u16(tail, eocdRel + 10);
        long cdSize32 = u32(tail, eocdRel + 12);
        long cdOff32  = u32(tail, eocdRel + 16);

        boolean maybeZip64 =
                totalEntries16 == 0xFFFF ||
                        cdSize32 == 0xFFFF_FFFFL ||
                        cdOff32 == 0xFFFF_FFFFL;

        long cdSize;
        long cdOffset;
        int totalEntries;

        if (!maybeZip64) {
            totalEntries = totalEntries16;
            cdSize = cdSize32;
            cdOffset = cdOff32;
        } else {
            Zip64Central z64 = readZip64Central(ch, eocdAbs, tail, tailOffset, eocdRel);
            totalEntries = z64.totalEntries;
            cdSize = z64.cdSize;
            cdOffset = z64.cdOffset;
        }

        if (totalEntries != 1) throw new IOException("Expected exactly 1 file in ZIP, but found " + totalEntries);
        if (cdSize <= 0) throw new IOException("Central directory size invalid: " + cdSize);
        if (cdOffset < 0 || zipSize < cdOffset + cdSize) throw new IOException("Central directory out of bounds.");

        if (Integer.MAX_VALUE < cdSize) throw new IOException("Central directory too large to map: " + cdSize);

        // 2) map central directory
        ByteBuffer cd = ch.map(FileChannel.MapMode.READ_ONLY, cdOffset, cdSize).order(ByteOrder.LITTLE_ENDIAN);
        if (cd.getInt(0) != SIG_CEN) throw new IOException("Central directory header signature mismatch.");

        int cenPos = 0;

        int flags = u16(cd, cenPos + 8);
        int method = u16(cd, cenPos + 10);
        if (method != 8) throw new IOException("Expected DEFLATE (method 8), got " + method);
        if ((flags & 0x0001) != 0) throw new IOException("ZIP entry is encrypted (unsupported).");

        long compSize = u32(cd, cenPos + 20);
        long uncompSize = u32(cd, cenPos + 24);

        int nameLen = u16(cd, cenPos + 28);
        int extraLen = u16(cd, cenPos + 30);
        int commentLen = u16(cd, cenPos + 32);

        long localHdrOff = u32(cd, cenPos + 42);

        // If ZIP64, sizes/offsets may be in extra field (0x0001)
        // For this challenge: usually comp/uncomp fit in 32-bit, but offsets often not.
        // We'll parse ZIP64 extra conservatively.
        int namePos = cenPos + 46;
        byte[] nameBytes = new byte[nameLen];
        for (int i = 0; i < nameLen; i++) nameBytes[i] = cd.get(namePos + i);

        String name = ((flags & (1 << 11)) != 0)
                ? new String(nameBytes, StandardCharsets.UTF_8)
                : new String(nameBytes, StandardCharsets.ISO_8859_1);

        int extraPos = namePos + nameLen;

        if (compSize == 0xFFFF_FFFFL || uncompSize == 0xFFFF_FFFFL || localHdrOff == 0xFFFF_FFFFL) {
            Zip64Extra ex = readZip64ExtraFromCentral(cd, extraPos, extraLen, compSize, uncompSize, localHdrOff);
            compSize = ex.compSize;
            uncompSize = ex.uncompSize;
            localHdrOff = ex.localHdrOff;
        }

        if (compSize <= 0) throw new IOException("Compressed size missing/zero.");
        if (localHdrOff < 0 || zipSize <= localHdrOff) throw new IOException("Local header offset invalid.");

        // 3) map local header window
        long localMapLen = min(256 * 1024L, zipSize - localHdrOff);
        if (localMapLen <= 30) throw new IOException("Local header out of bounds.");
        if (Integer.MAX_VALUE < localMapLen) throw new IOException("Local header map window too large.");

        ByteBuffer lh = ch.map(FileChannel.MapMode.READ_ONLY, localHdrOff, localMapLen).order(ByteOrder.LITTLE_ENDIAN);
        if (lh.getInt(0) != SIG_LOCAL) throw new IOException("Local header signature mismatch at " + localHdrOff);

        int lhNameLen = u16(lh, 26);
        int lhExtraLen = u16(lh, 28);

        long dataOffset = localHdrOff + 30L + (long) lhNameLen + (long) lhExtraLen;

        if (zipSize < dataOffset + compSize) throw new IOException("Compressed data range exceeds file bounds.");

        return new ZipEntryInfo(name, compSize, uncompSize, dataOffset);
    }

    private static final class Zip64Central {
        final int totalEntries;
        final long cdSize;
        final long cdOffset;

        Zip64Central(int totalEntries, long cdSize, long cdOffset) {
            this.totalEntries = totalEntries;
            this.cdSize = cdSize;
            this.cdOffset = cdOffset;
        }
    }

    private static Zip64Central readZip64Central(FileChannel ch,
                                                 long eocdAbs,
                                                 ByteBuffer tail,
                                                 long tailOffset,
                                                 int eocdRel) throws IOException {

        // ZIP64 locator is 20 bytes immediately before EOCD
        long locatorAbs = eocdAbs - 20;
        if (locatorAbs < 0) throw new IOException("ZIP64 locator position invalid.");

        ByteBuffer loc;
        if (tailOffset <= locatorAbs && locatorAbs + 20 <= tailOffset + tail.limit()) {
            int locRel = (int) (locatorAbs - tailOffset);
            loc = tail.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            loc.position(locRel);
            loc.limit(locRel + 20);
            loc = loc.slice().order(ByteOrder.LITTLE_ENDIAN);
        } else {
            loc = ch.map(FileChannel.MapMode.READ_ONLY, locatorAbs, 20).order(ByteOrder.LITTLE_ENDIAN);
        }

        if (loc.getInt(0) != SIG_ZIP64_LOC) {
            throw new IOException("ZIP64 indicated, but ZIP64 locator signature mismatch.");
        }

        long zip64EocdOffset = loc.getLong(8);
        if (zip64EocdOffset < 0) throw new IOException("ZIP64 EOCD offset invalid.");

        // ZIP64 EOCD record: first 56 bytes cover what we need
        ByteBuffer z64 = ch.map(FileChannel.MapMode.READ_ONLY, zip64EocdOffset, 56).order(ByteOrder.LITTLE_ENDIAN);
        if (z64.getInt(0) != SIG_ZIP64_EOCD) throw new IOException("ZIP64 EOCD signature mismatch.");

        // Offsets inside ZIP64 EOCD:
        // 24: total entries on this disk (8)
        // 32: total entries (8)
        // 40: size of central dir (8)
        // 48: offset of central dir (8)
        long totalEntries64 = z64.getLong(32);
        long cdSize = z64.getLong(40);
        long cdOffset = z64.getLong(48);

        if (totalEntries64 < 0 || Integer.MAX_VALUE < totalEntries64) {
            throw new IOException("ZIP64 total entries out of supported range: " + totalEntries64);
        }
        return new Zip64Central((int) totalEntries64, cdSize, cdOffset);
    }

    private static final class Zip64Extra {
        final long uncompSize;
        final long compSize;
        final long localHdrOff;

        Zip64Extra(long uncompSize, long compSize, long localHdrOff) {
            this.uncompSize = uncompSize;
            this.compSize = compSize;
            this.localHdrOff = localHdrOff;
        }
    }

    private static Zip64Extra readZip64ExtraFromCentral(ByteBuffer cd,
                                                        int extraPos,
                                                        int extraLen,
                                                        long compSize32,
                                                        long uncompSize32,
                                                        long localHdrOff32) throws IOException {

        long uncomp = uncompSize32;
        long comp = compSize32;
        long lhoff = localHdrOff32;

        int p = extraPos;
        int end = extraPos + extraLen;

        while (p + 4 <= end) {
            int headerId = cd.getShort(p) & 0xFFFF;
            int dataSize = cd.getShort(p + 2) & 0xFFFF;
            int dataPos = p + 4;

            if (dataPos + dataSize > end) break;

            if (headerId == 0x0001) { // ZIP64 extended information extra field
                int q = dataPos;

                if (uncomp == 0xFFFF_FFFFL) {
                    if (q + 8 > dataPos + dataSize) throw new IOException("ZIP64 extra truncated (uncompSize).");
                    uncomp = cd.getLong(q);
                    q += 8;
                }
                if (comp == 0xFFFF_FFFFL) {
                    if (q + 8 > dataPos + dataSize) throw new IOException("ZIP64 extra truncated (compSize).");
                    comp = cd.getLong(q);
                    q += 8;
                }
                if (lhoff == 0xFFFF_FFFFL) {
                    if (q + 8 > dataPos + dataSize) throw new IOException("ZIP64 extra truncated (localHdrOff).");
                    lhoff = cd.getLong(q);
                    // q += 8;
                }
                break;
            }

            p = dataPos + dataSize;
        }

        if (comp <= 0 || uncomp <= 0 || lhoff < 0) {
            throw new IOException("Could not read ZIP64 extra field for sizes/offsets.");
        }
        return new Zip64Extra(uncomp, comp, lhoff);
    }

    private static int findEocd(ByteBuffer tail) {
        int limit = tail.limit();
        for (int pos = limit - 22; 0 <= pos; pos--) {
            if (tail.getInt(pos) == SIG_EOCD) return pos;
        }
        return -1;
    }

    private static int u16(ByteBuffer bb, int off) {
        return bb.getShort(off) & 0xFFFF;
    }

    private static long u32(ByteBuffer bb, int off) {
        return bb.getInt(off) & 0xFFFF_FFFFL;
    }
}
