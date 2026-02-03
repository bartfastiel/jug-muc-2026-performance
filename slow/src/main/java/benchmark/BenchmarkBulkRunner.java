package benchmark;

import generator.PersonalDataGenerator;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.IO.println;
import static java.lang.System.currentTimeMillis;
import static java.nio.file.Files.*;

public class BenchmarkBulkRunner {

    public static final int MIN_PERSONS = 1;
    public static final int INITIAL_STEP_SIZE = 1;
    public static final int MAX_PERSONS = 100_000_000;
    public static final long INCREASE_STEP_SIZE_THRESHOLD_MILLIS = 2_000L;

    static void main() throws Exception {
        var output = Path.of("results/measured.csv");
        createDirectories(output.getParent());

        // create initial person data
        var currentNumberOfPersons = 1;
        PersonalDataGenerator.generate(currentNumberOfPersons);

        try (var writer = newBufferedWriter(output)) {
            writer.write("\"Benchmark\",\"Mode\",\"Threads\",\"Samples\",\"Score\",\"Score Error (99,9%)\",\"Unit\",\"Param: numberOfPersons\"\n");
            writer.flush();

            var step = INITIAL_STEP_SIZE;
            for (var n = MIN_PERSONS; n <= MAX_PERSONS; n += step) {
                println("Running benchmark for " + n + " persons...");
                var start = currentTimeMillis();

                PersonalDataGenerator.add(n - currentNumberOfPersons);
                currentNumberOfPersons = n;

                var tmp = Files.createTempFile("jmh-", ".csv");

                var opt = new OptionsBuilder()
                        .include(BenchmarkConfig.class.getName().replace(".", "\\."))
                        .param("numberOfPersons", String.valueOf(n))
                        .forks(1)
                        .warmupIterations(0)
                        .measurementIterations(1)
                        .resultFormat(ResultFormatType.CSV)
                        .result(tmp.toString())
                        .build();

                new Runner(opt).run();

                // read last line from the generated jmh report, skip first line (header)
                try (var reader = newBufferedReader(tmp)) {
                    String line;
                    String lastLine = null;
                    while ((line = reader.readLine()) != null) {
                        lastLine = line;
                    }
                    if (lastLine != null) {
                        writer.write(lastLine);
                        writer.write("\n");
                    }
                }

                writer.flush();

                var end = currentTimeMillis();
                if (INCREASE_STEP_SIZE_THRESHOLD_MILLIS * (step / INITIAL_STEP_SIZE) <= (end - start)) {
                    println("  (benchmark took " + (end - start) + " ms, increasing step)");
                    step *= 2;
                }
            }
        }
    }
}
