package com.edgerelative.application.feature.policy;

import com.edgerelative.application.feature.domain.FeatureKeys;
import com.edgerelative.application.feature.domain.FeatureVersion;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Builds the immutable {@link FeatureVersion}s for the active policy. Parameters include the
 * timeframe-specific ATR length, the benchmark code, and every window, so a version fully identifies
 * its calculation (DD-05 §120/§122).
 *
 * <p>Versions depend only on the policy and the (key, timeframe, benchmark) tuple, so they are
 * memoised. Without this, a long replay rebuilt a fresh version object (with a parameter map) for
 * every feature at every anchor — millions of identical objects for an M1 series.
 */
public final class FeatureVersions {

    public static final String ATR = "ATR_V1";
    public static final String RRS = "RRS_V1";
    public static final String RVOL = "RVOL_V1";
    public static final String RVE = "RVE_V1";
    public static final String STRUCTURE = "STRUCTURE_V1";
    public static final String DIRECTIONAL_VOLUME = "DIRECTIONAL_VOLUME_V1";

    private final FeaturePolicy policy;
    private final ConcurrentHashMap<String, FeatureVersion> cache = new ConcurrentHashMap<>();

    public FeatureVersions(FeaturePolicy policy) {
        this.policy = policy;
    }

    private FeatureVersion cached(String key, Supplier<FeatureVersion> factory) {
        FeatureVersion existing = cache.get(key);
        if (existing != null) {
            return existing;
        }
        FeatureVersion created = factory.get();
        FeatureVersion raced = cache.putIfAbsent(key, created);
        return raced == null ? created : raced;
    }

    public FeatureVersion atr(String featureKey, String timeframe) {
        return cached("atr|" + featureKey + "|" + timeframe, () -> FeatureVersion.of(
                featureKey,
                ATR,
                CalculationVersions.CURRENT,
                FeatureVersion.parameters(
                        "atrLength", policy.atr().lengthFor(timeframe),
                        "smoothing", policy.atr().smoothing(),
                        "timeframe", timeframe)));
    }

    public FeatureVersion rrs(String featureKey, String timeframe, String benchmarkCode) {
        return cached("rrs|" + featureKey + "|" + timeframe + "|" + benchmarkCode, () -> {
            var rrs = policy.rrs();
            return FeatureVersion.of(
                    featureKey,
                    RRS,
                    CalculationVersions.CURRENT,
                    FeatureVersion.parameters(
                            "atrLength", policy.atr().lengthFor(timeframe),
                            "atrSmoothing", policy.atr().smoothing(),
                            "priceChange", rrs.priceChange(),
                            "fastLength", rrs.fastLength(),
                            "slowLength", rrs.slowLength(),
                            "persistenceWindow", rrs.persistenceWindow(),
                            "slopeLookback", rrs.slopeLookback(),
                            "percentileWindow", rrs.percentileWindow(),
                            "percentileMinSamples", rrs.percentileMinSamples(),
                            "benchmark", benchmarkCode == null ? "UNRESOLVED" : benchmarkCode,
                            "timeframe", timeframe));
        });
    }

    public FeatureVersion rvol(String featureKey, String timeframe) {
        return cached("rvol|" + featureKey + "|" + timeframe, () -> {
            var rvol = policy.rvol();
            return FeatureVersion.of(
                    featureKey,
                    RVOL,
                    CalculationVersions.CURRENT,
                    FeatureVersion.parameters(
                            "estimator", rvol.estimator(),
                            "dailyLookback", rvol.dailyLookback(),
                            "intervalLookback", rvol.intervalLookback(),
                            "cumulativeLookback", rvol.cumulativeLookback(),
                            "minSamples", rvol.minSamples(),
                            "trimmedFraction", rvol.trimmedFraction(),
                            "ewSpan", rvol.ewSpan(),
                            "timeframe", timeframe));
        });
    }

    public FeatureVersion rve(String timeframe) {
        return cached("rve|" + timeframe, () -> FeatureVersion.of(
                FeatureKeys.RVE,
                RVE,
                CalculationVersions.CURRENT,
                FeatureVersion.parameters(
                        "fastLength", policy.rve().fastLength(),
                        "slowLength", policy.rve().slowLength(),
                        "timeframe", timeframe)));
    }

    public FeatureVersion structure(String featureKey) {
        return cached("structure|" + featureKey, () -> FeatureVersion.of(
                featureKey,
                STRUCTURE,
                CalculationVersions.CURRENT,
                FeatureVersion.parameters(
                        "pivotWidth", policy.structure().pivotWidth(),
                        "efficiencyWindow", policy.structure().efficiencyWindow())));
    }

    public FeatureVersion directionalVolume(String featureKey) {
        return cached("dvol|" + featureKey, () -> FeatureVersion.of(
                featureKey,
                DIRECTIONAL_VOLUME,
                CalculationVersions.CURRENT,
                FeatureVersion.parameters("window", policy.directionalVolume().window())));
    }
}
