package com.edgerelative.application.feature.policy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Virtual-thread executor for the observational dashboard fan-out.
 *
 * <p>Wrapped in a named type rather than exposed as a bare {@code ExecutorService} bean so it does
 * not compete with the broker adapter's bulk executor for by-type injection. Each watchlist
 * instrument is an independent set of blocking JDBC reads — the natural virtual-thread workload;
 * the caller bounds concurrency.
 */
public final class FeatureDashboardExecutor implements AutoCloseable {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ExecutorService executor() {
        return executor;
    }

    @Override
    public void close() {
        executor.close();
    }
}
