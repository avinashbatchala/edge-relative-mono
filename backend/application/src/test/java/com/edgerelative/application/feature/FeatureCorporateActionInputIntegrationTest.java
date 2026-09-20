package com.edgerelative.application.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.corporateaction.persistence.CorporateActionFactorRepository;
import com.edgerelative.application.feature.domain.FeatureKeys;
import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.service.FeatureSnapshotService;
import com.edgerelative.application.history.HistoryRepository;
import com.edgerelative.application.history.NewCandle;
import com.edgerelative.application.reference.CanonicalInstrumentService;
import com.edgerelative.application.reference.NseTradingCalendar;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * With {@code feature.corporate-actions.adjusted-inputs=true}, features read the split/bonus-adjusted
 * analytical series: an identical series carrying a 1:2 split has half the ATR, while RRS (a ratio of
 * two scaled quantities) is unchanged, demonstrating cross-action continuity.
 */
@SpringBootTest
@Testcontainers
class FeatureCorporateActionInputIntegrationTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);
    private static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-bookworm")
            .withDatabaseName("feature_ca")
            .withUsername("feature_ca")
            .withPassword("feature_ca");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_HOST", POSTGRES::getHost);
        registry.add("POSTGRES_PORT", POSTGRES::getFirstMappedPort);
        registry.add("POSTGRES_DB", POSTGRES::getDatabaseName);
        registry.add("POSTGRES_USER", POSTGRES::getUsername);
        registry.add("POSTGRES_PASSWORD", POSTGRES::getPassword);
        registry.add("broker.groww.base-url", WIREMOCK::baseUrl);
        registry.add("broker.groww.credentials.mode", () -> "ACCESS_TOKEN");
        registry.add("broker.groww.credentials.access-token", () -> "test-token");
        registry.add("broker.groww.retry.max-attempts", () -> "1");
        registry.add("feature.history-days", () -> "30");
        registry.add("feature.corporate-actions.adjusted-inputs", () -> "true");
        registry.add("feature.rvol.min-samples", () -> "1");
        registry.add("feature.rvol.daily-lookback", () -> "5");
        registry.add("feature.rvol.interval-lookback", () -> "5");
        registry.add("feature.rvol.cumulative-lookback", () -> "5");
        registry.add("feature.atr.default-length", () -> "3");
        registry.add("feature.atr.length-by-timeframe.M1", () -> "3");
        registry.add("feature.atr.length-by-timeframe.M5", () -> "3");
        registry.add("feature.rrs.fast-length", () -> "1");
        registry.add("feature.rrs.slow-length", () -> "2");
        registry.add("feature.rrs.persistence-window", () -> "2");
        registry.add("feature.rrs.slope-lookback", () -> "1");
        registry.add("feature.rrs.percentile-window", () -> "10");
        registry.add("feature.rve.fast-length", () -> "1");
        registry.add("feature.rve.slow-length", () -> "2");
        registry.add("feature.live.max-bars", () -> "2000");
        registry.add("feature.dashboard.cache-ttl", () -> "0s");
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @Autowired
    private NseTradingCalendar calendar;

    @Autowired
    private CanonicalInstrumentService canonical;

    @Autowired
    private HistoryRepository history;

    @Autowired
    private CorporateActionFactorRepository corporateActions;

    @Autowired
    private FeatureSnapshotService service;

    private long instrument(String symbol, String type) {
        return canonical.ensureInstrument("NSE", "CASH", type, symbol, symbol, null, null);
    }

    private void seed(long instrumentId) {
        long timeframeId = canonical.ensureTimeframe("M1");
        List<NewCandle> candles = new ArrayList<>();
        for (LocalDate date : List.of(D1, D2)) {
            Instant open = calendar.sessionOpen(date);
            for (int minute = 0; minute < calendar.sessionMinutes(); minute++) {
                Instant barOpen = open.plusSeconds(60L * minute);
                double price = (date == D1 ? 100.0 : 100.5) + minute * 0.001;
                candles.add(new NewCandle(
                        barOpen,
                        BigDecimal.valueOf(price),
                        BigDecimal.valueOf(price + 0.002),
                        BigDecimal.valueOf(price - 0.002),
                        BigDecimal.valueOf(price),
                        100,
                        null));
            }
        }
        history.upsertCandles(instrumentId, timeframeId, candles, 1000);
    }

    @Test
    void adjustedInputsHalveAtrAndLeaveScaleInvariantRrsUnchanged() {
        long raw = instrument("CAFEATRAW" + System.nanoTime(), "EQUITY");
        long adjusted = instrument("CAFEATADJ" + System.nanoTime(), "EQUITY");
        long benchmark = canonical.ensureInstrument("NSE", "CASH", "INDEX", "NIFTY", "NIFTY 50", null, null);
        seed(raw);
        seed(adjusted);
        seed(benchmark);

        // A 1:2 split after all seeded bars: all pre-ex prices halve, quantities double.
        long actionId = corporateActions.insertAction(
                adjusted, "SPLIT", LocalDate.of(2026, 9, 10), null, null,
                BigDecimal.ONE, new BigDecimal("2"), null, null, "test");
        corporateActions.insertFactor(
                actionId, adjusted, new BigDecimal("0.5"), new BigDecimal("2"),
                Instant.parse("2026-08-01T00:00:00Z"), "er-ca-factor-v1");

        Instant anchor = calendar.sessionClose(D2);
        FeatureSnapshot rawSnapshot = service.snapshot(raw, "M5", anchor, false);
        FeatureSnapshot adjustedSnapshot = service.snapshot(adjusted, "M5", anchor, false);

        double rawAtr = rawSnapshot.feature(FeatureKeys.ATR).value();
        double adjustedAtr = adjustedSnapshot.feature(FeatureKeys.ATR).value();
        assertThat(adjustedAtr).isCloseTo(rawAtr * 0.5, org.assertj.core.data.Offset.offset(1e-6));

        double rawRrs = rawSnapshot.feature(FeatureKeys.RRS_RAW).value();
        double adjustedRrs = adjustedSnapshot.feature(FeatureKeys.RRS_RAW).value();
        assertThat(adjustedRrs).isCloseTo(rawRrs, org.assertj.core.data.Offset.offset(1e-6));
    }
}
