package com.edgerelative.application.backtest.domain;

import com.edgerelative.application.reference.NseTradingCalendar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Metrics from the simulated ledger and a consistently sampled marked-to-market equity series.
 * Undefined metrics are {@code null} with an explanation in {@code notes}; zero is never substituted.
 *
 * <p>Trade aggregation: a completed trade is one position closed once (partial exits are not treated
 * as separate winning trades in this baseline). Win/loss uses net-of-cost P&L. Open positions are
 * reported separately and excluded from win rate.
 */
public final class BacktestMetrics {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private BacktestMetrics() {
    }

    public static Map<String, Object> compute(
            BacktestResult result, BigDecimal startingCapital, double barsPerYear) {
        List<BacktestTrade> completed = result.trades().stream()
                .filter(trade -> !trade.isOpen())
                .toList();
        List<BacktestTrade> open = result.trades().stream().filter(BacktestTrade::isOpen).toList();
        Map<String, String> notes = new LinkedHashMap<>();
        Map<String, Object> metrics = new LinkedHashMap<>();

        BigDecimal finalEquity = result.equityPoints().isEmpty()
                ? startingCapital
                : result.equityPoints().get(result.equityPoints().size() - 1).equity();
        BigDecimal netPnl = finalEquity.subtract(startingCapital);
        BigDecimal grossPnl = completed.stream()
                .map(BacktestTrade::grossPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal explicitCosts = completed.stream()
                .map(BacktestTrade::explicitCosts)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal completedNetPnl = grossPnl.subtract(explicitCosts);
        BigDecimal openMarkedPnl = open.stream()
                .map(BacktestTrade::netPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        metrics.put("startingCapital", startingCapital);
        metrics.put("finalEquity", finalEquity);
        metrics.put("grossPnl", grossPnl.setScale(2, RoundingMode.HALF_UP));
        metrics.put("explicitCosts", explicitCosts.setScale(2, RoundingMode.HALF_UP));
        // Completed-trade net ignores still-open positions; account net includes their mark.
        metrics.put("completedNetPnl", completedNetPnl.setScale(2, RoundingMode.HALF_UP));
        metrics.put("openMarkedPnl", openMarkedPnl.setScale(2, RoundingMode.HALF_UP));
        metrics.put("netPnl", netPnl.setScale(2, RoundingMode.HALF_UP));
        notes.put("netPnl", "Net account P&L = completed net + marked-to-market open P&L; open P&L is not realized.");
        notes.put("completedNetPnl", "Net P&L of completed trades only (grossPnl - explicitCosts).");
        metrics.put("netReturnPct", pct(netPnl, startingCapital, notes, "netReturnPct"));

        int wins = 0;
        int losses = 0;
        int breakeven = 0;
        BigDecimal sumWins = BigDecimal.ZERO;
        BigDecimal sumLosses = BigDecimal.ZERO;
        int maxConsecutiveLosses = 0;
        int currentLosses = 0;
        for (BacktestTrade trade : completed) {
            int sign = trade.netPnl().signum();
            if (sign > 0) {
                wins++;
                sumWins = sumWins.add(trade.netPnl());
                currentLosses = 0;
            } else if (sign < 0) {
                losses++;
                sumLosses = sumLosses.add(trade.netPnl());
                currentLosses++;
                maxConsecutiveLosses = Math.max(maxConsecutiveLosses, currentLosses);
            } else {
                breakeven++;
            }
        }
        metrics.put("completedTrades", completed.size());
        metrics.put("openPositions", open.size());
        metrics.put("wins", wins);
        metrics.put("losses", losses);
        metrics.put("breakevens", breakeven);
        metrics.put("winRatePct", completed.isEmpty()
                ? null
                : BigDecimal.valueOf(wins * 100.0 / completed.size()).setScale(2, RoundingMode.HALF_UP));
        if (completed.isEmpty()) {
            notes.put("winRatePct", "No completed trades.");
        }
        metrics.put("averageWinner", wins == 0 ? null : sumWins.divide(BigDecimal.valueOf(wins), 2, RoundingMode.HALF_UP));
        metrics.put("averageLoser", losses == 0 ? null : sumLosses.divide(BigDecimal.valueOf(losses), 2, RoundingMode.HALF_UP));
        if (wins == 0) {
            notes.put("averageWinner", "No winning trades.");
        }
        if (losses == 0) {
            notes.put("averageLoser", "No losing trades.");
        }
        if (completed.isEmpty()) {
            metrics.put("expectancy", null);
            notes.put("expectancy", "No completed trades.");
        } else {
            // Mean net P&L per completed trade; breakeven trades correctly contribute zero rather
            // than being counted as losses.
            metrics.put("expectancy", completedNetPnl
                    .divide(BigDecimal.valueOf(completed.size()), 2, RoundingMode.HALF_UP));
        }
        if (sumLosses.signum() == 0) {
            metrics.put("profitFactor", null);
            notes.put("profitFactor", completed.isEmpty()
                    ? "No completed trades."
                    : "No losing trades; profit factor is undefined.");
        } else {
            metrics.put("profitFactor", BigDecimal.valueOf(sumWins.doubleValue() / Math.abs(sumLosses.doubleValue()))
                    .setScale(3, RoundingMode.HALF_UP));
        }
        List<BacktestTrade> withR = completed.stream().filter(t -> t.realizedR() != null).toList();
        metrics.put("averageRealizedR", withR.isEmpty()
                ? null
                : withR.stream().map(BacktestTrade::realizedR).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(withR.size()), 3, RoundingMode.HALF_UP));
        notes.put("averageRealizedR", "Denominator is the initial planned risk per unit times quantity.");
        metrics.put("maxConsecutiveLosses", maxConsecutiveLosses);

        Map<String, BigDecimal> drawdown = drawdown(result.equityPoints(), startingCapital, notes);
        metrics.put("maxDrawdown", drawdown.get("maxDrawdown"));
        metrics.put("maxDrawdownPct", drawdown.get("maxDrawdownPct") == null
                ? null
                : drawdown.get("maxDrawdownPct"));
        metrics.put("drawdownDurationSamples", drawdown.get("drawdownDurationSamples"));
        metrics.put("recovered", drawdown.get("recovered"));

        metrics.put("averageGrossExposure", averageExposure(result.equityPoints(), notes));
        metrics.put("turnover", turnover(completed, startingCapital));
        metrics.put("worstTradeNetPnl", completed.isEmpty()
                ? null
                : completed.stream().map(BacktestTrade::netPnl).min(BigDecimal::compareTo).orElse(null));
        metrics.put("sharpe", dailySharpe(result.equityPoints(), notes));
        metrics.put("sortino", dailySortino(result.equityPoints(), notes));
        metrics.put("cagrPct", cagr(finalEquity, startingCapital, result, notes));

        // A candidate is any valid setup that reached a risk decision (approved or rejected).
        long candidates = completed.size() + open.size() + result.rejections().size();
        metrics.put("candidateCount", candidates);
        metrics.put("rejectionCount", result.rejections().size());
        metrics.put("rejectionRatePct", candidates == 0
                ? null
                : BigDecimal.valueOf(result.rejections().size() * 100.0 / candidates).setScale(2, RoundingMode.HALF_UP));
        metrics.put("planCount", completed.size() + open.size());
        metrics.put("ambiguousBarCount", result.trades().stream().mapToInt(BacktestTrade::ambiguousBars).sum());

        // Pipeline stage counts so an empty/low trade list explains where candidates were eliminated.
        metrics.put("stageCounts", result.stageCounts());
        metrics.put("notes", notes);
        metrics.put("samplingAssumption",
                "Risk metrics use per-session (exchange-calendar day) equity returns annualized with 252 trading days; a zero reference rate.");
        return metrics;
    }

    private static BigDecimal pct(BigDecimal value, BigDecimal base, Map<String, String> notes, String key) {
        if (base == null || base.signum() == 0) {
            notes.put(key, "Starting capital is zero.");
            return null;
        }
        return value.multiply(HUNDRED).divide(base, 4, RoundingMode.HALF_UP);
    }

    private static Map<String, BigDecimal> drawdown(
            List<EquityPoint> points, BigDecimal startingCapital, Map<String, String> notes) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        if (points.isEmpty()) {
            notes.put("maxDrawdown", "No equity samples.");
            result.put("maxDrawdown", null);
            result.put("maxDrawdownPct", null);
            result.put("drawdownDurationSamples", null);
            result.put("recovered", null);
            return result;
        }
        BigDecimal highWater = startingCapital;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal maxDrawdownPct = null;
        int duration = 0;
        int maxDuration = 0;
        boolean underwater = false;
        for (EquityPoint point : points) {
            if (point.equity().compareTo(highWater) >= 0) {
                highWater = point.equity();
                underwater = false;
                duration = 0;
            } else {
                underwater = true;
                duration++;
                maxDuration = Math.max(maxDuration, duration);
            }
            BigDecimal dd = highWater.subtract(point.equity());
            if (dd.compareTo(maxDrawdown) > 0) {
                maxDrawdown = dd;
                maxDrawdownPct = highWater.signum() == 0
                        ? null
                        : dd.multiply(HUNDRED).divide(highWater, 4, RoundingMode.HALF_UP);
            }
        }
        result.put("maxDrawdown", maxDrawdown.setScale(2, RoundingMode.HALF_UP));
        result.put("maxDrawdownPct", maxDrawdownPct);
        result.put("drawdownDurationSamples", BigDecimal.valueOf(maxDuration));
        result.put("recovered", BigDecimal.valueOf(underwater ? 0 : 1));
        return result;
    }

    private static BigDecimal averageExposure(List<EquityPoint> points, Map<String, String> notes) {
        if (points.isEmpty()) {
            notes.put("averageGrossExposure", "No equity samples.");
            return null;
        }
        BigDecimal sum = points.stream().map(EquityPoint::grossExposure).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(points.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal turnover(List<BacktestTrade> completed, BigDecimal startingCapital) {
        if (completed.isEmpty() || startingCapital == null || startingCapital.signum() == 0) {
            return null;
        }
        BigDecimal notional = completed.stream()
                .map(t -> t.entryPrice().multiply(BigDecimal.valueOf(t.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return notional.divide(startingCapital, 4, RoundingMode.HALF_UP);
    }

    /**
     * Per-session equity returns: the last equity sample of each exchange-calendar day. This is a
     * proper time-based sampling regardless of how many intraday anchors a session produced, so the
     * annualization is not built on an invalid evenly-spaced-anchor assumption.
     */
    private static List<Double> dailyReturns(List<EquityPoint> points) {
        Map<java.time.LocalDate, BigDecimal> endOfDay = new java.util.TreeMap<>();
        for (EquityPoint point : points) {
            if (point.at() == null) {
                continue;
            }
            endOfDay.put(point.at().atZone(NseTradingCalendar.EXCHANGE_ZONE).toLocalDate(), point.equity());
        }
        List<Double> returns = new ArrayList<>();
        BigDecimal previous = null;
        for (BigDecimal equity : endOfDay.values()) {
            if (previous != null && previous.signum() != 0) {
                returns.add(equity.doubleValue() / previous.doubleValue() - 1.0);
            }
            previous = equity;
        }
        return returns;
    }

    private static final double TRADING_DAYS_PER_YEAR = 252.0;

    private static BigDecimal dailySharpe(List<EquityPoint> points, Map<String, String> notes) {
        List<Double> returns = dailyReturns(points);
        if (returns.size() < 2) {
            notes.put("sharpe", "Fewer than two complete sessions; not enough daily samples.");
            return null;
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream().mapToDouble(r -> (r - mean) * (r - mean)).sum() / (returns.size() - 1);
        double sd = Math.sqrt(variance);
        if (sd == 0) {
            notes.put("sharpe", "Zero daily return variance.");
            return null;
        }
        return BigDecimal.valueOf(mean / sd * Math.sqrt(TRADING_DAYS_PER_YEAR)).setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal dailySortino(List<EquityPoint> points, Map<String, String> notes) {
        List<Double> returns = dailyReturns(points);
        if (returns.size() < 2) {
            notes.put("sortino", "Fewer than two complete sessions; not enough daily samples.");
            return null;
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double downside = returns.stream().mapToDouble(r -> r < 0 ? r * r : 0).sum() / returns.size();
        double dd = Math.sqrt(downside);
        if (dd == 0) {
            notes.put("sortino", "No daily downside deviation.");
            return null;
        }
        return BigDecimal.valueOf(mean / dd * Math.sqrt(TRADING_DAYS_PER_YEAR)).setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal cagr(
            BigDecimal finalEquity, BigDecimal startingCapital, BacktestResult result, Map<String, String> notes) {
        if (result.equityPoints().size() < 2 || startingCapital == null || startingCapital.signum() <= 0) {
            notes.put("cagrPct", "Insufficient equity samples.");
            return null;
        }
        long seconds = java.time.Duration.between(
                        result.equityPoints().get(0).at(),
                        result.equityPoints().get(result.equityPoints().size() - 1).at())
                .getSeconds();
        double years = seconds / (365.25 * 24 * 3600);
        if (years <= 0 || finalEquity.signum() <= 0) {
            notes.put("cagrPct", "Undefined for a non-positive period or equity.");
            return null;
        }
        double ratio = finalEquity.doubleValue() / startingCapital.doubleValue();
        double cagr = (Math.pow(ratio, 1.0 / years) - 1.0) * 100.0;
        if (!Double.isFinite(cagr)) {
            notes.put("cagrPct", "Undefined for a very short period; annualization is not meaningful.");
            return null;
        }
        return BigDecimal.valueOf(cagr).setScale(2, RoundingMode.HALF_UP);
    }
}
