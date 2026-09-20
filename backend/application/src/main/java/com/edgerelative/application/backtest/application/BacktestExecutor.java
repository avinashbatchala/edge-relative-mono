package com.edgerelative.application.backtest.application;

import java.util.concurrent.ExecutorService;

/**
 * Dedicated backtest worker wrapper. Deliberately not typed as {@link ExecutorService} so it does
 * not collide with other executor beans in the context.
 */
public final class BacktestExecutor implements AutoCloseable {

    private final ExecutorService delegate;

    public BacktestExecutor(ExecutorService delegate) {
        this.delegate = delegate;
    }

    public void submit(Runnable task) {
        delegate.submit(task);
    }

    @Override
    public void close() {
        delegate.shutdownNow();
    }
}
