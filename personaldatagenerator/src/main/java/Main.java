
void main() throws IOException {
    System.out.println("Personal Data Generator");
    var outputFileName = "personal-data.txt";
    var outputPath = Path.of(outputFileName);
    try (var writer = Files.newBufferedWriter(outputPath)) {
        writer.write("First name;Last name ;\n");
        writer.write("Max       ;Mustermann;\n");
    }
}
