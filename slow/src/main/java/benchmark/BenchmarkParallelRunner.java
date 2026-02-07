package benchmark;

import generator.PersonalDataGenerator;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.IO.println;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Runtime.getRuntime;
import static java.nio.file.Files.*;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.HOURS;

public class BenchmarkParallelRunner {

    public static final int MIN_PERSONS = 1;
    public static final int INITIAL_STEP_SIZE = 1;
    public static final int MAX_PERSONS = 100_000;
    public static final int MIN_THREADS = 1;
    public static final int MAX_THREADS = 10;
    public static final int ITERATIONS = 1;

    public static void main() throws Exception {
        var output = Path.of("results/measured.csv");
        createDirectories(output.getParent());

        Lock writeLock = new ReentrantLock();

        try (
                var writer = newBufferedWriter(output);
                var workers = newFixedThreadPool(
                        max(MIN_THREADS, min(MAX_THREADS, getRuntime().availableProcessors() / 2))
                )
        ) {
            writer.write("\"Benchmark\",\"Mode\",\"Threads\",\"Samples\",\"Score\",\"Score Error (99,9%)\",\"Unit\",\"Param: numberOfPersons\"\n");
            writer.flush();

            for (var n = MIN_PERSONS; n <= MAX_PERSONS; n += INITIAL_STEP_SIZE) {
                final var persons = n;
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
        println("Running benchmark for " + persons + " persons...");
        try {
            var tmp = Files.createTempFile("jmh-", ".csv");

            var opt = new OptionsBuilder()
                    .include(BenchmarkConfig.class.getName().replace(".", "\\."))
                    .param("numberOfPersons", String.valueOf(persons))
                    .forks(1)
                    .warmupIterations(0)
                    .measurementIterations(ITERATIONS)
                    .resultFormat(ResultFormatType.CSV)
                    .result(tmp.toString())
                    .build();

            PersonalDataGenerator.generate(persons);

            new Runner(opt).run();

            String lastLine = null;
            try (var reader = newBufferedReader(tmp)) {
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

            deleteIfExists(tmp);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        println("Benchmark for " + persons + " persons completed.");
    }
}
