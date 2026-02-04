package solution.juliett;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;

import static java.lang.IO.println;
import static java.nio.file.Files.newInputStream;

public class OnlyUnzip {

    private static final String INPUT_FILE = "personal-data.zip";

    public static void main() throws IOException {
        println(run(Path.of(INPUT_FILE)));
    }

    public static String run(Path zipFile) throws IOException {
        try (var input = new ZipInputStream(newInputStream(zipFile))) {
            var entry = input.getNextEntry();
            if (entry == null) {
                throw new IOException("No entries found in zip file " + "personal-data.zip");
            }
            try (var reader = new BufferedReader(new InputStreamReader(input))) {
                while (reader.readLine() != null) {
                }
            }
        }
        return "done";
    }
}