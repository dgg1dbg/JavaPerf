package org.example.seqlock;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.openhft.affinity.AffinityStrategies;
import net.openhft.affinity.AffinityThreadFactory;

/**
 * JMH custom executor hook: instantiated via
 * -Djmh.executor=CUSTOM -Djmh.executor.class=org.example.seqlock.SeqlockAffinityExecutor
 */
public final class SeqlockAffinityExecutor extends ThreadPoolExecutor {
    public SeqlockAffinityExecutor(int maxThreads, String prefix) {
        super(
                maxThreads,
                maxThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                newAffinityFactory(prefix)
        );
        prestartAllCoreThreads();
    }

    private static ThreadFactory newAffinityFactory(String prefix) {
        return new AffinityThreadFactory(prefix, true, AffinityStrategies.DIFFERENT_CORE, AffinityStrategies.ANY);
    }
}
