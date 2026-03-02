package org.example.seqlock;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.openhft.affinity.Affinity;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Control;

/**
 * Baseline ping-pong benchmark for 2 threads (1 ping + 1 pong).
 * Converted from legacy JMH sample style to current @Benchmark API.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
        value = 1,
        jvmArgsAppend = {
                "-Djmh.executor=CUSTOM",
                "-Djmh.executor.class=org.example.seqlock.SeqlockAffinityExecutor",
                "-XX:+AlwaysPreTouch"
        }
)
@State(Scope.Group)
public class PingPongControlBaselineBenchmark {
    private static final int PING_CPU = Integer.getInteger("pingpong.ping.cpu", -1);
    private static final int PONG_CPU = Integer.getInteger("pingpong.pong.cpu", -1);
    private static final boolean LOG_PIN = Boolean.getBoolean("pingpong.pin.log");
    private static final ThreadLocal<Integer> PINNED_CPU = ThreadLocal.withInitial(() -> Integer.MIN_VALUE);

    public final AtomicBoolean flag = new AtomicBoolean();

    @Setup(Level.Iteration)
    public void setupIteration() {
        flag.set(false);
    }

    @Benchmark
    @Group("pingpong")
    @GroupThreads(1)
    public void ping(Control control) {
        pinIfRequested("ping", PING_CPU);
        while (!control.stopMeasurement && !flag.compareAndSet(false, true)) {
            Thread.onSpinWait();
        }
    }

    @Benchmark
    @Group("pingpong")
    @GroupThreads(1)
    public void pong(Control control) {
        pinIfRequested("pong", PONG_CPU);
        while (!control.stopMeasurement && !flag.compareAndSet(true, false)) {
            Thread.onSpinWait();
        }
    }

    private static void pinIfRequested(String role, int cpu) {
        if (cpu < 0) {
            return;
        }
        final int current = PINNED_CPU.get();
        if (current == cpu) {
            return;
        }
        Affinity.setAffinity(cpu);
        PINNED_CPU.set(cpu);
        if (LOG_PIN) {
            final int actualCpu = Affinity.getCpu();
            final String status = (actualCpu == cpu) ? "OK" : "MISMATCH";
            System.out.printf(
                    Locale.ROOT,
                    "[pin] bench=pingpong role=%s requestedCpu=%d actualCpu=%d status=%s thread=%s%n",
                    role,
                    cpu,
                    actualCpu,
                    status,
                    Thread.currentThread().getName()
            );
        }
    }
}
