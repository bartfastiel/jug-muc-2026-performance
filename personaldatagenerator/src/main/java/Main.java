import static java.lang.IO.println;
import static java.lang.String.format;
import static java.time.Month.JANUARY;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Arrays.stream;
import static java.util.stream.Stream.concat;

static final String FIRST_NAME_HEADER = "First name";
static final String[] FIRST_NAMES = {
        "Max", "Moritz", "Susi", "Lieschen", "Hans", "Greta"
};

static final String LAST_NAME_HEADER = "Last name";
static final String[] LAST_NAMES = {
        "Muster", "Meier", "Schmidt", "Schneider-Ulrich", "Fischer", "Weber"
};

static final String BIRTH_DATE_HEADER = "Birth date";

static final int NUMBER_OF_PERSONS = 100_000_000;

void main() throws IOException {
    println("Personal Data Generator");
    var outputFileName = "personal-data_" + NUMBER_OF_PERSONS + ".zip";
    var outputPath = Path.of(outputFileName);

    var maxLengthFirstName = concat(stream(FIRST_NAMES), Stream.of(FIRST_NAME_HEADER))
            .mapToInt(String::length)
            .max()
            .orElseThrow();

    var maxLengthLastName = concat(stream(LAST_NAMES), Stream.of(LAST_NAME_HEADER))
            .mapToInt(String::length)
            .max()
            .orElseThrow();

    var maxLengthBirthDate = Math.max(BIRTH_DATE_HEADER.length(), "YYYY-MM-DD".length());

    var onePercent = NUMBER_OF_PERSONS / 100;
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
        for (int i = 0; i < NUMBER_OF_PERSONS; i++) {
            output.write(format(formatString,
                    FIRST_NAMES[random.nextInt(FIRST_NAMES.length)],
                    LAST_NAMES[random.nextInt(LAST_NAMES.length)],
                    earliestBirthDate.plusDays(random.nextLong(maxDays + 1)).format(dateTimeFormatter)
                    ));

            if (i % onePercent == 0) {
                println("Generated " + (i / onePercent) + "% of data...");
            }
        }
    }
}
