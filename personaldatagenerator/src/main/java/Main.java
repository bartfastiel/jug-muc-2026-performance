
static final String[] FIRST_NAMES = {
        "Max", "Moritz", "Susi", "Lieschen", "Hans", "Greta"
};

void main() throws IOException {
    System.out.println("Personal Data Generator");
    var outputFileName = "personal-data.zip";
    var outputPath = Path.of(outputFileName);
    try (
            var zipOutputStream = new ZipOutputStream(Files.newOutputStream(outputPath));
            var out = new OutputStreamWriter(zipOutputStream);
            var writer = new BufferedWriter(out);
    ) {
        zipOutputStream.putNextEntry(new ZipEntry("personal-data.csv"));
        writer.write("First name;Last name ;\n");
        var random = new Random();
        for (int i = 0; i < 100; i++) {
            var firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
            var lastName = "LastName" + (i + 1);
            writer.write(firstName + ";" + lastName + ";\n");
        }
    }
}
