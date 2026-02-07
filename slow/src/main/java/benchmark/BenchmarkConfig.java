package benchmark;

import generator.PersonalDataGenerator;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.openjdk.jmh.annotations.Level.Trial;
import static org.openjdk.jmh.annotations.Mode.SingleShotTime;

@BenchmarkMode(SingleShotTime)
@OutputTimeUnit(MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
public class BenchmarkConfig {

    @Param("500") // wird vom Parallel-Runner überschrieben
    public int numberOfPersons;

    private Path tempFile;

    //@Setup(Trial)
    public void prepareData() throws IOException {
        tempFile = Files.createTempFile(
                "persons-" + numberOfPersons + "-",
                ".csv"
        );
        PersonalDataGenerator.generate(numberOfPersons, tempFile);
    }

    @Benchmark
    public void runMainProgram() throws IOException {
        complexity.aconstant.Main.run(numberOfPersons);
    }

    @TearDown(Trial)
    public void cleanup() throws IOException {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }
}
