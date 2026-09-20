package com.edgerelative.broker.groww.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edgerelative.broker.api.error.BrokerAuthenticationException;
import com.edgerelative.broker.api.error.BrokerUnavailableException;
import com.edgerelative.broker.groww.resilience.GrowwCallExecutor;
import com.edgerelative.broker.groww.resilience.GrowwCallPriority;
import com.edgerelative.broker.groww.resilience.GrowwOperation;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class GrowwAuthorizedExecutorTest {

    @Test
    void connectionResetIsTreatedAsARecoverableTokenFailure() {
        GrowwCallExecutor callExecutor = mock(GrowwCallExecutor.class);
        GrowwAccessTokenProvider tokenProvider = mock(GrowwAccessTokenProvider.class);
        when(tokenProvider.bearerToken()).thenReturn("token");
        when(tokenProvider.recoverFromAuthenticationFailure()).thenReturn(true);
        when(callExecutor.execute(any(), any(), any()))
                .thenThrow(new BrokerUnavailableException(
                        "Groww request failed: reset", "groww", GrowwOperation.HISTORICAL_CANDLES.name(), "/v1", null,
                        new IOException("Connection reset")))
                .thenReturn("ok");
        GrowwAuthorizedExecutor executor = new GrowwAuthorizedExecutor(callExecutor, tokenProvider);

        String result = executor.executeAuthorized(
                GrowwOperation.HISTORICAL_CANDLES, GrowwCallPriority.BULK, token -> "ok");

        assertThat(result).isEqualTo("ok");
        verify(tokenProvider, times(1)).recoverFromAuthenticationFailure();
        verify(callExecutor, times(2)).execute(any(), any(), any());
    }

    @Test
    void authenticationFailureStillRecovers() {
        GrowwCallExecutor callExecutor = mock(GrowwCallExecutor.class);
        GrowwAccessTokenProvider tokenProvider = mock(GrowwAccessTokenProvider.class);
        when(tokenProvider.bearerToken()).thenReturn("token");
        when(tokenProvider.recoverFromAuthenticationFailure()).thenReturn(true);
        when(callExecutor.execute(any(), any(), any()))
                .thenThrow(new BrokerAuthenticationException("expired", "groww", "X", null, 401, null))
                .thenReturn("ok");
        GrowwAuthorizedExecutor executor = new GrowwAuthorizedExecutor(callExecutor, tokenProvider);

        String result = executor.executeAuthorized(
                GrowwOperation.HISTORICAL_CANDLES, GrowwCallPriority.BULK, token -> "ok");
        assertThat(result).isEqualTo("ok");
        verify(tokenProvider).recoverFromAuthenticationFailure();
    }

    @Test
    void infrastructuralUnavailabilityWithoutTransportCauseDoesNotRegenerateTheToken() {
        GrowwCallExecutor callExecutor = mock(GrowwCallExecutor.class);
        GrowwAccessTokenProvider tokenProvider = mock(GrowwAccessTokenProvider.class);
        when(tokenProvider.bearerToken()).thenReturn("token");
        when(callExecutor.execute(any(), any(), any()))
                .thenThrow(new BrokerUnavailableException(
                        "Groww in-flight limit saturated", "groww", GrowwOperation.HISTORICAL_CANDLES.name(), null, null,
                        null));
        GrowwAuthorizedExecutor executor = new GrowwAuthorizedExecutor(callExecutor, tokenProvider);

        assertThatThrownBy(() -> executor.executeAuthorized(
                        GrowwOperation.HISTORICAL_CANDLES, GrowwCallPriority.BULK, token -> "ok"))
                .isInstanceOf(BrokerUnavailableException.class);
        verify(tokenProvider, never()).recoverFromAuthenticationFailure();
    }
}
