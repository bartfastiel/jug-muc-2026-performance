package complexity.blinear;

import static complexity.DoSomething.doSomething;

public class Main {
    void main() {
        run(100);
    }

    /// # Lineare Komplexität
    /// **O(n)**
    ///
    /// Die Laufzeit wächst proportional zur Eingabegröße.
    ///
    /// | n        | Operationen |
    /// |----------|-------------|
    /// | 1        | 1           |
    /// | 10       | 10          |
    /// | 100      | 100         |
    ///
    public static void run(int n) {
        for (int i = 0; i < n; i++) {
            doSomething();
        }
    }
}