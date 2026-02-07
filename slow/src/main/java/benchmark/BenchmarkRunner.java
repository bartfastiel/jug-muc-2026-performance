package benchmark;

import generator.PersonalDataGenerator;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkRunner {

    static void main() throws Exception {
        var persons = 500;
        PersonalDataGenerator.generate(persons);
        new Runner(
                new OptionsBuilder()
                        .include(BenchmarkConfig.class.getName().replace(".", "\\."))
                        .param("numberOfPersons", String.valueOf(persons))
                        .build()
        ).run();
    }
}
