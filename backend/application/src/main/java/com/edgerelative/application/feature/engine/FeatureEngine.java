package com.edgerelative.application.feature.engine;

import com.edgerelative.application.feature.context.MarketContextFeature;
import com.edgerelative.application.feature.context.SectorContextFeature;
import com.edgerelative.application.feature.domain.ContextSnapshot;
import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureKeys;
import com.edgerelative.application.feature.domain.FeatureQuality;
import com.edgerelative.application.feature.domain.FeatureSchemaVersions;
import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.domain.FeatureValue;
import com.edgerelative.application.feature.domain.FeatureVersion;
import com.edgerelative.application.feature.math.BarSeries;
import com.edgerelative.application.feature.policy.FeaturePolicy;
import com.edgerelative.application.feature.policy.FeatureVersions;
import com.edgerelative.application.feature.relative.RrsFeature;
import com.edgerelative.application.feature.relative.RrsParameters;
import com.edgerelative.application.feature.volume.RveFeature;
import com.edgerelative.application.feature.volume.RvolFeature;
import com.edgerelative.application.feature.volume.SessionModel;
import com.edgerelative.application.feature.volume.VolumeBaselineEstimator;
import com.edgerelative.application.feature.volume.VolumeBaselineEstimators;
import com.edgerelative.application.feature.volatility.AtrFeature;
import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.application.reference.TimeframeCatalog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single production feature engine (DD-05 §123–§125).
 *
 * <p>Pure and deterministic: given identical canonical input, parameters and calendar it produces
 * identical snapshots. Live, replay, backtest and historical reconstruction all call this same code.
 * There is no broker, HTTP, database or wall-clock access in the calculation path; no clock is read,
 * because anchors come from the canonical bars. Every array value at index {@code i} depends only on
 * inputs at or before {@code i}.
 */
public final class FeatureEngine {

    private static final Set<String> REQUIRED = Set.of(
            FeatureKeys.ATR,
            FeatureKeys.RRS_RAW,
            FeatureKeys.RRS_FAST,
            FeatureKeys.RRS_SLOW,
            FeatureKeys.RRS_PERSISTENCE,
            FeatureKeys.RRS_PERCENTILE,
            FeatureKeys.RVOL_D1,
            FeatureKeys.RVOL_INTERVAL,
            FeatureKeys.RVOL_CUMULATIVE,
            FeatureKeys.RVE);

    private final AtrFeature atrFeature = new AtrFeature();
    private final RrsFeature rrsFeature = new RrsFeature();
    private final RvolFeature rvolFeature = new RvolFeature();
    private final RveFeature rveFeature = new RveFeature();
    private final MarketContextFeature marketContextFeature = new MarketContextFeature();
    private final SectorContextFeature sectorContextFeature = new SectorContextFeature();

    public FeatureSnapshot snapshot(FeatureContext context) {
        List<FeatureSnapshot> snapshots = snapshots(context);
        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException("Feature context has no subject candles");
        }
        return snapshots.get(snapshots.size() - 1);
    }

    /**
     * Produces one snapshot per subject bar; every value is causal.
     */
    public List<FeatureSnapshot> snapshots(FeatureContext context) {
        BarSeries subject = BarSeries.of(context.subjectCandles());
        BarSeries market = BarSeries.of(context.marketCandles());
        BarSeries sector = BarSeries.of(context.sectorCandles());
        FeaturePolicy policy = context.policy();
        FeatureVersions versions = context.versions();
        String timeframe = context.timeframe();
        SessionModel session = new SessionModel(context.calendar(), timeframeMinutes(context.calendar(), timeframe));
        VolumeBaselineEstimator estimator = VolumeBaselineEstimators.of(
                policy.rvol().estimator(), policy.rvol().trimmedFraction(), policy.rvol().ewSpan());

        int atrLength = policy.atr().lengthFor(timeframe);
        double[] atr = atrFeature.raw(subject, atrLength, policy.atr().smoothing());
        RrsParameters rrsParameters = new RrsParameters(
                atrLength,
                policy.atr().smoothing(),
                policy.rrs().priceChange(),
                policy.rrs().fastLength(),
                policy.rrs().slowLength(),
                policy.rrs().persistenceWindow(),
                policy.rrs().slopeLookback(),
                policy.rrs().percentileWindow());
        RrsFeature.Result rrs = rrsFeature.compute(subject, market, rrsParameters);
        RrsFeature.Result vsSector =
                market.size() > 0 && sector.size() > 0 ? rrsFeature.compute(subject, sector, rrsParameters) : null;
        RvolFeature.Result rvol = rvolFeature.compute(
                subject, policy.rvol(), policy.directionalVolume().window(), session, estimator);
        Metric[] rve = rveFeature.compute(
                rvol.interval(), policy.rve().fastLength(), policy.rve().slowLength());
        MarketContextFeature.Result marketContext =
                market.size() > 0 ? marketContextFeature.compute(market, policy, timeframe) : null;
        SectorContextFeature.Result sectorContext =
                market.size() > 0 && sector.size() > 0
                        ? sectorContextFeature.compute(sector, market, policy, timeframe)
                        : null;

        List<FeatureSnapshot> result = new ArrayList<>(subject.size());
        for (int i = 0; i < subject.size(); i++) {
            Instant anchor = subject.closeTime(i);
            Map<String, FeatureValue> features = new LinkedHashMap<>();
            put(features, FeatureKeys.ATR, versions.atr(FeatureKeys.ATR, timeframe), anchor, timeframe,
                    atrFeature.metricAt(subject, i, atr));
            put(features, FeatureKeys.RRS_RAW, versions.rrs(FeatureKeys.RRS_RAW, timeframe, context.marketCode()),
                    anchor, timeframe, rrsRaw(rrs, i));
            put(features, FeatureKeys.RRS_FAST, versions.rrs(FeatureKeys.RRS_FAST, timeframe, context.marketCode()),
                    anchor, timeframe, derived(rrs, i, rrs.fast()));
            put(features, FeatureKeys.RRS_SLOW, versions.rrs(FeatureKeys.RRS_SLOW, timeframe, context.marketCode()),
                    anchor, timeframe, derived(rrs, i, rrs.slow()));
            put(features, FeatureKeys.RRS_PERSISTENCE,
                    versions.rrs(FeatureKeys.RRS_PERSISTENCE, timeframe, context.marketCode()),
                    anchor, timeframe, derived(rrs, i, rrs.persistence()));
            put(features, FeatureKeys.RRS_SLOPE, versions.rrs(FeatureKeys.RRS_SLOPE, timeframe, context.marketCode()),
                    anchor, timeframe, derived(rrs, i, rrs.slope()));
            put(features, FeatureKeys.RRS_ACCELERATION,
                    versions.rrs(FeatureKeys.RRS_ACCELERATION, timeframe, context.marketCode()),
                    anchor, timeframe, derived(rrs, i, rrs.acceleration()));
            put(features, FeatureKeys.RRS_PERCENTILE,
                    versions.rrs(FeatureKeys.RRS_PERCENTILE, timeframe, context.marketCode()),
                    anchor, timeframe, derived(rrs, i, rrs.percentile()));
            put(features, FeatureKeys.RRS_TREND_STATE,
                    versions.rrs(FeatureKeys.RRS_TREND_STATE, timeframe, context.marketCode()),
                    anchor, timeframe, trend(rrs, i));
            if (vsSector != null) {
                put(features, FeatureKeys.RRS_VS_SECTOR_RAW,
                        versions.rrs(FeatureKeys.RRS_VS_SECTOR_RAW, timeframe, context.sectorCode()),
                        anchor, timeframe, rrsRaw(vsSector, i));
            }
            put(features, FeatureKeys.RVOL_D1, versions.rvol(FeatureKeys.RVOL_D1, timeframe), anchor, timeframe,
                    rvol.daily()[i]);
            put(features, FeatureKeys.RVOL_INTERVAL, versions.rvol(FeatureKeys.RVOL_INTERVAL, timeframe), anchor,
                    timeframe, rvol.interval()[i]);
            put(features, FeatureKeys.RVOL_CUMULATIVE, versions.rvol(FeatureKeys.RVOL_CUMULATIVE, timeframe), anchor,
                    timeframe, rvol.cumulative()[i]);
            put(features, FeatureKeys.RVE, versions.rve(timeframe), anchor, timeframe, rve[i]);
            put(features, FeatureKeys.DIRECTIONAL_VOLUME_LONG,
                    versions.directionalVolume(FeatureKeys.DIRECTIONAL_VOLUME_LONG), anchor, timeframe,
                    rvol.directionalLong()[i]);
            put(features, FeatureKeys.DIRECTIONAL_VOLUME_SHORT,
                    versions.directionalVolume(FeatureKeys.DIRECTIONAL_VOLUME_SHORT), anchor, timeframe,
                    rvol.directionalShort()[i]);

            result.add(new FeatureSnapshot(
                    context.instrumentId(),
                    anchor,
                    timeframe,
                    FeatureSchemaVersions.CURRENT,
                    requiredQuality(features),
                    requiredAvailability(features),
                    context.benchmark(),
                    features,
                    marketSnapshot(marketContext, market, anchor, context, versions, timeframe),
                    sectorSnapshot(sectorContext, sector, anchor, context, versions, timeframe)));
        }
        return result;
    }

    private ContextSnapshot marketSnapshot(
            MarketContextFeature.Result context,
            BarSeries market,
            Instant anchor,
            FeatureContext featureContext,
            FeatureVersions versions,
            String timeframe) {
        if (context == null) {
            return null;
        }
        int index = market.indexOfCloseTime(anchor);
        if (index < 0) {
            return null;
        }
        Map<String, FeatureValue> features = new LinkedHashMap<>();
        features.put(FeatureKeys.MARKET_ATR, value(
                versions.atr(FeatureKeys.MARKET_ATR, timeframe), anchor, timeframe, context.atr()[index]));
        features.put(FeatureKeys.MARKET_DIRECTIONAL_EFFICIENCY, value(
                versions.structure(FeatureKeys.MARKET_DIRECTIONAL_EFFICIENCY),
                anchor, timeframe, context.directionalEfficiency()[index]));
        features.put(FeatureKeys.MARKET_PRICE_STRUCTURE, value(
                versions.structure(FeatureKeys.MARKET_PRICE_STRUCTURE),
                anchor, timeframe, context.priceStructure()[index]));
        return new ContextSnapshot(
                "MARKET",
                featureContext.benchmark() == null ? null : featureContext.benchmark().marketInstrumentId(),
                featureContext.marketCode(),
                null,
                anchor,
                timeframe,
                contextQuality(features),
                features);
    }

    private ContextSnapshot sectorSnapshot(
            SectorContextFeature.Result context,
            BarSeries sector,
            Instant anchor,
            FeatureContext featureContext,
            FeatureVersions versions,
            String timeframe) {
        if (context == null) {
            return null;
        }
        int index = sector.indexOfCloseTime(anchor);
        if (index < 0) {
            return null;
        }
        Map<String, FeatureValue> features = new LinkedHashMap<>();
        features.put(FeatureKeys.SECTOR_RRS_RAW, value(
                versions.rrs(FeatureKeys.SECTOR_RRS_RAW, timeframe, featureContext.marketCode()),
                anchor, timeframe, context.rrsRaw()[index]));
        features.put(FeatureKeys.SECTOR_DIRECTIONAL_EFFICIENCY, value(
                versions.structure(FeatureKeys.SECTOR_DIRECTIONAL_EFFICIENCY),
                anchor, timeframe, context.directionalEfficiency()[index]));
        features.put(FeatureKeys.SECTOR_PRICE_STRUCTURE, value(
                versions.structure(FeatureKeys.SECTOR_PRICE_STRUCTURE),
                anchor, timeframe, context.priceStructure()[index]));
        return new ContextSnapshot(
                "SECTOR",
                featureContext.benchmark() == null ? null : featureContext.benchmark().sectorInstrumentId(),
                featureContext.sectorCode(),
                featureContext.benchmark() == null ? null : featureContext.benchmark().sectorId(),
                anchor,
                timeframe,
                contextQuality(features),
                features);
    }

    private static void put(
            Map<String, FeatureValue> features,
            String key,
            FeatureVersion version,
            Instant anchor,
            String timeframe,
            Metric metric) {
        features.put(key, value(version, anchor, timeframe, metric));
    }

    private static FeatureValue value(FeatureVersion version, Instant anchor, String timeframe, Metric metric) {
        if (metric.available() && metric.value() != null) {
            return FeatureValue.numeric(version, anchor, timeframe, metric.value(), metric.quality());
        }
        if (metric.available() && metric.label() != null) {
            return FeatureValue.categorical(version, anchor, timeframe, metric.label(), metric.quality());
        }
        return FeatureValue.unavailable(
                version, anchor, timeframe, metric.availability(), metric.quality(), metric.reason());
    }

    private static Metric rrsRaw(RrsFeature.Result result, int index) {
        if (result.rawAvailability()[index] == FeatureAvailability.VALID) {
            return Metric.numeric(result.raw()[index], result.rawQuality()[index]);
        }
        return Metric.unavailable(
                result.rawAvailability()[index], result.rawQuality()[index], "RRS raw unavailable");
    }

    private static Metric derived(RrsFeature.Result result, int index, double[] values) {
        if (result.rawAvailability()[index] != FeatureAvailability.VALID) {
            return Metric.unavailable(
                    result.rawAvailability()[index], result.rawQuality()[index], "RRS raw unavailable");
        }
        if (!Double.isFinite(values[index])) {
            return Metric.unavailable(FeatureAvailability.WARMING_UP, FeatureQuality.INCOMPLETE, "warmup");
        }
        return Metric.numeric(values[index], result.rawQuality()[index]);
    }

    private static Metric trend(RrsFeature.Result result, int index) {
        if (result.rawAvailability()[index] != FeatureAvailability.VALID) {
            return Metric.unavailable(
                    result.rawAvailability()[index], result.rawQuality()[index], "RRS raw unavailable");
        }
        if (result.trendState()[index] == null) {
            return Metric.unavailable(FeatureAvailability.WARMING_UP, FeatureQuality.INCOMPLETE, "warmup");
        }
        return Metric.label(result.trendState()[index], result.rawQuality()[index]);
    }

    private static FeatureQuality requiredQuality(Map<String, FeatureValue> features) {
        List<FeatureQuality> qualities = new ArrayList<>();
        features.forEach((key, value) -> {
            if (REQUIRED.contains(key)) {
                qualities.add(value.quality());
            }
        });
        return FeatureQuality.worst(qualities);
    }

    private static FeatureAvailability requiredAvailability(Map<String, FeatureValue> features) {
        for (Map.Entry<String, FeatureValue> entry : features.entrySet()) {
            if (REQUIRED.contains(entry.getKey()) && entry.getValue().availability() != FeatureAvailability.VALID) {
                return entry.getValue().availability();
            }
        }
        return FeatureAvailability.VALID;
    }

    private static FeatureQuality contextQuality(Map<String, FeatureValue> features) {
        return FeatureQuality.worst(features.values().stream().map(FeatureValue::quality).toList());
    }

    private static int timeframeMinutes(NseTradingCalendar calendar, String timeframe) {
        TimeframeCatalog.Spec spec = TimeframeCatalog.require(timeframe);
        if (spec.duration() == null) {
            return (int) calendar.sessionMinutes();
        }
        return (int) (spec.duration().getSeconds() / 60);
    }
}
