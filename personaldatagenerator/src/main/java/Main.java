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

void main() throws IOException {
    System.out.println("Personal Data Generator");
    var outputFileName = "personal-data.zip";
    var outputPath = Path.of(outputFileName);

    var maxLengthFirstName = concat(stream(FIRST_NAMES), Stream.of(FIRST_NAME_HEADER))
            .mapToInt(String::length)
            .max()
            .orElseThrow();

    var maxLengthLastName = concat(stream(LAST_NAMES), Stream.of(LAST_NAME_HEADER))
            .mapToInt(String::length)
            .max()
            .orElseThrow();

    try (
            var zipOutputStream = new ZipOutputStream(Files.newOutputStream(outputPath));
            var out = new OutputStreamWriter(zipOutputStream);
            var writer = new BufferedWriter(out);
    ) {
        zipOutputStream.putNextEntry(new ZipEntry("personal-data.csv"));
        writer.write(String.format("%-" + maxLengthFirstName + "s;" +
                        "%-" + maxLengthLastName + "s;" +
                        "\n",
                FIRST_NAME_HEADER,
                LAST_NAME_HEADER
        ));
        var random = new Random();
        for (int i = 0; i < 100; i++) {
            var firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
            var lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
            writer.write(String.format("%-" + maxLengthFirstName + "s;" +
                            "%-" + maxLengthLastName + "s;" +
                            "\n",
                    firstName,
                    lastName
            ));
        }
    }
}
