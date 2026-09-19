package com.edgerelative.broker.groww.client;

import com.edgerelative.broker.api.model.BrokerHolding;
import com.edgerelative.broker.api.model.BrokerPosition;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerUserProfile;
import com.edgerelative.broker.api.port.PortfolioBroker;
import com.edgerelative.broker.groww.auth.GrowwAuthorizedExecutor;
import com.edgerelative.broker.groww.dto.response.GrowwHoldingListResponse;
import com.edgerelative.broker.groww.dto.response.GrowwPositionListResponse;
import com.edgerelative.broker.groww.dto.response.GrowwUserProfileResponse;
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

/** Groww positions, holdings and account profile. */
public class GrowwPortfolioClient implements PortfolioBroker {

    private final GrowwAuthorizedExecutor executor;
    private final GrowwHttpClient http;
    private final GrowwRequestFactory requests;
    private final GrowwMapper mapper;

    public GrowwPortfolioClient(
            GrowwAuthorizedExecutor executor, GrowwHttpClient http, GrowwRequestFactory requests, GrowwMapper mapper) {
        this.executor = executor;
        this.http = http;
        this.requests = requests;
        this.mapper = mapper;
    }

    @Override
    public List<BrokerHolding> holdings() {
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.HOLDINGS,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(
                        requests.get("/v1/holdings/user", Map.of(), token), GrowwOperation.HOLDINGS));
        GrowwHoldingListResponse dto = mapper.dto(payload, GrowwHoldingListResponse.class, GrowwOperation.HOLDINGS);
        List<BrokerHolding> result = new ArrayList<>();
        if (dto.holdings() != null) {
            dto.holdings().forEach(holding -> result.add(mapper.toHolding(holding)));
        }
        return result;
    }

    @Override
    public List<BrokerPosition> positions(BrokerSegment segment) {
        Map<String, String> query = new LinkedHashMap<>();
        if (segment != null) {
            query.put("segment", segment.name());
        }
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.POSITIONS,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(requests.get("/v1/positions/user", query, token), GrowwOperation.POSITIONS));
        return toPositions(payload);
    }

    @Override
    public List<BrokerPosition> positionsForSymbol(String tradingSymbol, BrokerSegment segment) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("trading_symbol", tradingSymbol);
        if (segment != null) {
            query.put("segment", segment.name());
        }
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.POSITION_BY_SYMBOL,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(
                        requests.get("/v1/positions/trading-symbol", query, token), GrowwOperation.POSITION_BY_SYMBOL));
        return toPositions(payload);
    }

    @Override
    public BrokerUserProfile userProfile() {
        JsonNode payload = executor.executeAuthorized(
                GrowwOperation.USER_PROFILE,
                GrowwCallPriority.INTERACTIVE,
                token -> http.exchange(
                        requests.get("/v1/user/detail", Map.of(), token), GrowwOperation.USER_PROFILE));
        return mapper.toUserProfile(mapper.dto(payload, GrowwUserProfileResponse.class, GrowwOperation.USER_PROFILE));
    }

    private List<BrokerPosition> toPositions(JsonNode payload) {
        GrowwPositionListResponse dto = mapper.dto(payload, GrowwPositionListResponse.class, GrowwOperation.POSITIONS);
        List<BrokerPosition> result = new ArrayList<>();
        if (dto.positions() != null) {
            dto.positions().forEach(position -> result.add(mapper.toPosition(position)));
        }
        return result;
    }
}
