package golf.tweede;

import java.nio.file.Path;
import java.nio.file.Paths;

public class JniInterface {
    public static native String doubleToStringRust(double v);

    public static native String doubleToStringRyu(double v);

    public static native String doubleArrayToStringRyu(double[] v);

    static {
        Path p = Paths.get("../rust/target/release/libjava_interop.so");
        System.load(p.toAbsolutePath().toString()); // load library
    }

    public static void main(String[] args) {
        System.out.println(doubleToStringRust(0.123)); // this prints "0.123"!
        System.out.println(doubleToStringRyu(0.123)); // this prints "0.123"!
        System.out.println(doubleArrayToStringRyu(new double[] {0.123})); // this prints "0.123"!
    }
}
