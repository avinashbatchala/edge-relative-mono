package com.edgerelative.broker.groww.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.broker.api.error.BrokerAuthenticationException;
import com.edgerelative.broker.api.error.BrokerAuthorizationException;
import com.edgerelative.broker.api.error.BrokerNotFoundException;
import com.edgerelative.broker.api.error.BrokerRateLimitException;
import com.edgerelative.broker.api.error.BrokerTransientException;
import com.edgerelative.broker.api.error.BrokerUnavailableException;
import com.edgerelative.broker.api.error.BrokerUnknownException;
import com.edgerelative.broker.api.error.BrokerValidationException;
import com.edgerelative.broker.groww.resilience.GrowwOperation;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GrowwErrorDecoderTest {

    private final GrowwErrorDecoder decoder = new GrowwErrorDecoder();

    @Test
    void mapsDocumentedStatusesAndCodes() {
        assertThat(decoder.decode(401, null, null, GrowwOperation.QUOTE, "/q"))
                .isInstanceOf(BrokerAuthenticationException.class);
        assertThat(decoder.decode(403, new GrowwApiError("GA005", "no"), null, GrowwOperation.QUOTE, "/q"))
                .isInstanceOf(BrokerAuthorizationException.class);
        assertThat(decoder.decode(404, new GrowwApiError("GA004", "missing"), null, GrowwOperation.QUOTE, "/q"))
                .isInstanceOf(BrokerNotFoundException.class);
        assertThat(decoder.decode(400, new GrowwApiError("GA001", "bad"), null, GrowwOperation.QUOTE, "/q"))
                .isInstanceOf(BrokerValidationException.class);
        assertThat(decoder.decode(500, null, null, GrowwOperation.QUOTE, "/q"))
                .isInstanceOf(BrokerUnavailableException.class);
        assertThat(decoder.decode(503, new GrowwApiError("GA003", "busy"), null, GrowwOperation.QUOTE, "/q"))
                .isInstanceOf(BrokerTransientException.class);
        assertThat(decoder.decode(418, new GrowwApiError("GA999", "?"), null, GrowwOperation.QUOTE, "/q"))
                .isInstanceOf(BrokerUnknownException.class);
    }

    @Test
    void parsesRetryAfterOn429() {
        var failure = decoder.decode(429, null, "120", GrowwOperation.QUOTE, "/q");
        assertThat(failure).isInstanceOf(BrokerRateLimitException.class);
        assertThat(((BrokerRateLimitException) failure).retryAfter()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void ignoresUnparseableRetryAfter() {
        var failure = decoder.decode(429, null, "soon", GrowwOperation.QUOTE, "/q");
        assertThat(((BrokerRateLimitException) failure).retryAfter()).isNull();
    }
}
