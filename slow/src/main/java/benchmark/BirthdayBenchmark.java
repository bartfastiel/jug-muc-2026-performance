package benchmark;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
public class BirthdayBenchmark {

    @Param("1") // wird vom Runner überschrieben
    public int numberOfPersons;

    @Benchmark
    public void runMainProgram() throws Exception {
        //solution.juliett.Main.main();
        complexity.alinear.Main.run(numberOfPersons);
    }
}
