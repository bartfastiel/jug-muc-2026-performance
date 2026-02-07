package benchmark;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkRunner {

    static void main() throws Exception {
        new Runner(
                new OptionsBuilder()
                        .include(BenchmarkConfig.class.getName().replace(".", "\\."))
                        .build()
        ).run();
    }
}
