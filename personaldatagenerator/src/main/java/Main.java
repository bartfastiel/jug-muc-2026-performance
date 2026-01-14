
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
        writer.write("Max       ;Mustermann;\n");
    }
}
