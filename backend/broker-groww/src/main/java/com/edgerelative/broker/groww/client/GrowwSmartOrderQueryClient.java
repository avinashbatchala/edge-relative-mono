package com.edgerelative.broker.groww.client;

import com.edgerelative.broker.api.error.BrokerValidationException;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerSmartOrder;
import com.edgerelative.broker.api.model.BrokerSmartOrderType;
import com.edgerelative.broker.api.model.SmartOrderListQuery;
import com.edgerelative.broker.api.port.SmartOrderQueryBroker;
import com.edgerelative.broker.groww.auth.GrowwAuthorizedExecutor;
import com.edgerelative.broker.groww.dto.response.GrowwSmartOrderListResponse;
import com.edgerelative.broker.groww.dto.response.GrowwSmartOrderResponse;
import com.edgerelative.broker.groww.http.GrowwHttpClient;
import com.edgerelative.broker.groww.http.GrowwRequestFactory;
import com.edgerelative.broker.groww.mapper.GrowwMapper;
import com.edgerelative.broker.groww.resilience.GrowwCallPriority;
import com.edgerelative.broker.groww.resilience.GrowwOperation;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * Groww read-only GTT/OCO queries.
 */
public class GrowwSmartOrderQueryClient implements SmartOrderQueryBroker {

    private static final int MAX_PAGE = 500;
    private static final int MAX_PAGE_SIZE = 50;
    private static final Duration MAX_RANGE = Duration.ofDays(31);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final GrowwAuthorizedExecutor executor;
    private final GrowwHttpClient http;
    private final GrowwRequestFactory requests;
    private final GrowwMapper mapper;

    public GrowwSmartOrderQueryClient(
            GrowwAuthorizedExecutor executor, GrowwHttpClient http, GrowwRequestFactory requests, GrowwMapper mapper) {
        this.executor = executor;
        this.http = http;
        this.requests = requests;
        this.mapper = mapper;
    }

    @Override
    public BrokerSmartOrder smartOrder(BrokerSegment segment, BrokerSmartOrderType type, String smartOrderId) {
        String path = "/v1/order-advance/status/" + segment.name() + "/" + type.name() + "/internal/" + smartOrderId;
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.SMART_ORDER_GET,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(requests.get(path, Map.of(), token), GrowwOperation.SMART_ORDER_GET));
        return mapper.toSmartOrder(
                mapper.dto(payload, GrowwSmartOrderResponse.class, GrowwOperation.SMART_ORDER_GET));
    }

    @Override
    public List<BrokerSmartOrder> smartOrders(SmartOrderListQuery query) {
        validate(query);
        Map<String, String> params = new LinkedHashMap<>();
        if (query.segment() != null) {
            params.put("segment", query.segment().name());
        }
        if (query.smartOrderType() != null) {
            params.put("smart_order_type", query.smartOrderType().name());
        }
        if (query.status() != null) {
            params.put("status", query.status().name());
        }
        if (query.page() != null) {
            params.put("page", query.page().toString());
        }
        if (query.pageSize() != null) {
            params.put("page_size", query.pageSize().toString());
        }
        if (query.startDateTime() != null) {
            params.put("start_date_time", ISO.format(query.startDateTime().atZone(ZoneOffset.UTC)));
        }
        if (query.endDateTime() != null) {
            params.put("end_date_time", ISO.format(query.endDateTime().atZone(ZoneOffset.UTC)));
        }
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.SMART_ORDER_LIST,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(
                        requests.get("/v1/order-advance/list", params, token), GrowwOperation.SMART_ORDER_LIST));
        GrowwSmartOrderListResponse dto =
                mapper.dto(payload, GrowwSmartOrderListResponse.class, GrowwOperation.SMART_ORDER_LIST);
        List<BrokerSmartOrder> result = new ArrayList<>();
        if (dto.orders() != null) {
            dto.orders().forEach(order -> result.add(mapper.toSmartOrder(order)));
        }
        return result;
    }

    private static void validate(SmartOrderListQuery query) {
        if (query.page() != null && (query.page() < 0 || query.page() > MAX_PAGE)) {
            throw new BrokerValidationException(
                    "page must be between 0 and " + MAX_PAGE, "groww", GrowwOperation.SMART_ORDER_LIST.name(), null, null, null);
        }
        if (query.pageSize() != null && (query.pageSize() < 1 || query.pageSize() > MAX_PAGE_SIZE)) {
            throw new BrokerValidationException(
                    "page_size must be between 1 and " + MAX_PAGE_SIZE,
                    "groww",
                    GrowwOperation.SMART_ORDER_LIST.name(),
                    null,
                    null,
                    null);
        }
        if (query.startDateTime() != null && query.endDateTime() != null) {
            if (query.endDateTime().isBefore(query.startDateTime())) {
                throw new BrokerValidationException(
                        "end_date_time must not be before start_date_time",
                        "groww",
                        GrowwOperation.SMART_ORDER_LIST.name(),
                        null,
                        null,
                        null);
            }
            if (Duration.between(query.startDateTime(), query.endDateTime()).compareTo(MAX_RANGE) > 0) {
                throw new BrokerValidationException(
                        "Smart order date range must not exceed one month",
                        "groww",
                        GrowwOperation.SMART_ORDER_LIST.name(),
                        null,
                        null,
                        null);
            }
        }
    }
}
