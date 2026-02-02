package benchmark;

import generator.PersonalDataGenerator;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.HOURS;

public class BenchmarkParallelRunner {

    public static final int MIN_PERSONS = 1;
    public static final int INITIAL_STEP_SIZE = 1;
    public static final int MAX_PERSONS = 100_000_000;

    static void main() throws Exception {
        var output = Path.of("results/measured.csv");
        Files.createDirectories(output.getParent());

        Lock writeLock = new ReentrantLock();

        try (
                var writer = Files.newBufferedWriter(output);
                var workers = Executors.newFixedThreadPool(
                        Runtime.getRuntime().availableProcessors()
                )
        ) {
            writer.write("\"Benchmark\",\"Mode\",\"Threads\",\"Samples\",\"Score\",\"Score Error (99,9%)\",\"Unit\",\"Param: numberOfPersons\"\n");
            writer.flush();

            var currentNumberOfPersons = MIN_PERSONS;
            PersonalDataGenerator.generate(currentNumberOfPersons);

            for (var n = MIN_PERSONS; n <= MAX_PERSONS; n += INITIAL_STEP_SIZE) {
                final var persons = n;

                var toAdd = persons - currentNumberOfPersons;
                if (toAdd > 0) {
                    PersonalDataGenerator.add(toAdd);
                    currentNumberOfPersons = persons;
                }

                workers.submit(() -> runBenchmark(persons, writer, writeLock));
            }

            workers.shutdown();
            if (!workers.awaitTermination(1, HOURS)) {
                throw new RuntimeException("Benchmark tasks did not finish in time");
            }
        }
    }

    private static void runBenchmark(int persons,
                                     BufferedWriter writer,
                                     Lock writeLock) {
        try {
            var tmp = Files.createTempFile("jmh-", ".csv");

            var opt = new OptionsBuilder()
                    .include(Benchmark.class.getName().replace(".", "\\."))
                    .param("numberOfPersons", String.valueOf(persons))
                    .forks(1)
                    .warmupIterations(0)
                    .measurementIterations(1)
                    .resultFormat(ResultFormatType.CSV)
                    .result(tmp.toString())
                    .build();

            new Runner(opt).run();

            String lastLine = null;
            try (var reader = Files.newBufferedReader(tmp)) {
                reader.readLine(); // Header überspringen
                String line;
                while ((line = reader.readLine()) != null) {
                    lastLine = line;
                }
            }

            if (lastLine != null) {
                writeLock.lock();
                try {
                    writer.write(lastLine);
                    writer.write("\n");
                    writer.flush(); // wichtig für Live-Plot
                } finally {
                    writeLock.unlock();
                }
            }

            Files.deleteIfExists(tmp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
