package solution;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.time.LocalDate;
import java.util.stream.Stream;

import static generator.PersonalDataGenerator.generate;
import static java.nio.file.Files.createTempFile;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(textBlock = """
            twoSame, 1930-01-01|1930-01-01, Most common birthday is --01-01 with 2 persons celebrating it.
            """)
    void run(String name, String datesPipeSeparated, String expected) throws IOException {
        var birthdaysAsDaysSince1Jan1930 = Stream.of(datesPipeSeparated.split("\\|"))
                .mapToInt(date -> (int) java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.of(1930, 1, 1),
                        LocalDate.parse(date)))
                .toArray();

        var tempFile = createTempFile("personal-data", ".zip");
        generate(birthdaysAsDaysSince1Jan1930, tempFile);

        var result = solution.alpha.Main.run(tempFile);

        assertEquals(expected, result);
    }
}
