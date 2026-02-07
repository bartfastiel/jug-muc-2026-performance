package solution.golf;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static java.lang.IO.println;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.newInputStream;
import static java.util.Comparator.comparingLong;

public class Main {

    private static final String INPUT_FILE = "personal-data.zip";

    public static void main() throws IOException {
        println(run(Path.of(INPUT_FILE)));
    }

    public static String run(Path zipPath) throws IOException {
        var csvFile = extractZipFile(zipPath);
        var persons = parse(csvFile);
        var birthdays = countPartiesForEachDay(persons);
        var mostCommonBirthday = birthdays.entrySet().stream()
                .max(comparingLong(Map.Entry::getValue))
                .orElseThrow(() -> new IllegalStateException("No birthdays found"));
        return "Most common birthday is " + mostCommonBirthday.getKey() +
                " with " + mostCommonBirthday.getValue() +
                " persons celebrating it.";
    }

    private static Path extractZipFile(Path zipPath) throws IOException {
        try (var input = new ZipInputStream(newInputStream(zipPath))) {
            var entry = input.getNextEntry();
            if (entry == null) {
                throw new IOException("No entries found in zip file " + "personal-data.zip");
            }
            var outputFile = createTempFile("extracted-personal-data", ".csv");
            var buffer = new byte[8192];
            try (var fileOutputStream = new FileOutputStream(outputFile.toFile());
                 var output = new BufferedOutputStream(fileOutputStream)) {
                var bytesRead = input.read(buffer);
                while (bytesRead != -1) {
                    output.write(buffer, 0, bytesRead);
                    bytesRead = input.read(buffer);
                }
            }
            return outputFile;
        }
    }

    private static Collection<String> parse(Path csvFile) throws IOException {
        var birthdays = new ArrayList<String>(1_000_000);
        try (var reader = new BufferedReader(new FileReader(csvFile.toFile()))) {
            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                var parts = line.split(";");
                // take only mm-yy (last 5 chars) from yyyy-mm-dd
                birthdays.add(parts[2].substring(5));
            }
        }
        println("Extracted " + birthdays.size() + " persons:");
        return birthdays;
    }

    private static Map<String, Long> countPartiesForEachDay(Collection<String> individualBirthdays) {
        var birthdays = new HashMap<String, Long>();
        for (var birthday : individualBirthdays) {
            birthdays.put(birthday, birthdays.getOrDefault(birthday, 0L) + 1);
        }
        return birthdays;
    }
}