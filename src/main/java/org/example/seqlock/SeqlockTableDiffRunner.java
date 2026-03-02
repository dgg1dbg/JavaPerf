package org.example.seqlock;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;
import net.openhft.affinity.Affinity;
import net.openhft.affinity.AffinityStrategies;
import net.openhft.affinity.AffinityThreadFactory;
import org.HdrHistogram.Histogram;

/**
 * Non-JMH runner for two-thread seqlock diff measurement.
 *
 * Writer writes publish timestamp (nanoTime) into long0.
 * Reader records diff = now - long0 only for successful tryLoad().
 */
public final class SeqlockTableDiffRunner {
    private static final int FIXED_ID = 0;

    private SeqlockTableDiffRunner() {
    }

    public static void main(String[] args) throws InterruptedException {
        final Config cfg = Config.fromSystemProperties();
        printConfig(cfg);

        runCase("offheap", OffHeapAlignedTable::new, cfg);
        runCase("heap-naive", HeapByteNaiveTable::new, cfg);
        runCase("heap-aligned", HeapByteAlignedStartTable::new, cfg);
        runCase("object-array", ObjectArraySnapshotTable::new, cfg);
    }

    private static void runCase(String name, IntFunction<SlotTable64> tableFactory, Config cfg) throws InterruptedException {
        if (cfg.warmupSeconds > 0) {
            executePhase(name, tableFactory.apply(cfg.numSlots), cfg, false, cfg.warmupSeconds);
        }

        final Result result = executePhase(name, tableFactory.apply(cfg.numSlots), cfg, true, cfg.measureSeconds);
        if (result.samples == 0L) {
            System.out.printf(Locale.ROOT, "[%s] no successful tryLoad samples%n", name);
            return;
        }

        System.out.printf(
                Locale.ROOT,
                "[%s] samples=%d avg=%.1fns p50=%dns p99=%dns p99.9=%dns%n",
                result.name,
                result.samples,
                result.avgNs,
                result.p50Ns,
                result.p99Ns,
                result.p999Ns
        );
    }

    private static Result executePhase(
            String name,
            SlotTable64 table,
            Config cfg,
            boolean record,
            int seconds
    ) throws InterruptedException {
        final AtomicBoolean stop = new AtomicBoolean(false);
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(2);
        final ReaderStats stats = record ? new ReaderStats() : null;

        final Thread writer = new AffinityThreadFactory(
                "writer-" + name,
                true,
                AffinityStrategies.ANY
        ).newThread(() -> {
            try {
                pinCpu("writer", cfg.writerCpu, cfg.logPin);
                final Snapshot64 snapshot = new Snapshot64();
                int counter = 0;

                ready.countDown();
                await(start);
                while (!stop.get()) {
                    counter++;
                    fillSnapshot(snapshot, FIXED_ID, counter);
                    snapshot.long0 = System.nanoTime();
                    table.write(FIXED_ID, snapshot);
                }
            } finally {
                done.countDown();
            }
        });

        final Thread reader = new AffinityThreadFactory(
                "reader-" + name,
                true,
                AffinityStrategies.ANY
        ).newThread(() -> {
            try {
                pinCpu("reader", cfg.readerCpu, cfg.logPin);
                final Snapshot64 snapshot = new Snapshot64();

                ready.countDown();
                await(start);
                while (!stop.get()) {
                    if (!table.tryLoad(FIXED_ID, snapshot)) {
                        continue;
                    }
                    if (record) {
                        final long diff = System.nanoTime() - snapshot.long0;
                        if (diff >= 0L) {
                            stats.record(diff);
                        }
                    }
                }
            } finally {
                done.countDown();
            }
        });

        writer.start();
        reader.start();
        ready.await();
        start.countDown();

        final long endNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < endNs) {
            Thread.sleep(10);
        }

        stop.set(true);
        done.await();
        writer.join();
        reader.join();

        return record ? stats.toResult(name) : Result.empty(name);
    }

    private static void pinCpu(String role, int cpu, boolean logPin) {
        if (cpu < 0) {
            return;
        }
        Affinity.setAffinity(cpu);
        if (logPin) {
            final int actual = Affinity.getCpu();
            final String status = actual == cpu ? "OK" : "MISMATCH";
            System.out.printf(
                    Locale.ROOT,
                    "[pin] role=%s requestedCpu=%d actualCpu=%d status=%s thread=%s%n",
                    role,
                    cpu,
                    actual,
                    status,
                    Thread.currentThread().getName()
            );
        }
    }

    private static void await(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
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
        dst.lastUpdateType = counter & 3;
        dst.seq = 0;
    }

    private static void printConfig(Config cfg) {
        System.out.printf(
                Locale.ROOT,
                "SeqlockTableDiffRunner: numSlots=%d warmup=%ds measure=%ds writerCpu=%d readerCpu=%d%n",
                cfg.numSlots,
                cfg.warmupSeconds,
                cfg.measureSeconds,
                cfg.writerCpu,
                cfg.readerCpu
        );
    }

    private record Config(
            int numSlots,
            int warmupSeconds,
            int measureSeconds,
            int writerCpu,
            int readerCpu,
            boolean logPin
    ) {
        static Config fromSystemProperties() {
            return new Config(
                    Integer.getInteger("seqlock.diff.numSlots", 256),
                    Integer.getInteger("seqlock.diff.warmupSec", 3),
                    Integer.getInteger("seqlock.diff.measureSec", 10),
                    Integer.getInteger("seqlock.writer.cpu", -1),
                    Integer.getInteger("seqlock.reader.cpu", -1),
                    Boolean.getBoolean("seqlock.pin.log")
            );
        }
    }

    private static final class ReaderStats {
        private final Histogram histogram = new Histogram(1, TimeUnit.DAYS.toNanos(1), 3);
        private long samples;
        private long totalNs;

        void record(long diffNs) {
            final long recorded = Math.max(1L, diffNs);
            histogram.recordValue(recorded);
            samples++;
            totalNs += diffNs;
        }

        Result toResult(String name) {
            return new Result(
                    name,
                    samples,
                    samples == 0L ? 0.0 : totalNs / samples,
                    histogram.getValueAtPercentile(50.0),
                    histogram.getValueAtPercentile(99.0),
                    histogram.getValueAtPercentile(99.9)
            );
        }
    }

    private record Result(
            String name,
            long samples,
            double avgNs,
            long p50Ns,
            long p99Ns,
            long p999Ns
    ) {
        static Result empty(String name) {
            return new Result(name, 0L, 0.0, 0L, 0L, 0L);
        }
    }
}
