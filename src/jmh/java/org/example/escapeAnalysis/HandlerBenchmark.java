package org.example.escapeAnalysis;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Thread)
public class HandlerBenchmark {
    private Handler handler;
    private int userId;
    private int requestId;

    @Setup
    public void setup() {
        handler = new Handler();
        userId = 1;
        requestId = 7;
    }

    @Benchmark
    public int handleWithReuse() {
        nextInput();
        return handler.handleWithReuse(userId, requestId);
    }

    @Benchmark
    public int handleWithEA() {
        nextInput();
        return handler.handleWithEA(userId, requestId);
    }

    @Benchmark
    public int handleWithDirectArgs() {
        nextInput();
        return handler.handleWithDirectArgs(userId, requestId);
    }

    private void nextInput() {
        userId = (userId * 1664525) + 1013904223;
        requestId = (requestId * 1103515245) + 12345;
    }
}
