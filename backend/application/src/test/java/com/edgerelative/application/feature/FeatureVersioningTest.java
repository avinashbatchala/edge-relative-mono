package com.edgerelative.application.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.feature.domain.FeatureKeys;
import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.engine.FeatureContext;
import com.edgerelative.application.feature.engine.FeatureEngine;
import com.edgerelative.application.feature.math.AtrSmoothing;
import com.edgerelative.application.feature.policy.FeaturePolicy;
import com.edgerelative.application.feature.policy.FeatureVersions;
import com.edgerelative.application.history.AggregatedCandle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Feature versions are immutable in meaning; a parameter change is a distinct, distinguishable set.
 */
class FeatureVersioningTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);

    private static FeaturePolicy policyWithAtr(int atrLength) {
        return new FeaturePolicy(
                120,
                new FeaturePolicy.Benchmark("NIFTY50"),
                new FeaturePolicy.Atr(Map.of("M5", atrLength), atrLength, AtrSmoothing.WILDER),
                FeatureTestSupport.policy().rrs(),
                FeatureTestSupport.policy().rvol(),
                FeatureTestSupport.policy().rve(),
                FeatureTestSupport.policy().structure(),
                FeatureTestSupport.policy().directionalVolume());
    }

    @Test
    void sameParametersProduceTheSameVersionAndHash() {
        FeatureVersions versions = new FeatureVersions(policyWithAtr(3));
        assertThat(versions.rrs(FeatureKeys.RRS_RAW, "M5", "NIFTY50"))
                .isEqualTo(versions.rrs(FeatureKeys.RRS_RAW, "M5", "NIFTY50"));
    }

    @Test
    void changingAParameterKeepsTheSemanticNameButChangesTheHash() {
        FeatureVersions v1 = new FeatureVersions(policyWithAtr(3));
        FeatureVersions v2 = new FeatureVersions(policyWithAtr(7));
        var version1 = v1.rrs(FeatureKeys.RRS_RAW, "M5", "NIFTY50");
        var version2 = v2.rrs(FeatureKeys.RRS_RAW, "M5", "NIFTY50");
        assertThat(version1.semanticVersion()).isEqualTo("RRS_V1");
        assertThat(version2.semanticVersion()).isEqualTo("RRS_V1");
        assertThat(version1.parameterHash()).isNotEqualTo(version2.parameterHash());
    }

    @Test
    void reconstructionIsStableAndV1IsUnaffectedByAV2Rebuild() {
        FeatureEngine engine = new FeatureEngine();
        FeatureContext v1Context = context(policyWithAtr(3));
        FeatureSnapshot first = engine.snapshot(v1Context);
        FeatureSnapshot second = engine.snapshot(v1Context);
        assertThat(first).isEqualTo(second);

        FeatureContext v2Context = context(policyWithAtr(7));
        FeatureSnapshot v2Snapshot = engine.snapshot(v2Context);
        assertThat(v2Snapshot.feature(FeatureKeys.RRS_RAW).version().parameterHash())
                .isNotEqualTo(first.feature(FeatureKeys.RRS_RAW).version().parameterHash());
        assertThat(engine.snapshot(v1Context)).isEqualTo(first);
    }

    private static FeatureContext context(FeaturePolicy policy) {
        List<AggregatedCandle> subject = new ArrayList<>();
        subject.addAll(FeatureTestSupport.session(D1, 100));
        subject.addAll(FeatureTestSupport.session(D2, 120));
        List<AggregatedCandle> market = new ArrayList<>();
        market.addAll(FeatureTestSupport.session(D1, 500));
        market.addAll(FeatureTestSupport.session(D2, 520));
        return FeatureTestSupport.context(1, subject, market, List.of(), policy, "M5");
    }
}
