package benchmark;

import org.openjdk.jmh.annotations.*;
import slow.Slow;

import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@State(Scope.Benchmark)
public class BirthdayBenchmark {

    @Param("1") // wird vom Runner überschrieben
    public int numberOfPersons;

    @Benchmark
    public void runMainProgram() throws Exception {
        Slow.main();
    }
}
