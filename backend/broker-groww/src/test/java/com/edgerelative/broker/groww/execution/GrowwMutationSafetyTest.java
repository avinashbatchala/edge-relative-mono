package com.edgerelative.broker.groww.execution;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgerelative.broker.api.error.BrokerOperationNotEnabledException;
import com.edgerelative.broker.api.model.BrokerCancelOrderRequest;
import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerModifyOrderRequest;
import com.edgerelative.broker.api.model.BrokerOrderRequest;
import com.edgerelative.broker.api.model.BrokerOrderType;
import com.edgerelative.broker.api.model.BrokerProduct;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerSmartOrderModifyRequest;
import com.edgerelative.broker.api.model.BrokerSmartOrderRequest;
import com.edgerelative.broker.api.model.BrokerSmartOrderType;
import com.edgerelative.broker.api.model.BrokerTransactionType;
import com.edgerelative.broker.api.model.BrokerValidity;
import com.edgerelative.broker.groww.support.GrowwTestFixture;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves that no broker-side mutation can reach Groww in this change.
 */
class GrowwMutationSafetyTest {

    private GrowwTestFixture fixture;
    private final GrowwDisabledExecutionAdapter adapter = new GrowwDisabledExecutionAdapter();

    @BeforeEach
    void setUp() {
        fixture = new GrowwTestFixture();
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void everyMutationIsRefusedAndEmitsZeroDownstreamRequests() {
        assertThatThrownBy(() -> adapter.placeOrder(new BrokerOrderRequest(
                "RELIANCE", 1, new BigDecimal("2500"), null, BrokerValidity.DAY, BrokerExchange.NSE,
                BrokerSegment.CASH, BrokerProduct.CNC, BrokerOrderType.LIMIT, BrokerTransactionType.BUY,
                "ref-12345")))
                .isInstanceOf(BrokerOperationNotEnabledException.class)
                .satisfies(e -> assertThat(((BrokerOperationNotEnabledException) e).brokerErrorCode())
                        .isEqualTo(BrokerOperationNotEnabledException.CODE));

        assertThatThrownBy(() -> adapter.modifyOrder(new BrokerModifyOrderRequest(
                "GMK1", 1L, new BigDecimal("2500"), null, BrokerOrderType.LIMIT, BrokerSegment.CASH)))
                .isInstanceOf(BrokerOperationNotEnabledException.class);

        assertThatThrownBy(() -> adapter.cancelOrder(new BrokerCancelOrderRequest("GMK1", BrokerSegment.CASH)))
                .isInstanceOf(BrokerOperationNotEnabledException.class);

        assertThatThrownBy(() -> adapter.createSmartOrder(new BrokerSmartOrderRequest(
                BrokerSmartOrderType.GTT, "ref-12345", BrokerExchange.NSE, BrokerSegment.CASH,
                "RELIANCE", 1, BrokerProduct.CNC, BrokerValidity.DAY, new BigDecimal("2500"), "UP",
                new BigDecimal("2500"), BrokerOrderType.LIMIT, BrokerTransactionType.SELL, null, null,
                null, null)))
                .isInstanceOf(BrokerOperationNotEnabledException.class);

        assertThatThrownBy(() -> adapter.modifySmartOrder(
                "gtt_1",
                new BrokerSmartOrderModifyRequest(
                        BrokerSmartOrderType.GTT, BrokerSegment.CASH, 2L, new BigDecimal("2500"), "UP",
                        new BigDecimal("2500"), BrokerOrderType.LIMIT, null, null, null, null)))
                .isInstanceOf(BrokerOperationNotEnabledException.class);

        assertThatThrownBy(() -> adapter.cancelSmartOrder(
                BrokerSegment.CASH, BrokerSmartOrderType.GTT, "gtt_1"))
                .isInstanceOf(BrokerOperationNotEnabledException.class);

        fixture.server().verify(0, anyRequestedFor(anyUrl()));
    }
}
