package com.edgerelative.broker.groww.client;

import com.edgerelative.broker.api.model.BrokerMargin;
import com.edgerelative.broker.api.model.BrokerMarginOrder;
import com.edgerelative.broker.api.model.BrokerMarginRequirement;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.port.MarginBroker;
import com.edgerelative.broker.groww.auth.GrowwAuthorizedExecutor;
import com.edgerelative.broker.groww.dto.request.GrowwMarginOrderRequest;
import com.edgerelative.broker.groww.dto.response.GrowwMarginRequirementResponse;
import com.edgerelative.broker.groww.dto.response.GrowwMarginResponse;
import com.edgerelative.broker.groww.http.GrowwHttpClient;
import com.edgerelative.broker.groww.http.GrowwRequestFactory;
import com.edgerelative.broker.groww.mapper.GrowwMapper;
import com.edgerelative.broker.groww.resilience.GrowwCallPriority;
import com.edgerelative.broker.groww.resilience.GrowwOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * Groww margin queries.
 *
 * <p>{@code POST /v1/margins/detail/orders} is classified as read-only: it only computes a margin
 * requirement for a basket and never creates or changes broker state.
 */
public class GrowwMarginClient implements MarginBroker {

    private final GrowwAuthorizedExecutor executor;
    private final GrowwHttpClient http;
    private final GrowwRequestFactory requests;
    private final GrowwMapper mapper;

    public GrowwMarginClient(
            GrowwAuthorizedExecutor executor, GrowwHttpClient http, GrowwRequestFactory requests, GrowwMapper mapper) {
        this.executor = executor;
        this.http = http;
        this.requests = requests;
        this.mapper = mapper;
    }

    @Override
    public BrokerMargin userMargin() {
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.USER_MARGIN,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(
                        requests.get("/v1/margins/detail/user", Map.of(), token), GrowwOperation.USER_MARGIN));
        return mapper.toMargin(mapper.dto(payload, GrowwMarginResponse.class, GrowwOperation.USER_MARGIN));
    }

    @Override
    public BrokerMarginRequirement requiredMargin(BrokerSegment segment, List<BrokerMarginOrder> orders) {
        List<GrowwMarginOrderRequest> basket = new ArrayList<>();
        for (BrokerMarginOrder order : orders) {
            basket.add(new GrowwMarginOrderRequest(
                    order.tradingSymbol(),
                    order.quantity(),
                    order.price(),
                    order.exchange().name(),
                    order.segment().name(),
                    order.product().name(),
                    order.orderType().name(),
                    order.transactionType().name()));
        }
        Map<String, String> query = Map.of("segment", segment.name());
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.REQUIRED_MARGIN,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(
                        requests.postJson("/v1/margins/detail/orders", query, basket, token),
                        GrowwOperation.REQUIRED_MARGIN));
        return mapper.toMarginRequirement(
                mapper.dto(payload, GrowwMarginRequirementResponse.class, GrowwOperation.REQUIRED_MARGIN));
    }
}
