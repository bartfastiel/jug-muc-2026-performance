package solution.india;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static java.lang.IO.println;
import static java.nio.file.Files.newInputStream;
import static java.util.Comparator.comparingLong;

public class Main {

    private static final String INPUT_FILE = "personal-data.zip";

    public static void main() throws IOException {
        var persons = parseZipFile();
        var birthdays = countPartiesForEachDay(persons);
        var mostCommonBirthday = birthdays.entrySet().stream()
                .max(comparingLong(Map.Entry::getValue))
                .orElseThrow(() -> new IllegalStateException("No birthdays found"));
        println("Most common birthday is " + mostCommonBirthday.getKey() +
                " with " + mostCommonBirthday.getValue() +
                " persons celebrating it.");
    }

    private static ArrayList<String> parseZipFile() throws IOException {
        try (var input = new ZipInputStream(newInputStream(Path.of(INPUT_FILE)))) {
            var entry = input.getNextEntry();
            if (entry == null) {
                throw new IOException("No entries found in zip file " + "personal-data.zip");
            }
            var birthdays = new ArrayList<String>(1_000_000);
            try (var reader = new BufferedReader(new InputStreamReader(input))) {
                String line = reader.readLine(); // skip header
                while ((line = reader.readLine()) != null) {
                    birthdays.add(line.substring(26));
                }
            }
            println("Extracted " + birthdays.size() + " persons:");
            return birthdays;
        }
    }

    private static Map<String, Long> countPartiesForEachDay(Collection<String> individualBirthdays) {
        var birthdays = new HashMap<String, Long>();
        for (var birthday : individualBirthdays) {
            birthdays.put(birthday, birthdays.getOrDefault(birthday, 0L) + 1);
        }
        return birthdays;
    }
}