package benchmark;

import generator.PersonalDataGenerator;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkRunner {

    static void main() throws Exception {
        var n = 100_000_000;
        PersonalDataGenerator.generate(n);
        new Runner(
                new OptionsBuilder()
                        .include(BirthdayBenchmark.class.getName().replace(".", "\\."))
                        .param("numberOfPersons", String.valueOf(n))
                        .build()
        ).run();
    }
}
