package org.example.timer;

import static java.util.Objects.requireNonNull;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Fork(5)
@State(Scope.Thread)
public class TimersBenchmark {
    private long lastValue;

    @Benchmark
    public long latencyNanoTime() {
        return System.nanoTime();
    }

    @Benchmark
    public long latencyCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Benchmark
    public long granularityNanoTime() {
        long cur;
        do {
            cur = System.nanoTime();
        } while (cur == lastValue);
        lastValue = cur;
        return cur;
    }

    @Benchmark
    public long granularityCurrentTimeMillis() {
        long cur;
        do {
            cur = System.currentTimeMillis();
        } while (cur == lastValue);
        lastValue = cur;
        return cur;
    }

    public static void main(String[] args) throws RunnerException, InterruptedException {
        final PrintWriter pw = new PrintWriter(System.out, true);

        pw.println("---- 8< (cut here) -----------------------------------------");
        pw.println(System.getProperty("java.runtime.name") + ", " + System.getProperty("java.runtime.version"));
        pw.println(System.getProperty("java.vm.name") + ", " + System.getProperty("java.vm.version"));
        pw.println(System.getProperty("os.name") + ", " + System.getProperty("os.version") + ", " + System.getProperty("os.arch"));

        final int cpus = figureOutHotCPUs(pw);
        runWith(pw, 1);
        runWith(pw, Math.max(1, cpus / 2));
        runWith(pw, cpus);

        pw.println();
        pw.println("---- 8< (cut here) -----------------------------------------");
        pw.flush();
    }

    private static void runWith(PrintWriter pw, int threads, String... jvmOpts) throws RunnerException {
        pw.println();
        pw.println("Running with " + threads + " threads:");

        final Options opts = new OptionsBuilder()
                .include(".*" + TimersBenchmark.class.getSimpleName() + ".*")
                .threads(threads)
                .verbosity(VerboseMode.SILENT)
                .jvmArgs(jvmOpts)
                .build();

        final Collection<RunResult> results = new Runner(opts).run();
        for (RunResult r : results) {
            final String name = simpleName(r.getParams().getBenchmark());
            final double score = r.getPrimaryResult().getScore();
            final double scoreError = r.getPrimaryResult().getStatistics().getMeanErrorAt(0.99);
            pw.printf("%35s: %11.3f ± %10.3f ns%n", name, score, scoreError);
        }
    }

    private static String simpleName(String qName) {
        final int lastDot = requireNonNull(qName).lastIndexOf('.');
        return lastDot < 0 ? qName : qName.substring(lastDot + 1);
    }

    private static int figureOutHotCPUs(PrintWriter pw) throws InterruptedException {
        final ExecutorService service = Executors.newCachedThreadPool();
        pw.println();
        pw.print("Burning up to figure out the exact CPU count...");
        pw.flush();

        final int warmupTimeMillis = 1000;
        long lastChange = System.currentTimeMillis();
        final List<Future<?>> futures = new ArrayList<>();
        futures.add(service.submit(new BurningTask()));

        pw.print(".");

        int max = 0;
        while (System.currentTimeMillis() - lastChange < warmupTimeMillis) {
            final int cur = Runtime.getRuntime().availableProcessors();
            if (cur > max) {
                pw.print(".");
                max = cur;
                lastChange = System.currentTimeMillis();
                futures.add(service.submit(new BurningTask()));
            }
        }

        for (Future<?> f : futures) {
            pw.print(".");
            f.cancel(true);
        }

        service.shutdown();
        service.awaitTermination(1, TimeUnit.DAYS);
        pw.println(" done!");

        return max;
    }

    public static class BurningTask implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.onSpinWait();
            }
        }
    }
}
