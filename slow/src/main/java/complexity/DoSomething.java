package complexity;

import lombok.SneakyThrows;

public class DoSomething {
    @SneakyThrows
    public static void doSomething() {
        Thread.sleep(1);
    }

    /// # Konstante Komplexität
    /// **O(1)**
    ///
    /// Die Laufzeit ist unabhängig von der Größe der Eingabe.
    ///
    /// | n        | Operationen |
    /// |----------|-------------|
    /// | 1        | 1           |
    /// | 10       | 1           |
    /// | 100      | 1           |
    ///
    public static void runConstant(int n) {
        doSomething();
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
    public static void runLinear(int n) {
        for (int i = 0; i < n; i++) {
            doSomething();
        }
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
    public static void runLogarithmic(int n) {
        while (n > 1) {
            n = n / 2;
            doSomething();
        }
    }

    /// # Linearithmische Komplexität
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
    public static void runNLogN(int n) {
        for (int i = 0; i < n; i++) {
            int m = n;
            while (m > 1) {
                m = m / 2;
                doSomething();
            }
        }
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
    public static void runQuadratic(int n) {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                doSomething();
            }
        }
    }

    /// # Exponentielle Komplexität
    /// **O(2ⁿ)**
    ///
    /// Extrem schlecht skalierend, nur für sehr kleine n praktikabel.
    ///
    /// | n        | Operationen |
    /// |----------|-------------|
    /// | 1        | 2           |
    /// | 10       | 1 024       |
    /// | 20       | 1 048 576   |
    ///
    public static int runExponential(int n) {
        if (n <= 1) {
            return n;
        }
        return runExponential(n - 1) + runExponential(n - 2);
    }
}
