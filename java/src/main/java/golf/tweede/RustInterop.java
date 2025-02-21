package golf.tweede;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.stream.DoubleStream;

public class RustInterop {
    @State(Scope.Benchmark)
    public static class BenchmarkState {
        public double value = Math.PI;
        public double[] array = new double[1_000_000];

        @Setup
        public void setup() {
            for (int i = 0; i < array.length; i++) {
                array[i] = i / 12f;
            }
        }
    }

    // Benchmarks

    @Benchmark
    public String single_java(BenchmarkState state) {
        return Double.toString(state.value);
    }

    @Benchmark
    public String array_java(BenchmarkState state) {
        return String.join(" ", DoubleStream.of(state.array).mapToObj(Double::toString).toArray(String[]::new));
    }

    @Benchmark
    public String single_jni_rust(BenchmarkState state) {
        return JniInterface.doubleToStringRust(state.value);
    }

    @Benchmark
    public String single_jni_ryu(BenchmarkState state) {
        return JniInterface.doubleToStringRyu(state.value);
    }

    @Benchmark
    public String array_jni_ryu(BenchmarkState state) {
        return JniInterface.doubleArrayToStringRyu(state.array);
    }

    @Benchmark
    public String single_jnr_rust(BenchmarkState state) {
        return JnrInterface.doubleToStringRust(state.value);
    }

    @Benchmark
    public String single_jnr_ryu(BenchmarkState state) {
        return JnrInterface.doubleToStringRyu(state.value);
    }

    @Benchmark
    public String array_jnr_ryu(BenchmarkState state) {
        return JnrInterface.doubleArrayToStringRyu(state.array);
    }

    @Benchmark
    public String single_panama_rust(BenchmarkState state) {
        return PanamaInterface.doubleToStringRust(state.value);
    }

    @Benchmark
    public String single_panama_ryu(BenchmarkState state) {
        return PanamaInterface.doubleToStringRyu(state.value);
    }

    @Benchmark
    public String array_panama_ryu(BenchmarkState state) {
        return PanamaInterface.doubleArrayToStringRyu(state.array);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(RustInterop.class.getSimpleName())
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}
