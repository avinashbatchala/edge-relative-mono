package com.edgerelative.broker.groww.auth;

import com.edgerelative.broker.api.error.BrokerAuthenticationException;
import com.edgerelative.broker.groww.resilience.GrowwCallExecutor;
import com.edgerelative.broker.groww.resilience.GrowwCallPriority;
import com.edgerelative.broker.groww.resilience.GrowwOperation;
import java.util.function.Function;

/**
 * Runs an authenticated call through the admission pipeline, with at most one token-refresh recovery.
 *
 * <p>This is not a blind retry: on an authentication failure the token cache is invalidated once and
 * the call is re-executed, which re-enters rate limiting and cooldown. A second authentication failure
 * propagates. Mutations are never replayed here in a way that could double-submit; this change only
 * enables read-only operations, and mutation adapters reject before reaching this class.
 */
public class GrowwAuthorizedExecutor {

    private final GrowwCallExecutor callExecutor;
    private final GrowwAccessTokenProvider tokenProvider;

    public GrowwAuthorizedExecutor(GrowwCallExecutor callExecutor, GrowwAccessTokenProvider tokenProvider) {
        this.callExecutor = callExecutor;
        this.tokenProvider = tokenProvider;
    }

    public <T> T executeAuthorized(GrowwOperation operation, GrowwCallPriority priority, Function<String, T> call) {
        try {
            return callExecutor.execute(operation, priority, () -> call.apply(tokenProvider.bearerToken()));
        } catch (BrokerAuthenticationException first) {
            if (!tokenProvider.recoverFromAuthenticationFailure()) {
                throw first;
            }
            return callExecutor.execute(operation, priority, () -> call.apply(tokenProvider.bearerToken()));
        }
    }
}
