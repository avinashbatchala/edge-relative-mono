package com.edgerelative.application.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.engine.FeatureContext;
import com.edgerelative.application.feature.engine.FeatureEngine;
import com.edgerelative.application.history.AggregatedCandle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Adversarial point-in-time tests: appending large future values after an anchor must not change the
 * snapshot that would have been produced at the anchor. This is release-blocking (DD-05 §32, §151).
 */
class FeatureLeakageTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 9, 3);
    private static final LocalDate D4 = LocalDate.of(2026, 9, 4);
    private static final int ANCHOR_INDEX = 20;

    @Test
    void rrsRvolRveAndContextAreUnchangedByFutureBars() {
        List<AggregatedCandle> subjectPrefix = new ArrayList<>();
        subjectPrefix.addAll(FeatureTestSupport.session(D1, 100));
        subjectPrefix.addAll(FeatureTestSupport.session(D2, 100));
        subjectPrefix.addAll(FeatureTestSupport.session(D3, 100).subList(0, ANCHOR_INDEX + 1));
        List<AggregatedCandle> subjectFuture = new ArrayList<>(subjectPrefix);
        subjectFuture.addAll(FeatureTestSupport.session(D3, 100).subList(ANCHOR_INDEX + 1, 75));
        subjectFuture.addAll(FeatureTestSupport.session(D4, 10_000));

        List<AggregatedCandle> marketPrefix = new ArrayList<>();
        marketPrefix.addAll(FeatureTestSupport.session(D1, 500));
        marketPrefix.addAll(FeatureTestSupport.session(D2, 500));
        marketPrefix.addAll(FeatureTestSupport.session(D3, 500).subList(0, ANCHOR_INDEX + 1));
        List<AggregatedCandle> marketFuture = new ArrayList<>(marketPrefix);
        marketFuture.addAll(FeatureTestSupport.session(D3, 500).subList(ANCHOR_INDEX + 1, 75));
        marketFuture.addAll(FeatureTestSupport.session(D4, 900_000));

        FeatureContext prefix = FeatureTestSupport.context(1, subjectPrefix, marketPrefix, List.of(), FeatureTestSupport.policy(), "M5");
        FeatureContext future = FeatureTestSupport.context(1, subjectFuture, marketFuture, List.of(), FeatureTestSupport.policy(), "M5");

        FeatureEngine engine = new FeatureEngine();
        FeatureSnapshot atAnchor = engine.snapshot(prefix);
        FeatureSnapshot afterFuture = at(engine, future, atAnchor.anchorTimestamp());

        assertThat(afterFuture).isEqualTo(atAnchor);
    }

    private static FeatureSnapshot at(FeatureEngine engine, FeatureContext context, java.time.Instant anchor) {
        return engine.snapshots(context).stream()
                .filter(snapshot -> snapshot.anchorTimestamp().equals(anchor))
                .findFirst()
                .orElseThrow();
    }
}
