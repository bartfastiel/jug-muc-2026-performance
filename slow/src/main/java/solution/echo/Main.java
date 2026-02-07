package solution.echo;

import com.opencsv.bean.CsvBindByPosition;
import com.opencsv.bean.CsvDate;
import com.opencsv.bean.CsvToBeanBuilder;

import java.io.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.MonthDay;
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

    public static class Person {

        @CsvBindByPosition(position = 2)
        @CsvDate(value = "yyyy-MM-dd")
        public LocalDate birthDate;
    }

    private static Collection<Person> parse(Path csvFile) throws IOException {
        var persons = new ArrayList<Person>(1_000_000);
        try (var reader = new BufferedReader(new FileReader(csvFile.toFile()))) {
            new CsvToBeanBuilder<Person>(reader)
                    .withType(Person.class)
                    .withSeparator(';')
                    .withSkipLines(1)
                    .build()
                    .forEach(persons::add);
        }
        println("Extracted " + persons.size() + " persons:");
        return persons;
    }

    private static Map<MonthDay, Long> countPartiesForEachDay(Collection<Person> persons) {
        var birthdays = new HashMap<MonthDay, Long>();
        for (var person : persons) {
            var monthDay = MonthDay.from(person.birthDate);
            birthdays.put(monthDay, birthdays.getOrDefault(monthDay, 0L) + 1);
        }
        return birthdays;
    }
}