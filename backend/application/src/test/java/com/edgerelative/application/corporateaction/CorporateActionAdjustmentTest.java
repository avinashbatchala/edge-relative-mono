package com.edgerelative.application.corporateaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.corporateaction.domain.CorporateActionFactor;
import com.edgerelative.application.corporateaction.math.CorporateActionAdjustment;
import com.edgerelative.application.corporateaction.math.CorporateActionAdjustment.AdjustedCandle;
import com.edgerelative.application.history.AggregatedCandle;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Independent checks of the back-adjustment math against hand-derived factors.
 */
class CorporateActionAdjustmentTest {

    private static AggregatedCandle bar(String openIso, String closeIso, String price, long volume) {
        BigDecimal p = new BigDecimal(price);
        return new AggregatedCandle(
                Instant.parse(openIso),
                Instant.parse(closeIso),
                p,
                p.add(new BigDecimal("1")),
                p.subtract(new BigDecimal("1")),
                p,
                volume,
                null,
                null,
                null,
                false,
                true,
                "GOOD",
                "er-aggregate-v1");
    }

    private static CorporateActionFactor split(LocalDate exDate) {
        return new CorporateActionFactor(
                1L, 1L, "SPLIT", exDate, new BigDecimal("0.5"), new BigDecimal("2"), Instant.parse("2026-09-01T00:00:00Z"), "v1");
    }

    @Test
    void splitOfOneIntoTwoHalvesPricesAndDoublesVolumeBeforeTheExDateOnly() {
        List<AggregatedCandle> raw = List.of(
                bar("2026-09-15T03:45:00Z", "2026-09-15T03:46:00Z", "100", 10),
                bar("2026-09-16T03:45:00Z", "2026-09-16T03:46:00Z", "50", 20),
                bar("2026-09-17T03:45:00Z", "2026-09-17T03:46:00Z", "51", 20));

        List<AdjustedCandle> adjusted = CorporateActionAdjustment.backAdjust(raw, List.of(split(LocalDate.of(2026, 9, 16))));

        // Before ex-date: 100 -> 50, 10 -> 20, factor 0.5, adjusted definition.
        AdjustedCandle before = adjusted.get(0);
        assertThat(before.candle().open()).isEqualByComparingTo("50");
        assertThat(before.candle().high()).isEqualByComparingTo("50.5");
        assertThat(before.candle().low()).isEqualByComparingTo("49.5");
        assertThat(before.candle().close()).isEqualByComparingTo("50");
        assertThat(before.candle().volume()).isEqualTo(20);
        assertThat(before.candle().definitionVersion()).isEqualTo("er-ca-adjusted-v1");
        assertThat(before.cumulativePriceFactor()).isEqualByComparingTo("0.5");

        // On/after ex-date: unchanged, factor 1.
        assertThat(adjusted.get(1).candle().close()).isEqualByComparingTo("50");
        assertThat(adjusted.get(1).candle().volume()).isEqualTo(20);
        assertThat(adjusted.get(1).cumulativePriceFactor()).isEqualByComparingTo("1");
        assertThat(adjusted.get(2).candle().close()).isEqualByComparingTo("51");
    }

    @Test
    void successiveActionsComposeExactlyOnceEach() {
        List<AggregatedCandle> raw = List.of(bar("2026-09-10T03:45:00Z", "2026-09-10T03:46:00Z", "100", 10));
        List<CorporateActionFactor> factors = List.of(
                split(LocalDate.of(2026, 9, 16)), // 0.5 x 2
                new CorporateActionFactor(
                        2L, 1L, "BONUS", LocalDate.of(2026, 9, 20), new BigDecimal("0.5"), new BigDecimal("2"),
                        Instant.parse("2026-09-01T00:00:00Z"), "v1"));

        List<AdjustedCandle> adjusted = CorporateActionAdjustment.backAdjust(raw, factors);

        // Both actions are after the bar: 0.5 * 0.5 = 0.25 price, 2 * 2 = 4 volume.
        assertThat(adjusted.get(0).candle().close()).isEqualByComparingTo("25");
        assertThat(adjusted.get(0).candle().volume()).isEqualTo(40);
        assertThat(adjusted.get(0).cumulativePriceFactor()).isEqualByComparingTo("0.25");
    }

    @Test
    void repeatedCallsAreIdenticalAndNeverMutateTheRawBars() {
        List<AggregatedCandle> raw = List.of(bar("2026-09-15T03:45:00Z", "2026-09-15T03:46:00Z", "100", 10));
        List<CorporateActionFactor> factors = List.of(split(LocalDate.of(2026, 9, 16)));

        List<AdjustedCandle> first = CorporateActionAdjustment.backAdjust(raw, factors);
        List<AdjustedCandle> second = CorporateActionAdjustment.backAdjust(raw, factors);

        // Exactly once: 0.5, not 0.25.
        assertThat(first.get(0).candle().close()).isEqualByComparingTo("50");
        assertThat(second.get(0).candle().close()).isEqualByComparingTo("50");
        assertThat(first.get(0).candle()).isEqualTo(second.get(0).candle());
        // Raw is untouched.
        assertThat(raw.get(0).close()).isEqualByComparingTo("100");
        assertThat(raw.get(0).volume()).isEqualTo(10);
    }

    @Test
    void aDividendCashFactorIsNotAMultiplicativePriceFactor() {
        // The math accepts whatever explicit factor is supplied; the fixture documents that a cash
        // dividend is additive and therefore must not be encoded as a price multiplier. This asserts
        // the split transform is multiplicative so the two are not conflated.
        List<AggregatedCandle> raw = List.of(bar("2026-09-15T03:45:00Z", "2026-09-15T03:46:00Z", "100", 10));
        CorporateActionFactor dividendLike = new CorporateActionFactor(
                2L, 1L, "DIVIDEND", LocalDate.of(2026, 9, 16), new BigDecimal("0.95"), BigDecimal.ONE,
                Instant.parse("2026-09-01T00:00:00Z"), "v1");
        List<AdjustedCandle> adjusted = CorporateActionAdjustment.backAdjust(raw, List.of(dividendLike));
        // A 5% additive cash adjustment is represented here as (P-5)/P; quantity is unchanged.
        assertThat(adjusted.get(0).candle().close()).isEqualByComparingTo("95");
        assertThat(adjusted.get(0).candle().volume()).isEqualTo(10);
    }
}
