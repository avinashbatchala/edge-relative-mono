package com.edgerelative.application.backtest.application;

import java.util.List;
import java.util.Map;

/**
 * A bounded batch of backtests sharing one base request but differing in inline strategy parameters.
 * Used by the research layer to sweep configurations; each configuration becomes its own
 * reproducible run (the parameter set is hashed into the run key).
 */
public record BacktestSweepRequest(
        BacktestRunRequest base,
        List<Map<String, Object>> configs,
        Integer maxConfigs) {
}
