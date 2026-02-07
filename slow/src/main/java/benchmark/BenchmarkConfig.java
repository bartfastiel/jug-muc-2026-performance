package benchmark;

import generator.PersonalDataGenerator;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
public class BenchmarkConfig {

    @Param("1") // wird vom Runner überschrieben
    public int numberOfPersons;

    @Setup(Level.Trial)
    public void prepareData() throws IOException {
        //PersonalDataGenerator.generate(numberOfPersons);
    }

    @Benchmark
    public void runMainProgram() throws IOException {
        complexity.blinear.Main.run(numberOfPersons);
    }
}
