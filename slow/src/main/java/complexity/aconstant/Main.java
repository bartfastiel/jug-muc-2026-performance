package complexity.aconstant;

import static complexity.DoSomething.doSomething;
import static complexity.blinear.Main.run;

public class Main {
    void main() {
        run(100);
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
    public static void run(int n) {
        doSomething();
    }
}