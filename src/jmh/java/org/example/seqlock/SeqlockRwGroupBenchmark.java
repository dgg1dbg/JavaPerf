package org.example.seqlock;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.openhft.affinity.Affinity;
import org.HdrHistogram.Histogram;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
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
public class SeqlockRwGroupBenchmark {
    private static final int WRITER_CPU = Integer.getInteger("seqlock.writer.cpu", -1);
    private static final int READER_CPU = Integer.getInteger("seqlock.reader.cpu", -1);
    private static final ThreadLocal<Integer> PINNED_CPU = ThreadLocal.withInitial(() -> Integer.MIN_VALUE);

    private static void pinIfRequested(int cpu) {
        if (cpu < 0) {
            return;
        }
        final int current = PINNED_CPU.get();
        if (current == cpu) {
            return;
        }
        // AffinityThreadFactory may have already bound this thread; override target CPU directly.
        Affinity.setAffinity(cpu);
        PINNED_CPU.set(cpu);
    }

    public static class OffHeapState extends GroupState {
        @Override
        protected String implName() {
            return "offheap";
        }

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

    public static class HeapNaiveState extends GroupState {
        @Override
        protected String implName() {
            return "heap-naive";
        }

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

    public static class HeapAlignedState extends GroupState {
        @Override
        protected String implName() {
            return "heap-aligned";
        }

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

    @State(Scope.Group)
    public abstract static class GroupState {
        private static final int SAMPLE_MASK = 1023;
        private static final int FIXED_ID = 0;

        @Param({"256"})
        public int numSlots;

        @Param({"false"})
        public boolean verifyAlignment;

        @Param({"false", "true"})
        public boolean measureAge;

        protected SlotTable64 table;
        private int[] lastSeqById;
        private final Snapshot64 writerSnapshot = new Snapshot64();
        private final Snapshot64 readerSnapshot = new Snapshot64();
        private Histogram ageHist;
        private final AtomicBoolean agePrinted = new AtomicBoolean(false);
        private int sampleCounter;
        private int counter;

        protected abstract String implName();

        protected abstract SlotTable64 createTable(int numSlots);

        protected void verifyAlignment(SlotTable64 table) {
            // Optional per-implementation diagnostic.
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
            lastSeqById = new int[numSlots];
            lastSeqById[FIXED_ID] = table.seq(FIXED_ID);

            counter = 1;
            if (verifyAlignment) {
                verifyAlignment(table);
            }

            if (measureAge) {
                ageHist = new Histogram(1L, TimeUnit.SECONDS.toNanos(60), 3);
            }
        }

        @Setup(Level.Iteration)
        public void setupIteration() {
            sampleCounter = 0;
            agePrinted.set(false);
            if (measureAge) {
                if (ageHist == null) {
                    ageHist = new Histogram(1L, TimeUnit.SECONDS.toNanos(60), 3);
                }
                ageHist.reset();
            } else {
                ageHist = null;
            }
        }

        int writeOne() {
            final int id = FIXED_ID;
            final int c = ++counter;
            fillSnapshot(writerSnapshot, id, c);
            if (measureAge) {
                writerSnapshot.long0 = System.nanoTime();
            }
            table.write(id, writerSnapshot);
            return c;
        }

        long readOne() {
            long sum = 0L;
            final int id = FIXED_ID;
            if (table.tryLoad(id, readerSnapshot)) {
                lastSeqById[id] = readerSnapshot.seq;
                sum += readerSnapshot.int0;
                sum += readerSnapshot.int1;
                sum += readerSnapshot.int2;
                sum += readerSnapshot.int3;
                sum += readerSnapshot.int4;
                sum += readerSnapshot.int5;
                sum += readerSnapshot.int6;
                sum += readerSnapshot.int7;
                sum += readerSnapshot.int8;
                sum += readerSnapshot.int9;
                sum += readerSnapshot.int10;
                sum += readerSnapshot.int11;
                sum += readerSnapshot.long0;
                sum += readerSnapshot.lastUpdateType;
                sum += readerSnapshot.seq;
                if (measureAge) {
                    final long age = System.nanoTime() - readerSnapshot.long0;
                    sum += age;
                    if (((++sampleCounter) & SAMPLE_MASK) == 0) {
                        ageHist.recordValue(age);
                    }
                }
            }
            return sum;
        }

        @org.openjdk.jmh.annotations.TearDown(Level.Iteration)
        public void tearDownIteration() {
            if (!measureAge || ageHist == null) {
                return;
            }
            if (!agePrinted.compareAndSet(false, true)) {
                return;
            }

            final long samples = ageHist.getTotalCount();
            final long p50 = ageHist.getValueAtPercentile(50.0);
            final long p99 = ageHist.getValueAtPercentile(99.0);
            final long p999 = ageHist.getValueAtPercentile(99.9);
            System.out.printf(
                    Locale.ROOT,
                    "[age] impl=%s numSlots=%d fixedId=%d p50=%dns p99=%dns p99.9=%dns samples=%d%n",
                    implName(),
                    numSlots,
                    FIXED_ID,
                    p50,
                    p99,
                    p999,
                    samples
            );
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

    @Group("offheap_rw")
    @GroupThreads(1)
    @Benchmark
    public int offheapWriter(OffHeapState s) {
        pinIfRequested(WRITER_CPU);
        return s.writeOne();
    }

    @Group("offheap_rw")
    @GroupThreads(1)
    @Benchmark
    public long offheapReader(OffHeapState s) {
        pinIfRequested(READER_CPU);
        return s.readOne();
    }

    @Group("heap_naive_rw")
    @GroupThreads(1)
    @Benchmark
    public int heapNaiveWriter(HeapNaiveState s) {
        pinIfRequested(WRITER_CPU);
        return s.writeOne();
    }

    @Group("heap_naive_rw")
    @GroupThreads(1)
    @Benchmark
    public long heapNaiveReader(HeapNaiveState s) {
        pinIfRequested(READER_CPU);
        return s.readOne();
    }

    @Group("heap_aligned_rw")
    @GroupThreads(1)
    @Benchmark
    public int heapAlignedWriter(HeapAlignedState s) {
        pinIfRequested(WRITER_CPU);
        return s.writeOne();
    }

    @Group("heap_aligned_rw")
    @GroupThreads(1)
    @Benchmark
    public long heapAlignedReader(HeapAlignedState s) {
        pinIfRequested(READER_CPU);
        return s.readOne();
    }
}
