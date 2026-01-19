import static java.lang.IO.println;
import static java.nio.file.Files.newInputStream;

static final String INPUT_FILE_NAME = "personal-data_1000.zip";

void main() throws IOException {
    println("Extracting file " + INPUT_FILE_NAME);
    try (var input = new ZipInputStream(newInputStream(Path.of(INPUT_FILE_NAME)))) {
        ZipEntry entry;
        while ((entry = input.getNextEntry()) != null) {
            try (var fileOutputStream = new FileOutputStream("extracted-" + entry.getName());
                 var output = new BufferedOutputStream(fileOutputStream);
            ) {
                var buffer = new byte[1024];
                int length;
                while ((length = input.read(buffer)) > 0) {
                    output.write(buffer, 0, length);
                }
            }
        }
    }

}

