package org.example.seqlock;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import net.openhft.affinity.Affinity;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
        value = 1,
        jvmArgsAppend = {
                "-Dagrona.disable.bounds.checks=true",
                "-Djmh.executor=CUSTOM",
                "-Djmh.executor.class=org.example.seqlock.SeqlockAffinityExecutor",
                "--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED",
                "--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED",
                "-XX:+AlwaysPreTouch"
        }
)
public class SeqlockWriterBenchmark {
    private static final int FIXED_ID = 0;
    private static final int WRITER_CPU = Integer.getInteger("seqlock.writer.cpu", -1);
    private static final boolean LOG_PIN = Boolean.getBoolean("seqlock.pin.log");
    private static final ThreadLocal<Integer> PINNED_CPU = ThreadLocal.withInitial(() -> Integer.MIN_VALUE);

    private static void pinIfRequested(int cpu) {
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
                    "[pin] role=writer requestedCpu=%d actualCpu=%d status=%s thread=%s%n",
                    cpu,
                    actualCpu,
                    status,
                    Thread.currentThread().getName()
            );
        }
    }

    @State(Scope.Thread)
    public abstract static class WriterState {
        @Param({"256"})
        public int numSlots;

        @Param({"false"})
        public boolean verifyAlignment;

        private SlotTable64 table;
        private final Snapshot64 snapshot = new Snapshot64();
        private int counter;

        protected abstract SlotTable64 createTable(int numSlots);

        protected void verifyAlignment(SlotTable64 table) {
            // Optional diagnostics per impl.
        }

        @Setup(Level.Trial)
        public void setup() {
            if (!isPowerOfTwo(numSlots)) {
                throw new IllegalArgumentException("numSlots must be a power of two: " + numSlots);
            }
            if (FIXED_ID >= numSlots) {
                throw new IllegalArgumentException("FIXED_ID out of range for numSlots=" + numSlots);
            }

            table = createTable(numSlots);
            counter = 1;
            if (verifyAlignment) {
                verifyAlignment(table);
            }
        }

        int writeOne() {
            final int c = ++counter;
            fillSnapshot(snapshot, FIXED_ID, c);
            snapshot.long0 = System.nanoTime();
            table.write(FIXED_ID, snapshot);
            return c;
        }
    }

    public static class OffHeapState extends WriterState {
        @Override
        protected SlotTable64 createTable(int numSlots) {
            return new OffHeapAlignedTable(numSlots);
        }

        @Override
        protected void verifyAlignment(SlotTable64 table) {
            final OffHeapAlignedTable t = (OffHeapAlignedTable) table;
            if (!t.slot0Aligned64()) {
                throw new IllegalStateException("OffHeap slot0 is not 64B aligned");
            }
            System.out.println("[verify] offheap slot0Aligned64=true");
        }
    }

    public static class HeapNaiveState extends WriterState {
        @Override
        protected SlotTable64 createTable(int numSlots) {
            return new HeapByteNaiveTable(numSlots);
        }

        @Override
        protected void verifyAlignment(SlotTable64 table) {
            final HeapByteNaiveTable t = (HeapByteNaiveTable) table;
            System.out.println(
                    "[verify] heap-naive start="
                            + t.startOffset()
                            + ", slot0Aligned64="
                            + t.slot0Aligned64()
                            + ", slot0Address=0x"
                            + Long.toHexString(t.slot0Address())
            );
        }
    }

    public static class HeapAlignedState extends WriterState {
        @Override
        protected SlotTable64 createTable(int numSlots) {
            return new HeapByteAlignedStartTable(numSlots);
        }

        @Override
        protected void verifyAlignment(SlotTable64 table) {
            final HeapByteAlignedStartTable t = (HeapByteAlignedStartTable) table;
            final long slot0 = t.slot0Address();
            final boolean aligned = (slot0 & 63L) == 0L;
            if (!aligned) {
                throw new IllegalStateException(
                        "Aligned-start table slot0 is not 64B aligned; start=" + t.startOffset()
                );
            }
            System.out.println(
                    "[verify] heap-aligned start="
                            + t.startOffset()
                            + ", slot0Aligned64=true"
                            + ", rawDataAddress=0x"
                            + Long.toHexString(t.rawDataAddress())
                            + ", slot0Address=0x"
                            + Long.toHexString(slot0)
            );
        }
    }

    @Benchmark
    public int offheapWriterOnly(OffHeapState s) {
        pinIfRequested(WRITER_CPU);
        return s.writeOne();
    }

    @Benchmark
    public int heapNaiveWriterOnly(HeapNaiveState s) {
        pinIfRequested(WRITER_CPU);
        return s.writeOne();
    }

    @Benchmark
    public int heapAlignedWriterOnly(HeapAlignedState s) {
        pinIfRequested(WRITER_CPU);
        return s.writeOne();
    }

    private static boolean isPowerOfTwo(int v) {
        return v > 0 && (v & (v - 1)) == 0;
    }

    private static void fillSnapshot(Snapshot64 dst, int id, int counter) {
        dst.int0 = counter;
        dst.int1 = counter + 1;
        dst.int2 = counter + 2;
        dst.int3 = counter + 3;
        dst.int4 = counter + id;
        dst.int5 = counter ^ id;
        dst.int6 = counter * 3;
        dst.int7 = counter * 5;
        dst.int8 = counter * 7;
        dst.int9 = counter * 11;
        dst.int10 = counter * 13;
        dst.int11 = counter * 17;
        dst.long0 = (((long) counter) << 32) ^ (id & 0xFFFF_FFFFL);
        dst.lastUpdateType = counter & 3;
    }
}
