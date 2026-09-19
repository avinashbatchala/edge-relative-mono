package com.edgerelative.broker.groww.client;

import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerLtp;
import com.edgerelative.broker.api.model.BrokerOhlc;
import com.edgerelative.broker.api.model.BrokerOptionChain;
import com.edgerelative.broker.api.model.BrokerOptionChainStrike;
import com.edgerelative.broker.api.model.BrokerOptionGreeks;
import com.edgerelative.broker.api.model.BrokerQuote;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.port.MarketDataBroker;
import com.edgerelative.broker.groww.auth.GrowwAuthorizedExecutor;
import com.edgerelative.broker.groww.dto.response.GrowwGreeksResponse;
import com.edgerelative.broker.groww.dto.response.GrowwOptionChainResponse;
import com.edgerelative.broker.groww.dto.response.GrowwQuoteResponse;
import com.edgerelative.broker.groww.http.GrowwHttpClient;
import com.edgerelative.broker.groww.http.GrowwRequestFactory;
import com.edgerelative.broker.groww.mapper.GrowwMapper;
import com.edgerelative.broker.groww.resilience.GrowwCallPriority;
import com.edgerelative.broker.groww.resilience.GrowwOperation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Groww live-data (snapshot) implementation. Batching is encapsulated here, not by callers. */
public class GrowwMarketDataClient implements MarketDataBroker {

    private static final int MAX_BATCH = 50;

    private final GrowwAuthorizedExecutor executor;
    private final GrowwHttpClient http;
    private final GrowwRequestFactory requests;
    private final GrowwMapper mapper;

    public GrowwMarketDataClient(
            GrowwAuthorizedExecutor executor, GrowwHttpClient http, GrowwRequestFactory requests, GrowwMapper mapper) {
        this.executor = executor;
        this.http = http;
        this.requests = requests;
        this.mapper = mapper;
    }

    @Override
    public BrokerQuote quote(BrokerExchange exchange, BrokerSegment segment, String tradingSymbol) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("exchange", exchange.name());
        query.put("segment", segment.name());
        query.put("trading_symbol", tradingSymbol);
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.QUOTE,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(requests.get("/v1/live-data/quote", query, token), GrowwOperation.QUOTE));
        return toQuote(mapper.dto(payload, GrowwQuoteResponse.class, GrowwOperation.QUOTE));
    }

    @Override
    public List<BrokerLtp> lastTradedPrices(BrokerSegment segment, List<String> exchangeSymbols) {
        Map<String, String> query = batchingQuery(segment, exchangeSymbols);
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.LTP,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(requests.get("/v1/live-data/ltp", query, token), GrowwOperation.LTP));
        List<BrokerLtp> result = new ArrayList<>();
        payload.properties().forEach(entry -> result.add(
                new BrokerLtp(entry.getKey(), GrowwMapper.decimal(entry.getValue()))));
        return result;
    }

    @Override
    public Map<String, BrokerOhlc> ohlc(BrokerSegment segment, List<String> exchangeSymbols) {
        Map<String, String> query = batchingQuery(segment, exchangeSymbols);
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.OHLC,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(requests.get("/v1/live-data/ohlc", query, token), GrowwOperation.OHLC));
        Map<String, BrokerOhlc> result = new LinkedHashMap<>();
        payload.properties().forEach(entry -> result.put(entry.getKey(), mapper.toOhlc(entry.getValue())));
        return result;
    }

    @Override
    public BrokerOptionChain optionChain(BrokerExchange exchange, String underlying, LocalDate expiryDate) {
        String path = "/v1/option-chain/exchange/" + exchange.name() + "/underlying/" + underlying;
        Map<String, String> query = Map.of("expiry_date", expiryDate.toString());
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.OPTION_CHAIN,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(
                        requests.get(path, query, token), GrowwOperation.OPTION_CHAIN));
        GrowwOptionChainResponse dto =
                mapper.dto(payload, GrowwOptionChainResponse.class, GrowwOperation.OPTION_CHAIN);
        List<BrokerOptionChainStrike> strikes = mapper.toStrikes(dto, GrowwOperation.OPTION_CHAIN);
        return new BrokerOptionChain(
                underlying, expiryDate, GrowwMapper.decimal(dto.underlyingLtp()), strikes);
    }

    @Override
    public BrokerOptionGreeks greeks(
            BrokerExchange exchange, String underlying, String tradingSymbol, LocalDate expiryDate) {
        String path = "/v1/live-data/greeks/exchange/" + exchange.name() + "/underlying/" + underlying
                + "/trading_symbol/" + tradingSymbol + "/expiry/" + expiryDate;
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.GREEKS,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(requests.get(path, Map.of(), token), GrowwOperation.GREEKS));
        GrowwGreeksResponse dto = mapper.dto(payload, GrowwGreeksResponse.class, GrowwOperation.GREEKS);
        return mapper.toGreeks(dto.greeks());
    }

    private Map<String, String> batchingQuery(BrokerSegment segment, List<String> exchangeSymbols) {
        if (exchangeSymbols == null || exchangeSymbols.isEmpty()) {
            throw new com.edgerelative.broker.api.error.BrokerValidationException(
                    "At least one exchange symbol is required", "groww", GrowwOperation.LTP.name(), null, null, null);
        }
        if (exchangeSymbols.size() > MAX_BATCH) {
            throw new com.edgerelative.broker.api.error.BrokerValidationException(
                    "Groww supports at most " + MAX_BATCH + " instruments per request",
                    "groww",
                    GrowwOperation.LTP.name(),
                    null,
                    null,
                    null);
        }
        return Map.of("segment", segment.name(), "exchange_symbols", String.join(",", exchangeSymbols));
    }

    private BrokerQuote toQuote(GrowwQuoteResponse dto) {
        List<com.edgerelative.broker.api.model.BrokerDepthLevel> bids = depth(dto.depth() == null ? null : dto.depth().buy());
        List<com.edgerelative.broker.api.model.BrokerDepthLevel> asks = depth(dto.depth() == null ? null : dto.depth().sell());
        return new BrokerQuote(
                GrowwMapper.decimal(dto.lastPrice()),
                GrowwMapper.decimal(dto.averagePrice()),
                GrowwMapper.decimal(dto.dayChange()),
                GrowwMapper.decimal(dto.dayChangePercent()),
                GrowwMapper.decimal(dto.upperCircuitLimit()),
                GrowwMapper.decimal(dto.lowerCircuitLimit()),
                mapper.toOhlc(dto.ohlc()),
                bids,
                asks,
                GrowwMapper.decimal(dto.bidPrice()),
                dto.bidQuantity() == null ? 0L : dto.bidQuantity(),
                GrowwMapper.decimal(dto.offerPrice()),
                dto.offerQuantity() == null ? 0L : dto.offerQuantity(),
                dto.volume() == null ? 0L : dto.volume(),
                dto.lastTradeQuantity() == null ? 0L : dto.lastTradeQuantity(),
                dto.lastTradeTime() == null ? null : java.time.Instant.ofEpochMilli(dto.lastTradeTime()),
                GrowwMapper.decimal(dto.openInterest()),
                GrowwMapper.decimal(dto.previousOpenInterest()),
                GrowwMapper.decimal(dto.openInterestDayChange()),
                GrowwMapper.decimal(dto.openInterestDayChangePercentage()),
                GrowwMapper.decimal(dto.week52High()),
                GrowwMapper.decimal(dto.week52Low()),
                GrowwMapper.decimal(dto.impliedVolatility()),
                GrowwMapper.decimal(dto.marketCap()),
                GrowwMapper.decimal(dto.totalBuyQuantity()),
                GrowwMapper.decimal(dto.totalSellQuantity()));
    }

    private static List<com.edgerelative.broker.api.model.BrokerDepthLevel> depth(
            List<GrowwQuoteResponse.Level> levels) {
        if (levels == null) {
            return List.of();
        }
        List<com.edgerelative.broker.api.model.BrokerDepthLevel> result = new ArrayList<>();
        for (GrowwQuoteResponse.Level level : levels) {
            result.add(new com.edgerelative.broker.api.model.BrokerDepthLevel(
                    GrowwMapper.decimal(level.price()), level.quantity() == null ? 0L : level.quantity()));
        }
        return result;
    }
}
