package com.edgerelative.application.risk.application.port;

import com.edgerelative.application.risk.domain.RiskContext;
import java.time.Instant;
import java.util.Optional;

/**
 * Authoritative account/portfolio/broker-state input port. Until real producers exist this returns
 * empty and the engine fails closed. A provider must never fabricate zero balances, healthy broker
 * status, or an empty portfolio.
 */
@FunctionalInterface
public interface RiskContextProvider {

    Optional<RiskContext> current(long brokerAccountId, Instant at);
}
