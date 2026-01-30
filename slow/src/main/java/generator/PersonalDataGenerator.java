package generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static java.lang.IO.println;
import static java.lang.String.format;
import static java.time.Month.JANUARY;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Arrays.stream;
import static java.util.stream.Stream.concat;

public class PersonalDataGenerator {

    static final String FIRST_NAME_HEADER = "First name";
    static final String[] FIRST_NAMES = {
            "Max", "Moritz", "Susi", "Lieschen", "Hans", "Greta"
    };

    static final String LAST_NAME_HEADER = "Last name";
    static final String[] LAST_NAMES = {
            "Muster", "Meier", "Schmidt", "Schneider", "Fischer", "Weber"
    };

    static final String BIRTH_DATE_HEADER = "Birth date";

    static final int NUMBER_OF_PERSONS = 100_000_000;

    static void main() throws IOException {
        generate(NUMBER_OF_PERSONS);
    }

    public static void generate(int numberOfPersons) throws IOException {
        var random = new Random();
        var birthdaysAsDaysSince1Jan1930 = stream(new int[numberOfPersons])
                .map(i -> {
                    var earliestBirthDate = LocalDate.of(1930, JANUARY, 1);
                    var maxDays = DAYS.between(earliestBirthDate, LocalDate.now());
                    return (int) random.nextLong(maxDays + 1);
                })
                .toArray();
        generate(birthdaysAsDaysSince1Jan1930, Path.of("personal-data.zip"));
    }

    public static void generate(int[] birthdaysAsDaysSince1Jan1930, Path outputPath) throws IOException {
        println("Personal Data Generator");

        var maxLengthFirstName = concat(stream(FIRST_NAMES), Stream.of(FIRST_NAME_HEADER))
                .mapToInt(String::length)
                .max()
                .orElseThrow();

        var maxLengthLastName = concat(stream(LAST_NAMES), Stream.of(LAST_NAME_HEADER))
                .mapToInt(String::length)
                .max()
                .orElseThrow();

        var maxLengthBirthDate = Math.max(BIRTH_DATE_HEADER.length(), "YYYY-MM-DD".length());

        var onePercent = birthdaysAsDaysSince1Jan1930.length / 100;
        var earliestBirthDate = LocalDate.of(1930, JANUARY, 1);
        var maxDays = DAYS.between(earliestBirthDate, LocalDate.now());
        var dateTimeFormatter = new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd")
                .toFormatter();

        try (
                var zipOutputStream = new ZipOutputStream(Files.newOutputStream(outputPath));
                var streamWriter = new OutputStreamWriter(zipOutputStream);
                var output = new BufferedWriter(streamWriter);
        ) {
            zipOutputStream.putNextEntry(new ZipEntry("personal-data.csv"));
            var formatString = "%-" + maxLengthFirstName + "s;" +
                    "%-" + maxLengthLastName + "s;" +
                    "%-" + maxLengthBirthDate + "s;" +
                    "\n";
            output.write(format(formatString,
                    FIRST_NAME_HEADER,
                    LAST_NAME_HEADER,
                    BIRTH_DATE_HEADER
            ));
            var random = new Random();
            for (int i = 0; i < birthdaysAsDaysSince1Jan1930.length; i++) {
                output.write(format(formatString,
                        FIRST_NAMES[random.nextInt(FIRST_NAMES.length)],
                        LAST_NAMES[random.nextInt(LAST_NAMES.length)],
                        earliestBirthDate.plusDays(birthdaysAsDaysSince1Jan1930[i]).format(dateTimeFormatter)
                ));

                if (onePercent > 0 && (i + 1) % onePercent == 0) {
                    println("Generated " + (i / onePercent) + "% of data...");
                }
            }
        }
    }

    public static void add(int additionalPersons) throws IOException {
        var zipFileName = "personal-data.zip";
        var tempFileName = "personal-data.tmp.zip";

        var random = new Random();
        var earliestBirthDate = LocalDate.of(1930, JANUARY, 1);
        var maxDays = DAYS.between(earliestBirthDate, LocalDate.now());
        var formatter = new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd")
                .toFormatter();

        try (
                var zipIn = new ZipInputStream(Files.newInputStream(Path.of(zipFileName)));
                var zipOut = new ZipOutputStream(Files.newOutputStream(Path.of(tempFileName)));
        ) {
            var buffer = new byte[1024 * 1024]; // 1 MB
            ZipEntry entry;

            while ((entry = zipIn.getNextEntry()) != null) {
                var newEntry = new ZipEntry(entry.getName());
                zipOut.putNextEntry(newEntry);

                if (!entry.getName().equals("personal-data.csv")) {
                    // Blindes Kopieren aller anderen Entries
                    int read;
                    while ((read = zipIn.read(buffer)) != -1) {
                        zipOut.write(buffer, 0, read);
                    }
                } else {
                    // CSV: komplett durchstreamen
                    int read;
                    while ((read = zipIn.read(buffer)) != -1) {
                        zipOut.write(buffer, 0, read);
                    }

                    // Neue Datensätze anhängen
                    for (int i = 0; i < additionalPersons; i++) {
                        var line =
                                FIRST_NAMES[random.nextInt(FIRST_NAMES.length)] + ";" +
                                        LAST_NAMES[random.nextInt(LAST_NAMES.length)] + ";" +
                                        earliestBirthDate
                                                .plusDays(random.nextLong(maxDays + 1))
                                                .format(formatter) +
                                        "\n";

                        zipOut.write(line.getBytes());
                    }
                }

                zipOut.closeEntry();
                zipIn.closeEntry();
            }
        }

        // Atomarer Austausch
        Files.move(
                Path.of(tempFileName),
                Path.of(zipFileName),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
        );
    }
}