package com.edgerelative.broker.groww.client;

import com.edgerelative.broker.api.error.BrokerValidationException;
import com.edgerelative.broker.api.model.BrokerOrder;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerTrade;
import com.edgerelative.broker.api.model.OrderListQuery;
import com.edgerelative.broker.api.model.TradeListQuery;
import com.edgerelative.broker.api.port.OrderQueryBroker;
import com.edgerelative.broker.groww.auth.GrowwAuthorizedExecutor;
import com.edgerelative.broker.groww.dto.response.GrowwOrderListResponse;
import com.edgerelative.broker.groww.dto.response.GrowwOrderResponse;
import com.edgerelative.broker.groww.dto.response.GrowwTradeListResponse;
import com.edgerelative.broker.groww.http.GrowwHttpClient;
import com.edgerelative.broker.groww.http.GrowwRequestFactory;
import com.edgerelative.broker.groww.mapper.GrowwMapper;
import com.edgerelative.broker.groww.resilience.GrowwCallPriority;
import com.edgerelative.broker.groww.resilience.GrowwOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * Groww read-only order and trade queries.
 */
public class GrowwOrderQueryClient implements OrderQueryBroker {

    static final int MAX_TRADES_PAGE_SIZE = 50;
    static final int MAX_ORDERS_PAGE_SIZE = 100;

    private final GrowwAuthorizedExecutor executor;
    private final GrowwHttpClient http;
    private final GrowwRequestFactory requests;
    private final GrowwMapper mapper;

    public GrowwOrderQueryClient(
            GrowwAuthorizedExecutor executor, GrowwHttpClient http, GrowwRequestFactory requests, GrowwMapper mapper) {
        this.executor = executor;
        this.http = http;
        this.requests = requests;
        this.mapper = mapper;
    }

    @Override
    public BrokerOrder orderStatus(String brokerOrderId, BrokerSegment segment) {
        Map<String, String> query = Map.of("segment", segment.name());
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.ORDER_STATUS,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(
                        requests.get("/v1/order/status/" + brokerOrderId, query, token), GrowwOperation.ORDER_STATUS));
        return mapper.toOrder(mapper.dto(payload, GrowwOrderResponse.class, GrowwOperation.ORDER_STATUS));
    }

    @Override
    public BrokerOrder orderStatusByReference(String orderReferenceId, BrokerSegment segment) {
        Map<String, String> query = Map.of("segment", segment.name());
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.ORDER_STATUS_BY_REFERENCE,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(
                        requests.get("/v1/order/status/reference/" + orderReferenceId, query, token),
                        GrowwOperation.ORDER_STATUS_BY_REFERENCE));
        return mapper.toOrder(
                mapper.dto(payload, GrowwOrderResponse.class, GrowwOperation.ORDER_STATUS_BY_REFERENCE));
    }

    @Override
    public BrokerOrder orderDetail(String brokerOrderId, BrokerSegment segment) {
        Map<String, String> query = Map.of("segment", segment.name());
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.ORDER_DETAIL,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(
                        requests.get("/v1/order/detail/" + brokerOrderId, query, token), GrowwOperation.ORDER_DETAIL));
        return mapper.toOrder(mapper.dto(payload, GrowwOrderResponse.class, GrowwOperation.ORDER_DETAIL));
    }

    @Override
    public List<BrokerOrder> orders(OrderListQuery query) {
        validatePageSize(query.pageSize(), MAX_ORDERS_PAGE_SIZE, GrowwOperation.ORDER_LIST);
        Map<String, String> params = new LinkedHashMap<>();
        if (query.segment() != null) {
            params.put("segment", query.segment().name());
        }
        putIfPresent(params, "page", query.page());
        putIfPresent(params, "page_size", query.pageSize());
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.ORDER_LIST,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(requests.get("/v1/order/list", params, token), GrowwOperation.ORDER_LIST));
        GrowwOrderListResponse dto = mapper.dto(payload, GrowwOrderListResponse.class, GrowwOperation.ORDER_LIST);
        List<BrokerOrder> result = new ArrayList<>();
        if (dto.orders() != null) {
            dto.orders().forEach(order -> result.add(mapper.toOrder(order)));
        }
        return result;
    }

    @Override
    public List<BrokerTrade> trades(String brokerOrderId, TradeListQuery query) {
        validatePageSize(query.pageSize(), MAX_TRADES_PAGE_SIZE, GrowwOperation.ORDER_TRADES);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("segment", query.segment().name());
        putIfPresent(params, "page", query.page());
        putIfPresent(params, "page_size", query.pageSize());
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.ORDER_TRADES,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(
                        requests.get("/v1/order/trades/" + brokerOrderId, params, token), GrowwOperation.ORDER_TRADES));
        GrowwTradeListResponse dto = mapper.dto(payload, GrowwTradeListResponse.class, GrowwOperation.ORDER_TRADES);
        List<BrokerTrade> result = new ArrayList<>();
        if (dto.trades() != null) {
            dto.trades().forEach(trade -> result.add(mapper.toTrade(trade)));
        }
        return result;
    }

    private static void validatePageSize(Integer pageSize, int max, GrowwOperation operation) {
        if (pageSize != null && (pageSize < 1 || pageSize > max)) {
            throw new BrokerValidationException(
                    "page_size must be between 1 and " + max, "groww", operation.name(), null, null, null);
        }
    }

    private static void putIfPresent(Map<String, String> map, String key, Object value) {
        if (value != null) {
            map.put(key, value.toString());
        }
    }
}
