package golf.tweede;

import golf.tweede.gen.java_interop_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class PanamaInterface {

    private static String segmentToString(MemorySegment segment) {
        String string = segment.getString(0);
        java_interop_h.freeString(segment);
        return string;
    }

    public static String doubleToStringRust(double value) {
        return segmentToString(java_interop_h.doubleToStringRust(value));
    }

    public static String doubleToStringRyu(double value) {
        return segmentToString(java_interop_h.doubleToStringRyu(value));
    }

    public static String doubleArrayToStringRyu(double[] array) {
        String output;
        try (Arena offHeap = Arena.ofConfined()) {
            // allocate off-heap memory for input array
            MemorySegment segment = offHeap.allocateFrom(ValueLayout.JAVA_DOUBLE, array);
            output = segmentToString(java_interop_h.doubleArrayToStringRyu(segment, array.length));
        } // release memory for input array
        return output;
    }

    public static void main(String[] args) {
        System.out.println(doubleToStringRust(0.123)); // this prints "0.123"!
        System.out.println(doubleToStringRyu(0.123)); // this prints "0.123"!
        System.out.println(doubleArrayToStringRyu(new double[] {0.123})); // this prints "0.123"!
    }
}
