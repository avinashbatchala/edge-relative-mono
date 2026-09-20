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

    /** Isolated worker pool; runs are independent and must not block request threads. */
    @Bean(destroyMethod = "close")
    public BacktestExecutor backtestExecutor() {
        return new BacktestExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }
}
