package golf.tweede;

import jnr.ffi.LibraryLoader;
import jnr.ffi.Pointer;

public class JnrInterface {
    public interface RustLib {
        Pointer doubleToStringRust(double value);

        Pointer doubleToStringRyu(double value);

        Pointer doubleArrayToStringRyu(double[] array, int len);

        void freeString(Pointer string);
    }

    private static final RustLib lib;

    static {
        System.setProperty("jnr.ffi.library.path", "../rust/target/release");
        lib = LibraryLoader.create(RustLib.class).load("java_interop"); // load library
    }

    /**
     * Grab a string from a pointer, and free the original Rust string from the
     * pointer
     *
     * @param pointer which points a string
     * @return the string
     */
    private static String pointerToString(Pointer pointer) {
        String string = pointer.getString(0);
        lib.freeString(pointer);
        return string;
    }

    public static String doubleToStringRust(double value) {
        return pointerToString(lib.doubleToStringRust(value));
    }

    public static String doubleToStringRyu(double value) {
        return pointerToString(lib.doubleToStringRyu(value));
    }

    public static String doubleArrayToStringRyu(double[] array) {
        return pointerToString(lib.doubleArrayToStringRyu(array, array.length));
    }

    public static void main(String[] args) {
        System.out.println(doubleToStringRust(0.123)); // this prints "0.123"!
        System.out.println(doubleToStringRyu(0.123)); // this prints "0.123"!
        System.out.println(doubleArrayToStringRyu(new double[] {0.123})); // this prints "0.123"!
    }
}
