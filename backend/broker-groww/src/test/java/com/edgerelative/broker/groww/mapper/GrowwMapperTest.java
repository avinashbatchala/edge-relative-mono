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
        assertThat(order.createdAt()).isEqualTo(Instant.parse("2023-10-01T10:15:30Z"));
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
        assertThat(mapper.instant("2023-10-01T10:15:30")).isEqualTo(Instant.parse("2023-10-01T10:15:30Z"));
        assertThat(mapper.instant("2023-10-01 10:15:30")).isEqualTo(Instant.parse("2023-10-01T10:15:30Z"));
        assertThat(mapper.instant((String) null)).isNull();
    }

    @Test
    void candleRowsAreValidatedForElementCount() {
        JsonNode good = json.readTree("[\"2025-09-24T10:30:00\", 1.5, 2.0, 1.0, 1.8, 1000]");
        assertThat(mapper.toCandle(List.of(
                        good.get(0), good.get(1), good.get(2), good.get(3), good.get(4), good.get(5)),
                GrowwOperation.HISTORICAL_CANDLES))
                .isNotNull();

        assertThatThrownBy(() -> mapper.toCandle(List.of(good.get(0), good.get(1)), GrowwOperation.HISTORICAL_CANDLES))
                .isInstanceOf(BrokerProtocolException.class)
                .hasMessageContaining("fewer than 6");
    }

    private JsonNode number(String value) {
        return json.readTree(value);
    }
}
