package com.edgerelative.application.backtest.application;

import com.edgerelative.application.backtest.engine.BacktestEngine;
import com.edgerelative.application.feature.engine.FeatureEngine;
import com.edgerelative.application.history.query.HistoricalDataReader;
import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.application.risk.domain.RiskEvaluator;
import com.edgerelative.application.strategy.domain.StrategyEngine;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BacktestConfiguration {

    @Bean
    public BacktestEngine backtestEngine(
            HistoricalDataReader reader,
            FeatureEngine featureEngine,
            StrategyEngine strategyEngine,
            RiskEvaluator riskEvaluator,
            NseTradingCalendar calendar) {
        return new BacktestEngine(reader, featureEngine, strategyEngine, riskEvaluator, calendar);
    }

    /**
     * Bounded worker pool. Backtests are CPU/RAM-heavy batch jobs; a single worker serialises them so
     * two long replays cannot run concurrently and saturate the machine. Cancellation still works
     * (the running task polls the run status) and further submissions queue.
     */
    @Bean(destroyMethod = "close")
    public BacktestExecutor backtestExecutor() {
        return new BacktestExecutor(Executors.newFixedThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "backtest-worker");
            thread.setDaemon(true);
            return thread;
        }));
    }
}
