package com.edgerelative.broker.groww.client;

import com.edgerelative.broker.api.error.BrokerException;
import com.edgerelative.broker.api.error.BrokerInterruptedException;
import com.edgerelative.broker.api.error.BrokerProtocolException;
import com.edgerelative.broker.api.error.BrokerValidationException;
import com.edgerelative.broker.api.model.BrokerCandle;
import com.edgerelative.broker.api.model.BrokerCandleInterval;
import com.edgerelative.broker.api.model.BrokerCandleSeries;
import com.edgerelative.broker.api.model.BrokerContract;
import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerExpiry;
import com.edgerelative.broker.api.model.HistoricalCandleRequest;
import com.edgerelative.broker.api.port.HistoricalDataBroker;
import com.edgerelative.broker.groww.auth.GrowwAuthorizedExecutor;
import com.edgerelative.broker.groww.dto.response.GrowwCandleRangeResponse;
import com.edgerelative.broker.groww.dto.response.GrowwContractsResponse;
import com.edgerelative.broker.groww.dto.response.GrowwExpiriesResponse;
import com.edgerelative.broker.groww.http.GrowwHttpClient;
import com.edgerelative.broker.groww.http.GrowwRequestFactory;
import com.edgerelative.broker.groww.mapper.GrowwMapper;
import com.edgerelative.broker.groww.resilience.GrowwCallPriority;
import com.edgerelative.broker.groww.resilience.GrowwOperation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import tools.jackson.databind.JsonNode;

/**
 * Groww historical candles and derivative reference data.
 *
 * <p>Long ranges are split and fetched concurrently on virtual threads, but only after being bounded
 * by the bulk semaphore and the shared category rate limiter. Results are merged into a deterministic,
 * ascending, de-duplicated series; a partial failure is surfaced, never silently returned as success.
 */
public class GrowwHistoricalDataClient implements HistoricalDataBroker {

    private final GrowwAuthorizedExecutor executor;
    private final GrowwHttpClient http;
    private final GrowwRequestFactory requests;
    private final GrowwMapper mapper;
    private final GrowwHistoricalRangeSplitter splitter;
    private final ExecutorService bulkExecutor;
    private final Semaphore bulkGate;

    public GrowwHistoricalDataClient(
            GrowwAuthorizedExecutor executor,
            GrowwHttpClient http,
            GrowwRequestFactory requests,
            GrowwMapper mapper,
            GrowwHistoricalRangeSplitter splitter,
            ExecutorService bulkExecutor,
            int bulkMaxConcurrency) {
        this.executor = executor;
        this.http = http;
        this.requests = requests;
        this.mapper = mapper;
        this.splitter = splitter;
        this.bulkExecutor = bulkExecutor;
        this.bulkGate = new Semaphore(bulkMaxConcurrency, true);
    }

    @Override
    public BrokerCandleSeries candles(HistoricalCandleRequest request) {
        if (request.startTime() == null || request.endTime() == null || !request.startTime().isBefore(request.endTime())) {
            throw new BrokerValidationException(
                    "Historical candle range must have start before end",
                    "groww",
                    GrowwOperation.HISTORICAL_CANDLES.name(),
                    null,
                    null,
                    null);
        }
        List<GrowwHistoricalRangeSplitter.TimeRange> ranges =
                splitter.split(request.startTime(), request.endTime(), request.interval());

        List<List<BrokerCandle>> chunks;
        if (ranges.size() == 1) {
            chunks = List.of(fetchChunk(request, ranges.get(0)));
        } else {
            chunks = fetchConcurrently(request, ranges);
        }

        Map<Instant, BrokerCandle> merged = new java.util.TreeMap<>();
        for (List<BrokerCandle> chunk : chunks) {
            for (BrokerCandle candle : chunk) {
                merged.put(candle.openTime(), candle);
            }
        }
        return new BrokerCandleSeries(
                request.brokerSymbol(),
                request.interval(),
                request.startTime(),
                request.endTime(),
                null,
                new ArrayList<>(merged.values()));
    }

    private List<List<BrokerCandle>> fetchConcurrently(
            HistoricalCandleRequest request, List<GrowwHistoricalRangeSplitter.TimeRange> ranges) {
        List<Future<List<BrokerCandle>>> futures = new ArrayList<>();
        for (GrowwHistoricalRangeSplitter.TimeRange range : ranges) {
            futures.add(bulkExecutor.submit(() -> fetchChunk(request, range)));
        }
        List<List<BrokerCandle>> results = new ArrayList<>();
        try {
            for (Future<List<BrokerCandle>> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException e) {
            cancelAll(futures);
            Thread.currentThread().interrupt();
            throw new BrokerInterruptedException(
                    "Interrupted during historical candle fan-out", "groww", GrowwOperation.HISTORICAL_CANDLES.name(), null, e);
        } catch (ExecutionException e) {
            cancelAll(futures);
            Throwable cause = e.getCause();
            if (cause instanceof BrokerException brokerException) {
                throw brokerException;
            }
            throw new BrokerProtocolException(
                    "Historical candle chunk failed", "groww", GrowwOperation.HISTORICAL_CANDLES.name(), null, cause);
        }
    }

    private static void cancelAll(List<Future<List<BrokerCandle>>> futures) {
        for (Future<List<BrokerCandle>> future : futures) {
            future.cancel(true);
        }
    }

    private List<BrokerCandle> fetchChunk(
            HistoricalCandleRequest request, GrowwHistoricalRangeSplitter.TimeRange range) {
        acquireBulk();
        try {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("exchange", request.exchange().name());
            query.put("segment", request.segment().name());
            query.put("groww_symbol", request.brokerSymbol());
            query.put("start_time", Long.toString(range.start().getEpochSecond()));
            query.put("end_time", Long.toString(range.end().getEpochSecond()));
            query.put("candle_interval", request.interval().wireValue());
            JsonNode payload = executor.executeAuthorized(
                    GrowwOperation.HISTORICAL_CANDLES,
                    GrowwCallPriority.BULK,
                    token -> http.exchange(
                            requests.get("/v1/historical/candles", query, token), GrowwOperation.HISTORICAL_CANDLES));
            GrowwCandleRangeResponse dto =
                    mapper.dto(payload, GrowwCandleRangeResponse.class, GrowwOperation.HISTORICAL_CANDLES);
            List<BrokerCandle> candles = new ArrayList<>();
            if (dto.candles() != null) {
                for (List<JsonNode> row : dto.candles()) {
                    candles.add(mapper.toCandle(row, GrowwOperation.HISTORICAL_CANDLES));
                }
            }
            candles.sort(java.util.Comparator.comparing(BrokerCandle::openTime));
            return candles;
        } finally {
            bulkGate.release();
        }
    }

    private void acquireBulk() {
        try {
            bulkGate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BrokerInterruptedException(
                    "Interrupted while awaiting bulk historical capacity", "groww", GrowwOperation.HISTORICAL_CANDLES.name(), null, e);
        }
    }

    @Override
    public List<BrokerExpiry> expiries(
            BrokerExchange exchange, String underlyingSymbol, Integer year, Integer month) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("exchange", exchange.name());
        query.put("underlying_symbol", underlyingSymbol);
        if (year != null) {
            query.put("year", year.toString());
        }
        if (month != null) {
            query.put("month", month.toString());
        }
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.HISTORICAL_EXPIRIES,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(
                        requests.get("/v1/historical/expiries", query, token), GrowwOperation.HISTORICAL_EXPIRIES));
        List<String> dates = stringList(payload, "expiries", GrowwOperation.HISTORICAL_EXPIRIES);
        List<BrokerExpiry> result = new ArrayList<>();
        for (String date : dates) {
            result.add(mapper.toExpiry(date));
        }
        return result;
    }

    @Override
    public List<BrokerContract> contracts(
            BrokerExchange exchange, String underlyingSymbol, LocalDate expiryDate) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("exchange", exchange.name());
        query.put("underlying_symbol", underlyingSymbol);
        query.put("expiry_date", expiryDate.toString());
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.HISTORICAL_CONTRACTS,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(
                        requests.get("/v1/historical/contracts", query, token), GrowwOperation.HISTORICAL_CONTRACTS));
        List<String> symbols = stringList(payload, "contracts", GrowwOperation.HISTORICAL_CONTRACTS);
        List<BrokerContract> result = new ArrayList<>();
        for (String symbol : symbols) {
            result.add(mapper.toContract(symbol));
        }
        return result;
    }

    /**
     * Groww's docs show the array either directly under {@code payload} or nested under a named field.
     * Both shapes are accepted explicitly rather than guessed.
     */
    private List<String> stringList(JsonNode payload, String nestedField, GrowwOperation operation) {
        if (payload == null || payload.isNull()) {
            return List.of();
        }
        if (payload.isArray()) {
            List<String> result = new ArrayList<>();
            payload.forEach(node -> result.add(node.asString()));
            return result;
        }
        JsonNode nested = payload.path(nestedField);
        if (!nested.isArray()) {
            throw new BrokerProtocolException(
                    "Groww response did not contain array '" + nestedField + "'", "groww", operation.name(), null, null);
        }
        List<String> result = new ArrayList<>();
        nested.forEach(node -> result.add(node.asString()));
        return result;
    }

    // Kept for symmetry with the documented expiries/contracts DTOs and future typed use.
    GrowwExpiriesResponse expiriesDto(JsonNode payload) {
        return mapper.dto(payload, GrowwExpiriesResponse.class, GrowwOperation.HISTORICAL_EXPIRIES);
    }

    GrowwContractsResponse contractsDto(JsonNode payload) {
        return mapper.dto(payload, GrowwContractsResponse.class, GrowwOperation.HISTORICAL_CONTRACTS);
    }

    static BrokerCandleInterval intervalOrThrow(BrokerCandleInterval interval) {
        if (interval == null) {
            throw new BrokerValidationException(
                    "Candle interval is required", "groww", GrowwOperation.HISTORICAL_CANDLES.name(), null, null, null);
        }
        return interval;
    }
}
