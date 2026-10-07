package com.edgerelative.application.llm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.edgerelative.llm.api.error.LlmUnavailableException;
import com.edgerelative.llm.api.model.LlmCompletion;
import com.edgerelative.llm.api.model.LlmPrompt;
import com.edgerelative.llm.api.port.LlmClient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class LlmNarrationServiceTest {

    private static LlmClient client(boolean available) {
        return new LlmClient() {
            @Override
            public String providerName() {
                return "stub";
            }

            @Override
            public boolean available() {
                return available;
            }

            @Override
            public LlmCompletion complete(LlmPrompt prompt) {
                return new LlmCompletion("stub", "stub-model", "advisory text", "stop", 5, 3);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<LlmClient> provider(LlmClient client) {
        ObjectProvider<LlmClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return provider;
    }

    @Test
    void returnsAdvisoryCompletion() {
        LlmNarrationService service = new LlmNarrationService(provider(client(true)));

        LlmCompletion completion = service.narrate("What changed?", "revenue=100");

        assertThat(service.available()).isTrue();
        assertThat(completion.text()).isEqualTo("advisory text");
        assertThat(completion.provider()).isEqualTo("stub");
    }

    @Test
    void throwsUnavailableWhenClientNotConfigured() {
        LlmNarrationService service = new LlmNarrationService(provider(null));

        assertThat(service.available()).isFalse();
        assertThatThrownBy(() -> service.narrate("q", "f")).isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void throwsUnavailableWhenKeyMissing() {
        LlmNarrationService service = new LlmNarrationService(provider(client(false)));

        assertThatThrownBy(() -> service.narrate("q", "f")).isInstanceOf(LlmUnavailableException.class);
    }
}
