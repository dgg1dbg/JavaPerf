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
@Fork(value = 3)
@State(Scope.Group)
public class PingPongControlBaselineBenchmark {

    public final AtomicBoolean flag = new AtomicBoolean();

    @Setup(Level.Iteration)
    public void setupIteration() { flag.set(false); }

    @State(Scope.Thread)
    public static class PinState {
        int cpu;
        boolean pinned;

        @Setup(Level.Trial)
        public void setup() {
            // cpu is injected by JMH params or system props
        }

        void pinOnce(String role) {
            if (cpu < 0 || pinned) return;
            Affinity.setAffinity(cpu);
            pinned = true;
        }
    }

    @State(Scope.Thread)
    public static class PingPin extends PinState {
        { cpu = Integer.getInteger("pingpong.ping.cpu", -1); }
        @Setup(Level.Trial) public void pin() { pinOnce("ping"); }
    }

    @State(Scope.Thread)
    public static class PongPin extends PinState {
        { cpu = Integer.getInteger("pingpong.pong.cpu", -1); }
        @Setup(Level.Trial) public void pin() { pinOnce("pong"); }
    }

    @Benchmark @Group("pingpong") @GroupThreads(1)
    public void ping(Control c, PingPin p) {
        while (!c.stopMeasurement && !flag.compareAndSet(false, true)) {
        }
    }

    @Benchmark @Group("pingpong") @GroupThreads(1)
    public void pong(Control c, PongPin p) {
        while (!c.stopMeasurement && !flag.compareAndSet(true, false)) {
        }
    }
}
