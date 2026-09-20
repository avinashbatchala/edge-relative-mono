package com.edgerelative.application.feature.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class FeatureStreamPublisherTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void snapshotUsesTheCurrentSequenceAndBroadcastAdvancesIt() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("s-1");
        FeatureStreamPublisher publisher = new FeatureStreamPublisher(JSON);
        publisher.register(session);

        publisher.sendSnapshot(session, List.of("authoritative"));
        publisher.broadcastUpdate(List.of("incremental"));
        publisher.broadcastUpdate(List.of("incremental-2"));

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(3)).sendMessage(messages.capture());
        List<JsonNode> envelopes = messages.getAllValues().stream()
                .map(message -> JSON.readTree(message.getPayload()))
                .toList();

        assertThat(envelopes.get(0).path("type").asString()).isEqualTo("feature.snapshot");
        assertThat(envelopes.get(0).path("version").asInt()).isEqualTo(1);
        // Snapshot must not advance the broadcast sequence.
        assertThat(envelopes.get(0).path("sequence").asLong()).isZero();
        assertThat(envelopes.get(1).path("type").asString()).isEqualTo("feature.update");
        assertThat(envelopes.get(1).path("sequence").asLong()).isEqualTo(1);
        assertThat(envelopes.get(2).path("sequence").asLong()).isEqualTo(2);
    }

    @Test
    void closedSessionsAreDroppedRatherThanBlocking() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(false);
        when(session.getId()).thenReturn("s-gone");
        FeatureStreamPublisher publisher = new FeatureStreamPublisher(JSON);
        publisher.register(session);

        publisher.broadcastUpdate(List.of("x"));

        verify(session, times(0)).sendMessage(any());
        assertThat(publisher.sessionCount()).isZero();
    }

    @Test
    void concurrentBroadcastsDeliverSequencesInOrderToASession() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("s-ordered");
        List<Long> delivered = new CopyOnWriteArrayList<>();
        CountDownLatch firstSendEntered = new CountDownLatch(1);
        CountDownLatch secondSendCompleted = new CountDownLatch(1);
        doAnswer(invocation -> {
            TextMessage message = invocation.getArgument(0);
            long sequence = JSON.readTree(message.getPayload()).path("sequence").asLong();
            if (sequence == 1) {
                // Let the second broadcast allocate and send first; a correct publisher must not
                // expose sequence 2 before sequence 1 on the same session.
                firstSendEntered.countDown();
                secondSendCompleted.await(5, TimeUnit.SECONDS);
            }
            delivered.add(sequence);
            if (sequence == 2) {
                secondSendCompleted.countDown();
            }
            return null;
        }).when(session).sendMessage(any());

        FeatureStreamPublisher publisher = new FeatureStreamPublisher(JSON);
        publisher.register(session);

        Thread first = new Thread(() -> publisher.broadcastUpdate(List.of("first")));
        first.start();
        assertThat(firstSendEntered.await(5, TimeUnit.SECONDS)).isTrue();
        Thread second = new Thread(() -> publisher.broadcastUpdate(List.of("second")));
        second.start();
        first.join(5000);
        second.join(5000);

        assertThat(delivered).containsExactly(1L, 2L);
    }
}
