package com.edgerelative.application.feature.policy;

import com.edgerelative.application.feature.math.AtrSmoothing;
import com.edgerelative.application.feature.math.PriceChange;
import com.edgerelative.application.feature.volume.BaselineEstimatorType;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed feature configuration.
 *
 * <p>Values are research candidates, not production constants. They live here (and in
 * {@code application.yaml}) so no parameter is buried in calculation code, and they are folded into
 * the feature version's parameter hash so a change is visible to lineage and forces a rebuild.
 */
@ConfigurationProperties(prefix = "feature")
public class FeatureProperties {

    private int historyDays;
    private final Benchmark benchmark = new Benchmark();
    private final Atr atr = new Atr();
    private final Rrs rrs = new Rrs();
    private final Rvol rvol = new Rvol();
    private final Rve rve = new Rve();
    private final Structure structure = new Structure();
    private final DirectionalVolume directionalVolume = new DirectionalVolume();
    private final Persistence persistence = new Persistence();
    private final Live live = new Live();

    public FeaturePolicy toPolicy() {
        return new FeaturePolicy(
                historyDays,
                new FeaturePolicy.Benchmark(benchmark.getMarketCode()),
                new FeaturePolicy.Atr(
                        atr.getLengthByTimeframe(),
                        atr.getDefaultLength(),
                        AtrSmoothing.parse(atr.getSmoothing())),
                new FeaturePolicy.Rrs(
                        PriceChange.parse(rrs.getPriceChange()),
                        rrs.getFastLength(),
                        rrs.getSlowLength(),
                        rrs.getPersistenceWindow(),
                        rrs.getSlopeLookback(),
                        rrs.getPercentileWindow()),
                new FeaturePolicy.Rvol(
                        BaselineEstimatorType.parse(rvol.getEstimator()),
                        rvol.getDailyLookback(),
                        rvol.getIntervalLookback(),
                        rvol.getCumulativeLookback(),
                        rvol.getMinSamples(),
                        rvol.getTrimmedFraction(),
                        rvol.getEwSpan()),
                new FeaturePolicy.Rve(rve.getFastLength(), rve.getSlowLength()),
                new FeaturePolicy.Structure(structure.getPivotWidth(), structure.getEfficiencyWindow()),
                new FeaturePolicy.DirectionalVolume(directionalVolume.getWindow()));
    }

    public void validate() {
        positive(historyDays, "feature.history-days");
        requireText(benchmark.getMarketCode(), "feature.benchmark.market-code");
        positive(atr.getDefaultLength(), "feature.atr.default-length");
        requireText(atr.getSmoothing(), "feature.atr.smoothing");
        positive(rrs.getFastLength(), "feature.rrs.fast-length");
        positive(rrs.getSlowLength(), "feature.rrs.slow-length");
        if (rrs.getSlowLength() < rrs.getFastLength()) {
            throw new IllegalStateException("feature.rrs.slow-length must be >= fast-length");
        }
        positive(rrs.getPersistenceWindow(), "feature.rrs.persistence-window");
        positive(rrs.getSlopeLookback(), "feature.rrs.slope-lookback");
        positive(rrs.getPercentileWindow(), "feature.rrs.percentile-window");
        positive(rvol.getDailyLookback(), "feature.rvol.daily-lookback");
        positive(rvol.getIntervalLookback(), "feature.rvol.interval-lookback");
        positive(rvol.getCumulativeLookback(), "feature.rvol.cumulative-lookback");
        positive(rvol.getMinSamples(), "feature.rvol.min-samples");
        positive(rve.getFastLength(), "feature.rve.fast-length");
        positive(rve.getSlowLength(), "feature.rve.slow-length");
        positive(structure.getPivotWidth(), "feature.structure.pivot-width");
        positive(structure.getEfficiencyWindow(), "feature.structure.efficiency-window");
        positive(directionalVolume.getWindow(), "feature.directional-volume.window");
        positive(persistence.getQueueCapacity(), "feature.persistence.queue-capacity");
        positive(live.getMaxBars(), "feature.live.max-bars");
    }

    private static void positive(int value, String name) {
        if (value < 1) {
            throw new IllegalStateException(name + " must be >= 1");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
    }

    public int getHistoryDays() {
        return historyDays;
    }

    public void setHistoryDays(int historyDays) {
        this.historyDays = historyDays;
    }

    public Benchmark getBenchmark() {
        return benchmark;
    }

    public Atr getAtr() {
        return atr;
    }

    public Rrs getRrs() {
        return rrs;
    }

    public Rvol getRvol() {
        return rvol;
    }

    public Rve getRve() {
        return rve;
    }

    public Structure getStructure() {
        return structure;
    }

    public DirectionalVolume getDirectionalVolume() {
        return directionalVolume;
    }

    public Persistence getPersistence() {
        return persistence;
    }

    public Live getLive() {
        return live;
    }

    public static class Benchmark {
        private String marketCode;

        public String getMarketCode() {
            return marketCode;
        }

        public void setMarketCode(String marketCode) {
            this.marketCode = marketCode;
        }
    }

    public static class Atr {
        private Map<String, Integer> lengthByTimeframe = new LinkedHashMap<>();
        private int defaultLength;
        private String smoothing;

        public Map<String, Integer> getLengthByTimeframe() {
            return lengthByTimeframe;
        }

        public void setLengthByTimeframe(Map<String, Integer> lengthByTimeframe) {
            this.lengthByTimeframe = lengthByTimeframe;
        }

        public int getDefaultLength() {
            return defaultLength;
        }

        public void setDefaultLength(int defaultLength) {
            this.defaultLength = defaultLength;
        }

        public String getSmoothing() {
            return smoothing;
        }

        public void setSmoothing(String smoothing) {
            this.smoothing = smoothing;
        }
    }

    public static class Rrs {
        private String priceChange;
        private int fastLength;
        private int slowLength;
        private int persistenceWindow;
        private int slopeLookback;
        private int percentileWindow;

        public String getPriceChange() {
            return priceChange;
        }

        public void setPriceChange(String priceChange) {
            this.priceChange = priceChange;
        }

        public int getFastLength() {
            return fastLength;
        }

        public void setFastLength(int fastLength) {
            this.fastLength = fastLength;
        }

        public int getSlowLength() {
            return slowLength;
        }

        public void setSlowLength(int slowLength) {
            this.slowLength = slowLength;
        }

        public int getPersistenceWindow() {
            return persistenceWindow;
        }

        public void setPersistenceWindow(int persistenceWindow) {
            this.persistenceWindow = persistenceWindow;
        }

        public int getSlopeLookback() {
            return slopeLookback;
        }

        public void setSlopeLookback(int slopeLookback) {
            this.slopeLookback = slopeLookback;
        }

        public int getPercentileWindow() {
            return percentileWindow;
        }

        public void setPercentileWindow(int percentileWindow) {
            this.percentileWindow = percentileWindow;
        }
    }

    public static class Rvol {
        private String estimator;
        private int dailyLookback;
        private int intervalLookback;
        private int cumulativeLookback;
        private int minSamples;
        private double trimmedFraction;
        private int ewSpan;

        public String getEstimator() {
            return estimator;
        }

        public void setEstimator(String estimator) {
            this.estimator = estimator;
        }

        public int getDailyLookback() {
            return dailyLookback;
        }

        public void setDailyLookback(int dailyLookback) {
            this.dailyLookback = dailyLookback;
        }

        public int getIntervalLookback() {
            return intervalLookback;
        }

        public void setIntervalLookback(int intervalLookback) {
            this.intervalLookback = intervalLookback;
        }

        public int getCumulativeLookback() {
            return cumulativeLookback;
        }

        public void setCumulativeLookback(int cumulativeLookback) {
            this.cumulativeLookback = cumulativeLookback;
        }

        public int getMinSamples() {
            return minSamples;
        }

        public void setMinSamples(int minSamples) {
            this.minSamples = minSamples;
        }

        public double getTrimmedFraction() {
            return trimmedFraction;
        }

        public void setTrimmedFraction(double trimmedFraction) {
            this.trimmedFraction = trimmedFraction;
        }

        public int getEwSpan() {
            return ewSpan;
        }

        public void setEwSpan(int ewSpan) {
            this.ewSpan = ewSpan;
        }
    }

    public static class Rve {
        private int fastLength;
        private int slowLength;

        public int getFastLength() {
            return fastLength;
        }

        public void setFastLength(int fastLength) {
            this.fastLength = fastLength;
        }

        public int getSlowLength() {
            return slowLength;
        }

        public void setSlowLength(int slowLength) {
            this.slowLength = slowLength;
        }
    }

    public static class Structure {
        private int pivotWidth;
        private int efficiencyWindow;

        public int getPivotWidth() {
            return pivotWidth;
        }

        public void setPivotWidth(int pivotWidth) {
            this.pivotWidth = pivotWidth;
        }

        public int getEfficiencyWindow() {
            return efficiencyWindow;
        }

        public void setEfficiencyWindow(int efficiencyWindow) {
            this.efficiencyWindow = efficiencyWindow;
        }
    }

    public static class DirectionalVolume {
        private int window;

        public int getWindow() {
            return window;
        }

        public void setWindow(int window) {
            this.window = window;
        }
    }

    public static class Live {
        private int maxBars;

        public int getMaxBars() {
            return maxBars;
        }

        public void setMaxBars(int maxBars) {
            this.maxBars = maxBars;
        }
    }

    public static class Persistence {
        private boolean enabled = true;
        private int queueCapacity;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }
}
