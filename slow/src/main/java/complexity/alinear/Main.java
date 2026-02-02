package complexity.alinear;

import static complexity.DoSomething.doSomething;

public class Main {
    void main() {
        run(100);
    }

    public static void run(int n) {
        for (int i = 0; i < n; i++) {
            doSomething();
        }
    }
}