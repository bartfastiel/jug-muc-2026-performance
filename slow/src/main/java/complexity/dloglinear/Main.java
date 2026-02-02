package complexity.dloglinear;

import static complexity.DoSomething.doSomething;

public class Main {
    void main() {
        run(100);
    }

    /// # Loglineare, (auch Quasilineare oder Linearithmische) Komplexität
    /// **O(n log n)**
    ///
    /// Häufig bei effizienten Sortieralgorithmen.
    ///
    /// | n        | Operationen |
    /// |----------|-------------|
    /// | 1        | 0           |
    /// | 10       | 33          |
    /// | 100      | 664         |
    ///
    public static void run(int n) {
        for (int i = 0; i < n; i++) {
            int m = n;
            while (m > 1) {
                m = m / 2;
                doSomething();
            }
        }
    }
}