package complexity.fexponential.equadratic;

import static complexity.DoSomething.doSomething;

public class Main {
    void main() {
        run(100);
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
    public static int run(int n) {
        if (n <= 1) {
            return n;
        }
        return run(n - 1) + run(n - 2);
    }
}