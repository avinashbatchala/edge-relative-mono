package com.edgerelative.broker.groww.client;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgerelative.broker.api.error.BrokerNotFoundException;
import com.edgerelative.broker.api.error.BrokerValidationException;
import com.edgerelative.broker.api.model.BrokerCandleInterval;
import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerLtp;
import com.edgerelative.broker.api.model.BrokerMargin;
import com.edgerelative.broker.api.model.BrokerMarginOrder;
import com.edgerelative.broker.api.model.BrokerOrder;
import com.edgerelative.broker.api.model.BrokerOrderType;
import com.edgerelative.broker.api.model.BrokerPosition;
import com.edgerelative.broker.api.model.BrokerProduct;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerSmartOrderType;
import com.edgerelative.broker.api.model.BrokerTransactionType;
import com.edgerelative.broker.api.model.HistoricalCandleRequest;
import com.edgerelative.broker.api.model.OrderListQuery;
import com.edgerelative.broker.api.model.SmartOrderListQuery;
import com.edgerelative.broker.api.model.TradeListQuery;
import com.edgerelative.broker.groww.support.GrowwTestFixture;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrowwReadClientsIntegrationTest {

    private GrowwTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new GrowwTestFixture();
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void quoteUsesCorrectMethodPathHeadersAndMapsPrices() {
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/live-data/quote"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{
                                  "last_price":2500.55,"day_change":10.5,"bid_price":2500.4,
                                  "ohlc":{"open":2490,"high":2510,"low":2480,"close":2500.55},
                                  "depth":{"buy":[{"price":2500.4,"quantity":100}],"sell":[{"price":2500.6,"quantity":80}]},
                                  "volume":123456,"last_trade_time":1727000000000}
                                }""")));

        var quote = fixture.marketData().quote(BrokerExchange.NSE, BrokerSegment.CASH, "RELIANCE");

        assertThat(quote.lastPrice()).isEqualByComparingTo(new BigDecimal("2500.55"));
        assertThat(quote.ohlc().open()).isEqualByComparingTo(new BigDecimal("2490"));
        assertThat(quote.bids()).hasSize(1);
        assertThat(quote.asks()).hasSize(1);
        assertThat(quote.volume()).isEqualTo(123456);
        fixture.server()
                .verify(getRequestedFor(urlPathEqualTo("/v1/live-data/quote"))
                        .withQueryParam("exchange", equalTo("NSE"))
                        .withQueryParam("segment", equalTo("CASH"))
                        .withQueryParam("trading_symbol", equalTo("RELIANCE"))
                        .withHeader("Authorization", equalTo("Bearer test-token"))
                        .withHeader("X-API-VERSION", equalTo("1.0")));
    }

    @Test
    void ltpBatchesMultipleSymbolsInOneRequest() {
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/live-data/ltp"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"NSE_RELIANCE":2500.55,"BSE_SENSEX":79500.25}}""")));

        List<BrokerLtp> prices =
                fixture.marketData().lastTradedPrices(BrokerSegment.CASH, List.of("NSE_RELIANCE", "BSE_SENSEX"));

        assertThat(prices).hasSize(2);
        assertThat(prices.get(0).lastPrice()).isEqualByComparingTo(new BigDecimal("2500.55"));
        fixture.server()
                .verify(1, getRequestedFor(urlPathEqualTo("/v1/live-data/ltp"))
                        .withQueryParam("exchange_symbols", containing("NSE_RELIANCE")));
    }

    @Test
    void batchingMoreThanFiftySymbolsIsRejectedWithoutCallingGroww() {
        List<String> tooMany = java.util.stream.IntStream.range(0, 51)
                .mapToObj(i -> "NSE_SYM" + i)
                .toList();

        assertThatThrownBy(() -> fixture.marketData().lastTradedPrices(BrokerSegment.CASH, tooMany))
                .isInstanceOf(BrokerValidationException.class);
        fixture.server().verify(0, getRequestedFor(urlPathEqualTo("/v1/live-data/ltp")));
    }

    @Test
    void orderStatusDetailAndReferenceMapCorrectly() {
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/order/status/GMK1"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"groww_order_id":"GMK1","order_status":"EXECUTED",
                                 "filled_quantity":100,"order_reference_id":"ref-1"}}""")));
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/order/status/reference/ref-1"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"groww_order_id":"GMK1","order_status":"EXECUTED",
                                 "order_reference_id":"ref-1"}}""")));
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/order/detail/GMK1"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"groww_order_id":"GMK1","trading_symbol":"RELIANCE",
                                 "order_status":"EXECUTED","quantity":100,"price":2500,"filled_quantity":100,
                                 "average_fill_price":2500,"exchange":"NSE","order_type":"MARKET",
                                 "transaction_type":"BUY","segment":"CASH","product":"CNC"}}""")));

        BrokerOrder status = fixture.orderQuery().orderStatus("GMK1", BrokerSegment.CASH);
        BrokerOrder byReference = fixture.orderQuery().orderStatusByReference("ref-1", BrokerSegment.CASH);
        BrokerOrder detail = fixture.orderQuery().orderDetail("GMK1", BrokerSegment.CASH);

        assertThat(status.brokerOrderId()).isEqualTo("GMK1");
        assertThat(byReference.orderReferenceId()).isEqualTo("ref-1");
        assertThat(detail.tradingSymbol()).isEqualTo("RELIANCE");
        assertThat(detail.averageFillPrice()).isEqualByComparingTo("2500");
    }

    @Test
    void orderListRejectsPageSizeAboveDocumentedMaximumWithoutCallingGroww() {
        assertThatThrownBy(() -> fixture.orderQuery()
                .orders(new OrderListQuery(BrokerSegment.CASH, 0, 101)))
                .isInstanceOf(BrokerValidationException.class);
        fixture.server().verify(0, getRequestedFor(urlPathEqualTo("/v1/order/list")));
    }

    @Test
    void tradesRejectPageSizeAboveFifty() {
        assertThatThrownBy(() -> fixture.orderQuery()
                .trades("GMK1", new TradeListQuery(BrokerSegment.CASH, 0, 51)))
                .isInstanceOf(BrokerValidationException.class);
    }

    @Test
    void notFoundErrorIsTyped() {
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/order/detail/missing"))
                        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                                .withStatus(404)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {"status":"FAILURE","error":{"code":"GA004","message":"Requested entity does not exist"}}""")));

        assertThatThrownBy(() -> fixture.orderQuery().orderDetail("missing", BrokerSegment.CASH))
                .isInstanceOf(BrokerNotFoundException.class);
    }

    @Test
    void holdingsPositionsAndProfileMap() {
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/holdings/user"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"holdings":[{"isin":"INE002A01018",
                                 "trading_symbol":"RELIANCE","quantity":10,"average_price":2000.25}]}}""")));
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/positions/user"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"positions":[{"trading_symbol":"RELIANCE",
                                 "segment":"CASH","exchange":"NSE","quantity":10,"net_price":2000,"product":"CNC"}]}}""")));
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/user/detail"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"vendor_user_id":"u1","ucc":"UCC1",
                                 "nse_enabled":true,"bse_enabled":false,"ddpi_enabled":true,
                                 "active_segments":["CASH","FNO","COMMODITY"]}}""")));

        assertThat(fixture.portfolio().holdings()).hasSize(1);
        List<BrokerPosition> positions = fixture.portfolio().positions(BrokerSegment.CASH);
        assertThat(positions).hasSize(1);
        assertThat(positions.get(0).netPrice()).isEqualByComparingTo("2000");
        assertThat(positions.get(0).quantity()).isEqualTo(10);
        assertThat(fixture.portfolio().userProfile().activeSegments())
                .containsExactlyInAnyOrder(BrokerSegment.CASH, BrokerSegment.FNO);
    }

    @Test
    void requiredMarginPostsBasketAndMaps() {
        fixture.server()
                .stubFor(post(urlPathEqualTo("/v1/margins/detail/orders"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"total_requirement":12345.67,
                                 "span_required":100,"exposure_required":50}}""")));

        var requirement = fixture.margin().requiredMargin(
                BrokerSegment.FNO,
                List.of(new BrokerMarginOrder(
                        "NIFTY-FUT", 50, new BigDecimal("25000"), BrokerExchange.NSE, BrokerSegment.FNO,
                        BrokerProduct.NRML, BrokerOrderType.LIMIT, BrokerTransactionType.BUY)));

        assertThat(requirement.totalRequirement()).isEqualByComparingTo("12345.67");
        fixture.server()
                .verify(postRequestedFor(urlPathEqualTo("/v1/margins/detail/orders"))
                        .withQueryParam("segment", equalTo("FNO"))
                        .withRequestBody(containing("NIFTY-FUT")));
    }

    @Test
    void userMarginMapsNestedDetails() {
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/margins/detail/user"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"clear_cash":1000.5,"net_margin_used":200.25,
                                 "fno_margin_details":{"span_margin_used":100},
                                 "equity_margin_details":{"cnc_margin_used":50}}}""")));
        BrokerMargin margin = fixture.margin().userMargin();
        assertThat(margin.clearCash()).isEqualByComparingTo("1000.5");
        assertThat(margin.fno().spanMarginUsed()).isEqualByComparingTo("100");
        assertThat(margin.equity().cncMarginUsed()).isEqualByComparingTo("50");
    }

    @Test
    void historicalCandlesMapAndSortAscending() {
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/historical/candles"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"candles":[
                                  ["2025-09-24T10:35:00",101,102,100,101.5,900],
                                  ["2025-09-24T10:30:00",100,101,99,100.5,1000]
                                ],"closing_price":101.5}}""")));

        var series = fixture.historical().candles(new HistoricalCandleRequest(
                BrokerExchange.NSE, BrokerSegment.CASH, "NSE-RELIANCE",
                Instant.parse("2025-09-24T10:00:00Z"), Instant.parse("2025-09-24T11:00:00Z"),
                BrokerCandleInterval.FIVE_MINUTE));

        assertThat(series.candles()).hasSize(2);
        assertThat(series.candles().get(0).openTime()).isBefore(series.candles().get(1).openTime());
        // Groww's "10:30:00" is exchange-local (IST), i.e. 05:00:00Z.
        assertThat(series.candles().get(0).openTime()).isEqualTo(Instant.parse("2025-09-24T05:00:00Z"));
        assertThat(series.candles().get(0).close()).isEqualByComparingTo("100.5");
    }

    @Test
    void skipsUnusableHistoricalRowsInsteadOfFailingTheWholeSeries() {
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/historical/candles"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"candles":[
                                  ["2025-09-24T10:30:00",100,101,99,100.5,1000],
                                  ["2025-09-24T10:35:00",null,null,null,null,0],
                                  ["2025-09-24T10:40:00",101,103,100,102.5,900]
                                ],"closing_price":102.5}}""")));

        var series = fixture.historical().candles(new HistoricalCandleRequest(
                BrokerExchange.NSE, BrokerSegment.CASH, "NSE-RELIANCE",
                Instant.parse("2025-09-24T10:00:00Z"), Instant.parse("2025-09-24T11:00:00Z"),
                BrokerCandleInterval.FIVE_MINUTE));

        assertThat(series.candles()).hasSize(2);
        assertThat(series.candles().get(1).close()).isEqualByComparingTo("102.5");
    }

    @Test
    void dailyEquityCandlesAllowMissingOpen() {
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/historical/candles"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"candles":[
                                  ["2026-09-01T00:00:00", null, 1311.7, 1280.0, 1309.0, 24706257, null],
                                  ["2026-09-02T00:00:00", null, 1310.0, 1290.0, 1300.0, 20000000, null]
                                ]}}""")));

        var series = fixture.historical().candles(new HistoricalCandleRequest(
                BrokerExchange.NSE, BrokerSegment.CASH, "NSE-RELIANCE",
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-03T00:00:00Z"),
                BrokerCandleInterval.ONE_DAY));

        assertThat(series.candles()).hasSize(2);
        assertThat(series.candles().get(0).open()).isNull();
        assertThat(series.candles().get(0).close()).isEqualByComparingTo("1309.0");
    }

    @Test
    void longHistoricalRangeIsSplitIntoMultipleRequests() {
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/historical/candles"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"candles":[
                                  ["2025-01-02T09:20:00",100,101,99,100.5,1000]
                                ]}}""")));

        var series = fixture.historical().candles(new HistoricalCandleRequest(
                BrokerExchange.NSE, BrokerSegment.CASH, "NSE-RELIANCE",
                Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-03-17T00:00:00Z"),
                BrokerCandleInterval.FIVE_MINUTE));

        assertThat(series.candles()).hasSize(1);
        assertThat(fixture.server().findAll(getRequestedFor(urlPathEqualTo("/v1/historical/candles"))))
                .hasSize(3);
    }

    @Test
    void expiriesAndContractsSupportBothDocumentedShapes() {
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/historical/expiries"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"expiries":["2025-10-14","2025-10-21"]}}""")));
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/historical/contracts"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"contracts":["NSE-NIFTY-14Oct25-25100-CE"]}}""")));

        assertThat(fixture.historical().expiries(BrokerExchange.NSE, "NIFTY", 2025, 10))
                .extracting(e -> e.expiryDate())
                .containsExactly(LocalDate.of(2025, 10, 14), LocalDate.of(2025, 10, 21));
        assertThat(fixture.historical().contracts(BrokerExchange.NSE, "NIFTY", LocalDate.of(2025, 10, 14)))
                .hasSize(1);
    }

    @Test
    void instrumentMasterDownloadParsesCsv() {
        fixture.server()
                .stubFor(get(urlPathEqualTo("/instruments/instrument.csv"))
                        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "text/csv")
                                .withBody("exchange,exchange_token,trading_symbol,groww_symbol,name,"
                                        + "instrument_type,segment,series,isin,underlying_symbol,"
                                        + "underlying_exchange_token,lot_size,expiry_date,strike_price,"
                                        + "tick_size,freeze_quantity,is_reserved,buy_allowed,sell_allowed\n"
                                        + "NSE,2885,RELIANCE,NSE-RELIANCE,Reliance,EQ,CASH,EQ,"
                                        + "INE002A01018,,,1,,,0.05,,false,true,true")));

        assertThat(fixture.instruments().downloadInstrumentMaster()).hasSize(1);
    }

    @Test
    void smartOrderListAndGetMap() {
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/order-advance/list"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"orders":[{"smart_order_id":"gtt_1",
                                 "smart_order_type":"GTT","status":"ACTIVE","trading_symbol":"RELIANCE",
                                 "exchange":"NSE","quantity":10,"trigger_price":"2500","ltp":2490}]}}""")));
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/order-advance/status/CASH/GTT/internal/gtt_1"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"smart_order_id":"gtt_1",
                                 "smart_order_type":"GTT","status":"ACTIVE"}}""")));

        var smartOrders = fixture.smartOrders().smartOrders(new SmartOrderListQuery(
                BrokerSegment.CASH, BrokerSmartOrderType.GTT, null, 0, 10, null, null));
        assertThat(smartOrders).hasSize(1);
        assertThat(fixture.smartOrders().smartOrder(BrokerSegment.CASH, BrokerSmartOrderType.GTT, "gtt_1")
                .smartOrderId())
                .isEqualTo("gtt_1");
    }

    @Test
    void retryAfterOn429ActivatesCooldownAndFailsTyped() {
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/live-data/quote"))
                        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                                .withStatus(429)
                                .withHeader("Retry-After", "3")
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {"status":"FAILURE","error":{"code":"GA003","message":"rate limited"}}""")));

        assertThatThrownBy(() -> fixture.marketData().quote(BrokerExchange.NSE, BrokerSegment.CASH, "RELIANCE"))
                .isInstanceOf(com.edgerelative.broker.api.error.BrokerRateLimitException.class);
    }

    @Test
    void timeoutIsTypedAndDoesNotHang() {
        fixture.server()
                .stubFor(get(urlPathEqualTo("/v1/live-data/quote"))
                        .willReturn(okJson("""
                                {"status":"SUCCESS","payload":{"last_price":1}}""")
                                .withFixedDelay(3000)));

        fixture.properties().setRequestTimeout(Duration.ofMillis(200));
        assertThatThrownBy(() -> fixture.marketData().quote(BrokerExchange.NSE, BrokerSegment.CASH, "RELIANCE"))
                .isInstanceOf(com.edgerelative.broker.api.error.BrokerTimeoutException.class);
    }
}
