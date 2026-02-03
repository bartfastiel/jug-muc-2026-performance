package benchmark;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkRunner {

    static void main() throws Exception {
        new Runner(
                new OptionsBuilder()
                        .include(BenchmarkConfig.class.getName().replace(".", "\\."))
                        .param("numberOfPersons", String.valueOf(5_000_000))
                        .build()
        ).run();
    }
}
