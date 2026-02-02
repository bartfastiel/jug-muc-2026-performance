package complexity.equadratic;

import static complexity.DoSomething.doSomething;

public class Main {
    void main() {
        run(100);
    }

    /// # Quadratische Komplexität
    /// **O(n²)**
    ///
    /// Entsteht meist durch verschachtelte Schleifen.
    ///
    /// | n        | Operationen |
    /// |----------|-------------|
    /// | 1        | 1           |
    /// | 10       | 100         |
    /// | 100      | 10 000      |
    ///
    public static void run(int n) {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                doSomething();
            }
        }
    }
}