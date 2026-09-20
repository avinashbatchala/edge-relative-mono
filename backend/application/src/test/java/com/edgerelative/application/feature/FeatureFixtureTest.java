package com.edgerelative.application.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.feature.engine.Metric;
import com.edgerelative.application.feature.math.Atr;
import com.edgerelative.application.feature.math.AtrSmoothing;
import com.edgerelative.application.feature.math.BarSeries;
import com.edgerelative.application.feature.math.PriceChange;
import com.edgerelative.application.feature.relative.RrsFeature;
import com.edgerelative.application.feature.relative.RrsParameters;
import com.edgerelative.application.feature.volume.RveFeature;
import com.edgerelative.application.feature.volume.RvolFeature;
import com.edgerelative.application.feature.volume.SessionModel;
import com.edgerelative.application.feature.volume.BaselineEstimatorType;
import com.edgerelative.application.feature.volume.VolumeBaselineEstimators;
import com.edgerelative.application.history.AggregatedCandle;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the shared, frozen fixtures consumed by the (future) Python implementation too.
 */
class FeatureFixtureTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void atrFixtureMatches() throws IOException {
        JsonNode fixture = load("atr-v1.json");
        int length = fixture.path("parameters").path("length").asInt();
        AtrSmoothing smoothing = AtrSmoothing.parse(fixture.path("parameters").path("smoothing").asString());
        BarSeries bars = BarSeries.of(bars(fixture.path("bars")));
        double[] atr = Atr.series(high(bars), low(bars), close(bars), length, smoothing);
        assertArray(fixture.path("expected"), atr);
    }

    @Test
    void rrsFixtureMatches() throws IOException {
        JsonNode fixture = load("rrs-v1.json");
        JsonNode parameters = fixture.path("parameters");
        RrsParameters rrsParameters = new RrsParameters(
                parameters.path("atrLength").asInt(),
                AtrSmoothing.parse(parameters.path("atrSmoothing").asString()),
                PriceChange.parse(parameters.path("priceChange").asString()),
                parameters.path("fastLength").asInt(),
                parameters.path("slowLength").asInt(),
                parameters.path("persistenceWindow").asInt(),
                parameters.path("slopeLookback").asInt(),
                parameters.path("percentileWindow").asInt(),
                parameters.path("percentileMinSamples").asInt(1));
        RrsFeature.Result result = new RrsFeature().compute(
                BarSeries.of(bars(fixture.path("bars"))),
                BarSeries.of(bars(fixture.path("benchmarkBars"))),
                rrsParameters);
        assertArray(fixture.path("expectedRaw"), result.raw());
        assertArray(fixture.path("expectedFast"), result.fast());
        assertArray(fixture.path("expectedSlow"), result.slow());
        assertArray(fixture.path("expectedPersistence"), result.persistence());
        assertArray(fixture.path("expectedAcceleration"), result.acceleration());
        assertArray(fixture.path("expectedPercentile"), result.percentile());
        JsonNode trend = fixture.path("expectedTrendState");
        for (int i = 0; i < trend.size(); i++) {
            assertThat(result.trendState()[i]).isEqualTo(trend.get(i).isNull() ? null : trend.get(i).asString());
        }
    }

    @Test
    void rvolFixtureMatches() throws IOException {
        JsonNode fixture = load("rvol-v1.json");
        JsonNode parameters = fixture.path("parameters");
        var rvolParameters = new com.edgerelative.application.feature.policy.FeaturePolicy.Rvol(
                BaselineEstimatorType.parse(parameters.path("estimator").asString()),
                parameters.path("dailyLookback").asInt(),
                parameters.path("intervalLookback").asInt(),
                parameters.path("cumulativeLookback").asInt(),
                parameters.path("minSamples").asInt(),
                0.1,
                20);
        SessionModel session = new SessionModel(FeatureTestSupport.CALENDAR, fixture.path("timeframeMinutes").asInt());
        RvolFeature.Result result = new RvolFeature().compute(
                BarSeries.of(bars(fixture.path("bars"))),
                rvolParameters,
                20,
                session,
                VolumeBaselineEstimators.of(BaselineEstimatorType.MEAN, 0.1, 20));
        assertMetricArray(fixture.path("expectedDaily"), result.daily());
        assertMetricArray(fixture.path("expectedInterval"), result.interval());
        assertMetricArray(fixture.path("expectedCumulative"), result.cumulative());
        assertMetricArray(fixture.path("expectedDirectionalLong"), result.directionalLong());
        assertMetricArray(fixture.path("expectedDirectionalShort"), result.directionalShort());
    }

    @Test
    void rveFixtureMatches() throws IOException {
        JsonNode fixture = load("rve-v1.json");
        JsonNode values = fixture.path("rvolInterval");
        Metric[] input = new Metric[values.size()];
        for (int i = 0; i < values.size(); i++) {
            input[i] = Metric.numeric(values.get(i).asDouble());
        }
        Metric[] result = new RveFeature().compute(
                input,
                fixture.path("parameters").path("fastLength").asInt(),
                fixture.path("parameters").path("slowLength").asInt());
        assertMetricArray(fixture.path("expected"), result);
    }

    private static void assertMetricArray(JsonNode expected, Metric[] actual) {
        assertThat(actual).hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            if (expected.get(i).isNull()) {
                assertThat(actual[i].available()).as("index %d", i).isFalse();
            } else {
                assertThat(actual[i].available()).as("index %d", i).isTrue();
                assertThat(actual[i].value()).isCloseTo(expected.get(i).asDouble(), org.assertj.core.data.Offset.offset(1e-6));
            }
        }
    }

    private static void assertArray(JsonNode expected, double[] actual) {
        assertThat(actual).hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            if (expected.get(i).isNull()) {
                assertThat(actual[i]).as("index %d", i).isNaN();
            } else {
                assertThat(actual[i]).as("index %d", i)
                        .isCloseTo(expected.get(i).asDouble(), org.assertj.core.data.Offset.offset(1e-9));
            }
        }
    }

    private static List<AggregatedCandle> bars(JsonNode array) {
        List<AggregatedCandle> bars = new ArrayList<>();
        for (JsonNode node : array) {
            bars.add(new AggregatedCandle(
                    java.time.Instant.parse(node.path("openTime").asString()),
                    java.time.Instant.parse(node.path("closeTime").asString()),
                    decimal(node.path("open")),
                    decimal(node.path("high")),
                    decimal(node.path("low")),
                    decimal(node.path("close")),
                    node.path("volume").asLong(),
                    null,
                    null,
                    null,
                    false,
                    node.path("complete").asBoolean(true),
                    node.path("quality").asString("GOOD"),
                    "er-aggregate-v1"));
        }
        return bars;
    }

    private static BigDecimal decimal(JsonNode node) {
        return BigDecimal.valueOf(node.asDouble());
    }

    private static JsonNode load(String name) throws IOException {
        Path path = fixturesDir().resolve(name);
        return JSON.readTree(Files.readString(path));
    }

    private static Path fixturesDir() {
        Path working = Path.of(System.getProperty("user.dir"));
        for (Path candidate = working; candidate != null; candidate = candidate.getParent()) {
            Path fixtures = candidate.resolve("contracts").resolve("fixtures").resolve("features");
            if (Files.isDirectory(fixtures)) {
                return fixtures;
            }
        }
        throw new IllegalStateException("contracts/fixtures/features not found from " + working);
    }

    private static double[] high(BarSeries series) {
        return column(series, 0);
    }

    private static double[] low(BarSeries series) {
        return column(series, 1);
    }

    private static double[] close(BarSeries series) {
        return column(series, 2);
    }

    private static double[] column(BarSeries series, int which) {
        double[] values = new double[series.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = switch (which) {
                case 0 -> series.high(i);
                case 1 -> series.low(i);
                default -> series.close(i);
            };
        }
        return values;
    }
}
