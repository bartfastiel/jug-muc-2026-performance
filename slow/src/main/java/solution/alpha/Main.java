package solution.alpha;

import com.opencsv.bean.CsvBindByPosition;
import com.opencsv.bean.CsvDate;
import com.opencsv.bean.CsvToBeanBuilder;

import java.io.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Collection;
import java.util.LinkedList;
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

    public static String run(Path zipFile) throws IOException {
        var csvFile = extractZipFile(zipFile);
        var persons = parse(csvFile);
        var personWithMostCommonBirthday = findPersonWithMostCommonBirthday(persons);
        return "Most common birthday is " + MonthDay.from(personWithMostCommonBirthday.birthDate) +
                " with " + numberOfPersonsThatCelebrate(persons, MonthDay.from(personWithMostCommonBirthday.birthDate)) +
                " persons celebrating it.";
    }

    private static Path extractZipFile(Path zipFile) throws IOException {
        try (var input = new ZipInputStream(newInputStream(zipFile))) {
            var entry = input.getNextEntry();
            if (entry == null) {
                throw new IOException("No entries found in zip file " + "personal-data.zip");
            }
            var outputFile = createTempFile("extracted-personal-data", ".csv");
            try (var fileOutputStream = new FileOutputStream(outputFile.toFile());
                 var output = new BufferedOutputStream(fileOutputStream)) {
                var nextByte = input.read();
                while (nextByte != -1) {
                    output.write(nextByte);
                    nextByte = input.read();
                }
            }
            return outputFile;
        }
    }

    public static class Person {

        @CsvBindByPosition(position = 0)
        public String firstName;

        @CsvBindByPosition(position = 1)
        public String lastName;

        @CsvBindByPosition(position = 2)
        @CsvDate(value = "yyyy-MM-dd")
        public LocalDate birthDate;
    }

    private static Collection<Person> parse(Path csvFile) throws IOException {
        var persons = new LinkedList<Person>();
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

    private static Person findPersonWithMostCommonBirthday(Collection<Person> persons) {
        return persons.stream()
                .max(comparingLong(p -> numberOfPersonsThatCelebrate(persons, MonthDay.from(p.birthDate))))
                .orElseThrow(() -> new IllegalStateException("No persons found"));
    }

    private static long numberOfPersonsThatCelebrate(Collection<Person> persons, MonthDay birthday) {
        return persons.stream()
                .filter(other -> MonthDay.from(other.birthDate).equals(birthday))
                .count();
    }
}