package slow;

import com.opencsv.bean.CsvToBeanBuilder;

import java.io.*;
import java.nio.file.Path;
import java.time.MonthDay;
import java.util.Collection;
import java.util.LinkedList;
import java.util.zip.ZipInputStream;

import static java.lang.IO.println;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.newInputStream;
import static java.util.Comparator.comparingLong;

public class Main {
    public static void run(int numberOfPersons) throws IOException {
        var csvFile = extractZipFile("personal-data.zip");
        var persons = parse(csvFile);
        var personWithMostCommonBirthday = findPersonWithMostCommonBirthday(persons);
        println("Most common birthday is " + MonthDay.from(personWithMostCommonBirthday.getBirthDate()) +
                " with " + numberOfPersonsThatCelebrate(persons, MonthDay.from(personWithMostCommonBirthday.getBirthDate())) +
                " persons celebrating it.");
    }

    static Path extractZipFile(String inputFile) throws IOException {
        try (var input = new ZipInputStream(newInputStream(Path.of(inputFile)))) {
            var entry = input.getNextEntry();
            if (entry == null) {
                throw new IOException("No entries found in zip file " + inputFile);
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

    static Collection<Person> parse(Path csvFile) throws IOException {
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

    static Person findPersonWithMostCommonBirthday(Collection<Person> persons) {
        return persons.stream()
                .max(comparingLong(p -> numberOfPersonsThatCelebrate(persons, MonthDay.from(p.getBirthDate()))))
                .orElseThrow(() -> new IllegalStateException("No persons found"));
    }

    static long numberOfPersonsThatCelebrate(Collection<Person> persons, MonthDay birthday) {
        return persons.stream()
                .filter(other -> MonthDay.from(other.getBirthDate()).equals(birthday))
                .count();
    }
}