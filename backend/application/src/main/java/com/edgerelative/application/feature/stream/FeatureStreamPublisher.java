package com.edgerelative.application.feature.stream;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bounded-ish broadcaster for feature stream messages. Sessions are held in a concurrent set; a slow
 * or closed session is dropped rather than allowed to block others. The sequence counter is the
 * authoritative gap-detection signal for clients.
 */
@Component
public class FeatureStreamPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureStreamPublisher.class);

    private final JsonMapper jsonMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final AtomicLong sequence = new AtomicLong();
    /**
     * Serialises sequence allocation with the write, so a session can never observe a higher
     * sequence before a lower one and concurrent broadcasts never write a session at the same time
     * (Spring's {@link WebSocketSession} is not safe for concurrent sends).
     */
    private final Object sendLock = new Object();

    public FeatureStreamPublisher(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void register(WebSocketSession session) {
        sessions.add(session);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session);
    }

    public int sessionCount() {
        return sessions.size();
    }

    public long currentSequence() {
        return sequence.get();
    }

    /**
     * Sends an authoritative snapshot to one session at the current stream position. It deliberately
     * does not advance the counter so a snapshot cannot create a perceived gap for other clients.
     */
    public void sendSnapshot(WebSocketSession session, Object payload) {
        synchronized (sendLock) {
            send(session, FeatureStreamEnvelope.of(
                    FeatureStreamEnvelope.FEATURE_SNAPSHOT, sequence.get(), payload));
        }
    }

    /** Broadcasts an incremental update to every connected session. */
    public void broadcastUpdate(Object payload) {
        synchronized (sendLock) {
            FeatureStreamEnvelope envelope = FeatureStreamEnvelope.of(
                    FeatureStreamEnvelope.FEATURE_UPDATE, sequence.incrementAndGet(), payload);
            for (WebSocketSession session : sessions) {
                send(session, envelope);
            }
        }
    }

    private void send(WebSocketSession session, FeatureStreamEnvelope envelope) {
        try {
            if (!session.isOpen()) {
                sessions.remove(session);
                return;
            }
            session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(envelope)));
        } catch (IOException | RuntimeException exception) {
            sessions.remove(session);
            LOG.debug("Dropping feature stream session {}: {}", session.getId(), exception.getMessage());
        }
    }
}
