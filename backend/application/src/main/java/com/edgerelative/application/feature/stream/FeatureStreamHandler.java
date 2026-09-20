package com.edgerelative.application.feature.stream;

import com.edgerelative.application.feature.service.FeatureDashboardService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Feature stream endpoint. On connect it sends an authoritative {@code feature.snapshot}; a client
 * that detects a sequence gap or reconnects sends {@code feature.resync} and receives a fresh
 * snapshot. Incremental {@code feature.update} messages are produced by the runtime producer.
 */
@Component
public class FeatureStreamHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureStreamHandler.class);

    private final FeatureDashboardService dashboard;
    private final FeatureStreamPublisher publisher;
    private final JsonMapper jsonMapper;

    public FeatureStreamHandler(
            FeatureDashboardService dashboard, FeatureStreamPublisher publisher, JsonMapper jsonMapper) {
        this.dashboard = dashboard;
        this.publisher = publisher;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        publisher.register(session);
        publisher.sendSnapshot(session, dashboard.rows());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = jsonMapper.readTree(message.getPayload());
            String type = node.path("type").asString("");
            if (FeatureStreamEnvelope.RESYNC.equals(type)) {
                publisher.sendSnapshot(session, dashboard.rows());
            }
        } catch (RuntimeException exception) {
            LOG.debug("Ignoring malformed feature stream message from {}: {}", session.getId(), exception.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        publisher.unregister(session);
    }
}
