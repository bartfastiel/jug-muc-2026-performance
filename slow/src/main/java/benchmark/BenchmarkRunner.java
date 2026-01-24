package benchmark;

import generator.PersonalDataGenerator;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class BenchmarkRunner {

    public static final int MIN_PERSONS = 1;
    public static final int INITIAL_STEP_SIZE = 100;
    public static final int MAX_PERSONS = 100_000_000;
    public static final long INCREASE_STEP_SIZE_THRESHOLD_MILLIS = 2_000L;

    static void main() throws Exception {
        Path output = Path.of("results/measured.csv");
        Files.createDirectories(output.getParent());

        // create initial person data
        var currentNumberOfPersons = 1;
        PersonalDataGenerator.generate(currentNumberOfPersons);

        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write("\"Benchmark\",\"Mode\",\"Threads\",\"Samples\",\"Score\",\"Score Error (99,9%)\",\"Unit\",\"Param: numberOfPersons\"\n");
            writer.flush();

            var step = INITIAL_STEP_SIZE;
            for (int n = MIN_PERSONS; n <= MAX_PERSONS; n += step) {
                IO.println("Running benchmark for " + n + " persons...");
                var start = System.currentTimeMillis();

                PersonalDataGenerator.add(n - currentNumberOfPersons);
                currentNumberOfPersons = n;

                Path tmp = Files.createTempFile("jmh-", ".csv");

                Options opt = new OptionsBuilder()
                        .include(BirthdayBenchmark.class.getName().replace(".", "\\."))
                        .param("numberOfPersons", String.valueOf(n))
                        .forks(1)
                        .warmupIterations(0)
                        .measurementIterations(1)
                        .resultFormat(ResultFormatType.CSV)
                        .result(tmp.toString())
                        .build();

                new Runner(opt).run();

                // read last line from the generated jmh report, skip first line (header)
                try (BufferedReader reader = Files.newBufferedReader(tmp)) {
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

                var end = System.currentTimeMillis();
                if (INCREASE_STEP_SIZE_THRESHOLD_MILLIS * (step / INITIAL_STEP_SIZE) <= (end - start)) {
                    IO.println("  (benchmark took " + (end - start) + " ms, increasing step)");
                    step *= 2;
                }
            }
        }
    }
}
