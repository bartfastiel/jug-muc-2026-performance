package solution.juliett;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;

import static java.lang.IO.println;
import static java.nio.file.Files.newInputStream;

public class Main {

    private static final String INPUT_FILE = "personal-data.zip";

    public static void main() throws IOException {
        var partiesPerDay = new int[12 * 31 + 1];
        try (var input = new ZipInputStream(newInputStream(Path.of(INPUT_FILE)))) {
            var entry = input.getNextEntry();
            if (entry == null) {
                throw new IOException("No entries found in zip file " + "personal-data.zip");
            }
            try (var reader = new BufferedReader(new InputStreamReader(input))) {
                var line = reader.readLine(); // skip header
                while ((line = reader.readLine()) != null) {
                    var month = (line.charAt(26) - '0') * 10 + (line.charAt(27) - '0');
                    var day = (line.charAt(29) - '0') * 10 + (line.charAt(30) - '0');
                    var dayIndex = (month - 1) * 31 + (day - 1);
                    partiesPerDay[dayIndex]++;
                }
            }
        }
        Integer mostCommonBirthday = null;
        var maxParties = 0;
        for (var i = 0; i < partiesPerDay.length; i++) {
            if (partiesPerDay[i] > maxParties) {
                maxParties = partiesPerDay[i];
                mostCommonBirthday = i;
            }
        }
        if (mostCommonBirthday == null) {
            println("No birthdays found");
            return;
        }
        var month = (mostCommonBirthday / 31) + 1;
        var day = (mostCommonBirthday % 31) + 1;
        println("Most common birthday is " + String.format("%02d-%02d", month, day) +
                " with " + maxParties +
                " persons celebrating it.");
    }
}