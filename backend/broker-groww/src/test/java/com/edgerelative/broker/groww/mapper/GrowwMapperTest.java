package com.edgerelative.broker.groww.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgerelative.broker.api.error.BrokerProtocolException;
import com.edgerelative.broker.api.model.BrokerOrder;
import com.edgerelative.broker.api.model.BrokerOrderStatus;
import com.edgerelative.broker.api.model.BrokerOrderType;
import com.edgerelative.broker.api.model.BrokerProduct;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerTransactionType;
import com.edgerelative.broker.groww.dto.response.GrowwOrderResponse;
import com.edgerelative.broker.groww.resilience.GrowwOperation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GrowwMapperTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final GrowwMapper mapper = new GrowwMapper(json);

    @Test
    void mapsAnOrderAndPreservesMonetaryPrecision() {
        GrowwOrderResponse dto = new GrowwOrderResponse(
                "GMK1",
                "RELIANCE",
                "EXECUTED",
                "ok",
                100L,
                number("2500.55"),
                number("2450"),
                100L,
                0L,
                number("2500.55"),
                0L,
                "NA",
                "DAY",
                "NSE",
                "MARKET",
                "BUY",
                "CASH",
                "CNC",
                "2023-10-01T10:15:30",
                "2023-10-01T10:15:30",
                "2024-08-24T14:15:22Z",
                "ref-1");

        BrokerOrder order = mapper.toOrder(dto);

        assertThat(order.brokerOrderId()).isEqualTo("GMK1");
        assertThat(order.exchange().name()).isEqualTo("NSE");
        assertThat(order.segment()).isEqualTo(BrokerSegment.CASH);
        assertThat(order.transactionType()).isEqualTo(BrokerTransactionType.BUY);
        assertThat(order.orderType()).isEqualTo(BrokerOrderType.MARKET);
        assertThat(order.product()).isEqualTo(BrokerProduct.CNC);
        assertThat(order.status()).isEqualTo(BrokerOrderStatus.EXECUTED);
        assertThat(order.price()).isEqualByComparingTo(new BigDecimal("2500.55"));
        assertThat(order.createdAt()).isEqualTo(Instant.parse("2023-10-01T04:45:30Z"));
        assertThat(order.tradeDate()).isEqualTo(Instant.parse("2024-08-24T14:15:22Z"));
    }

    @Test
    void unknownOrderStatusDegradesSafely() {
        GrowwOrderResponse dto = new GrowwOrderResponse(
                "x", "s", "SOME_FUTURE_STATUS", null, 1L, null, null, null, null, null, null, null, null, "NSE",
                "MARKET", "BUY", "CASH", "CNC", null, null, null, null);
        assertThat(mapper.toOrder(dto).status()).isEqualTo(BrokerOrderStatus.UNKNOWN);
    }

    @Test
    void mapsKnownExchangesIncludingCommodity() {
        assertThat(mapper.exchange("NSE").name()).isEqualTo("NSE");
        assertThat(mapper.exchange("BSE").name()).isEqualTo("BSE");
        assertThat(mapper.exchange("MCX").name()).isEqualTo("MCX");
    }

    @Test
    void unknownExchangeFailsObservably() {
        assertThatThrownBy(() -> mapper.exchange("NYSE"))
                .isInstanceOf(BrokerProtocolException.class)
                .hasMessageContaining("exchange");
    }

    @Test
    void parsesStringifiedOhlc() {
        JsonNode node = json.readTree("\"{open: 149.50,high: 150.50,low: 148.50,close: 149.50}\"");
        var ohlc = mapper.toOhlc(node);
        assertThat(ohlc.open()).isEqualByComparingTo(new BigDecimal("149.50"));
        assertThat(ohlc.close()).isEqualByComparingTo(new BigDecimal("149.50"));
    }

    @Test
    void parsesDocumentedTimestampFormats() {
        assertThat(mapper.instant("2024-08-24T14:15:22Z")).isEqualTo(Instant.parse("2024-08-24T14:15:22Z"));
        // Zone-less Groww timestamps are exchange-local (IST), not UTC.
        assertThat(mapper.instant("2023-10-01T10:15:30")).isEqualTo(Instant.parse("2023-10-01T04:45:30Z"));
        assertThat(mapper.instant("2023-10-01 10:15:30")).isEqualTo(Instant.parse("2023-10-01T04:45:30Z"));
        assertThat(mapper.instant((String) null)).isNull();
    }

    @Test
    void mapsValidCandleRows() {
        JsonNode good = json.readTree("[\"2025-09-24T10:30:00\", 1.5, 2.0, 1.0, 1.8, 1000]");
        assertThat(mapper.toCandle(List.of(
                        good.get(0), good.get(1), good.get(2), good.get(3), good.get(4), good.get(5))))
                .isNotNull();
    }

    @Test
    void allowsMissingOpenForDailyEquityCandles() {
        JsonNode row = json.readTree(
                "[\"2026-09-01T00:00:00\", null, 1311.7, 1280.0, 1309.0, 24706257, null]");
        var candle = mapper.toCandle(List.of(
                row.get(0), row.get(1), row.get(2), row.get(3), row.get(4), row.get(5), row.get(6)));

        assertThat(candle).isNotNull();
        assertThat(candle.open()).isNull();
        assertThat(candle.high()).isEqualByComparingTo("1311.7");
        assertThat(candle.close()).isEqualByComparingTo("1309.0");
    }

    @Test
    void returnsNullForUnusableCandleRowsSoOneBadRowDoesNotFailTheSeries() {
        JsonNode shortRow = json.readTree("[\"2025-09-24T10:30:00\", 1.5]");
        assertThat(mapper.toCandle(List.of(shortRow.get(0), shortRow.get(1)))).isNull();

        JsonNode nullPrices = json.readTree("[\"2025-09-24T10:30:00\", null, null, null, null, 0]");
        assertThat(mapper.toCandle(List.of(
                        nullPrices.get(0),
                        nullPrices.get(1),
                        nullPrices.get(2),
                        nullPrices.get(3),
                        nullPrices.get(4),
                        nullPrices.get(5))))
                .isNull();

        JsonNode stringPrice = json.readTree("[\"2025-09-24T10:30:00\", 1.5, \"not-a-price\", 1.0, 1.8, 1000]");
        assertThat(mapper.toCandle(List.of(
                        stringPrice.get(0),
                        stringPrice.get(1),
                        stringPrice.get(2),
                        stringPrice.get(3),
                        stringPrice.get(4),
                        stringPrice.get(5))))
                .isNull();
    }

    private JsonNode number(String value) {
        return json.readTree(value);
    }
}
