package complexity.clog;

import static complexity.DoSomething.doSomething;

public class Main {
    void main() {
        run(100);
    }

    /// # Logarithmische Komplexität
    /// **O(log n)**
    ///
    /// Typisch für Divide-and-Conquer, z. B. Binary Search.
    ///
    /// | n        | Operationen |
    /// |----------|-------------|
    /// | 1        | 0           |
    /// | 10       | 4           |
    /// | 100      | 7           |
    ///
    public static void run(int n) {
        while (n > 1) {
            n = n / 2;
            doSomething();
        }
    }
}