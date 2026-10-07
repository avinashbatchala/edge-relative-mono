package com.edgerelative.application.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.engine.FeatureContext;
import com.edgerelative.application.feature.engine.FeatureContextTemplate;
import com.edgerelative.application.feature.engine.FeatureEngine;
import com.edgerelative.application.feature.engine.LiveFeatureStore;
import com.edgerelative.application.feature.policy.FeaturePolicy;
import com.edgerelative.application.feature.policy.FeatureVersions;
import com.edgerelative.application.history.AggregatedCandle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Live and replay must use the same engine and produce identical snapshots (DD-05 §29/§124).
 */
class FeatureReplayParityTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);

    @Test
    void incrementalLiveFeedMatchesHistoricalReplay() {
        FeaturePolicy policy = FeatureTestSupport.policy();
        List<AggregatedCandle> subject = new ArrayList<>();
        subject.addAll(FeatureTestSupport.session(D1, 100));
        subject.addAll(FeatureTestSupport.session(D2, 120));
        List<AggregatedCandle> market = new ArrayList<>();
        market.addAll(FeatureTestSupport.session(D1, 500));
        market.addAll(FeatureTestSupport.session(D2, 520));

        FeatureEngine engine = new FeatureEngine();
        FeatureSnapshot replay = engine.snapshot(
                FeatureTestSupport.context(1, subject, market, List.of(), policy, "M5"));

        LiveFeatureStore store = new LiveFeatureStore(engine, 1000);
        store.seed(99L, "M5", market);
        FeatureContextTemplate template = new FeatureContextTemplate(
                1L, "M5", 99L, "NIFTY50", null, null, null, null, null,
                policy, new FeatureVersions(policy), FeatureTestSupport.CALENDAR);
        FeatureSnapshot live = null;
        for (AggregatedCandle candle : subject) {
            live = store.accept(template, candle);
        }

        assertThat(live).isEqualTo(replay);
    }

    @Test
    void identicalInputsProduceIdenticalSnapshotsAcrossRuns() {
        FeaturePolicy policy = FeatureTestSupport.policy();
        FeatureContext context = FeatureTestSupport.context(
                1,
                combine(FeatureTestSupport.session(D1, 100), FeatureTestSupport.session(D2, 120)),
                combine(FeatureTestSupport.session(D1, 500), FeatureTestSupport.session(D2, 520)),
                List.of(),
                policy,
                "M5");
        FeatureEngine engine = new FeatureEngine();
        assertThat(engine.snapshot(context)).isEqualTo(engine.snapshot(context));
    }

    private static List<AggregatedCandle> combine(List<AggregatedCandle> left, List<AggregatedCandle> right) {
        List<AggregatedCandle> result = new ArrayList<>(left);
        result.addAll(right);
        return result;
    }
}
