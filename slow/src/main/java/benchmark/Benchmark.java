package benchmark;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
public class Benchmark {

    @Param("1") // wird vom Runner überschrieben
    public int numberOfPersons;

    @org.openjdk.jmh.annotations.Benchmark
    public void runMainProgram() throws Exception {
        //solution.juliett.Main.main();
        complexity.aconstant.Main.run(numberOfPersons);
    }
}
