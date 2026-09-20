package com.edgerelative.application.strategy.api;

import com.edgerelative.application.strategy.application.SetupEvaluation;
import com.edgerelative.application.strategy.domain.HardGateResult;
import com.edgerelative.application.strategy.domain.StrategyEvaluationResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Structured evaluation result for the API. It never represents a risk approval or an order. */
public record SetupEvaluationResultResponse(
        String evaluationId,
        UUID setupInstanceId,
        String strategyVersion,
        String parameterSetId,
        int parameterVersion,
        long instrumentId,
        Instant evaluationTimestamp,
        String direction,
        String previousState,
        String setupState,
        boolean transitioned,
        String initialization,
        String setupFamily,
        boolean valid,
        String primaryReasonCode,
        List<String> reasonCodes,
        String triggerType,
        BigDecimal triggerLevel,
        Double entryExtensionAtr,
        BigDecimal invalidationLevel,
        List<GateResponse> hardGates,
        boolean appended) {

    public static SetupEvaluationResultResponse from(SetupEvaluation evaluation) {
        return from(evaluation.result(), evaluation.appended());
    }

    private static SetupEvaluationResultResponse from(StrategyEvaluationResult result, boolean appended) {
        return new SetupEvaluationResultResponse(
                result.evaluationId(),
                result.setupInstanceId(),
                result.strategyVersion(),
                result.parameterSetId(),
                result.parameterVersion(),
                result.instrumentId(),
                result.evaluationTimestamp(),
                result.direction() == null ? null : result.direction().name(),
                result.previousState().name(),
                result.setupState().name(),
                result.transitioned(),
                result.initialization() == null ? null : result.initialization().name(),
                result.setupFamily() == null ? null : result.setupFamily().name(),
                result.valid(),
                result.primaryReasonCode() == null ? null : result.primaryReasonCode().name(),
                result.reasonCodes().stream().map(Enum::name).toList(),
                result.trigger() == null ? null : result.trigger().triggerType(),
                result.trigger() == null ? null : result.trigger().triggerLevel(),
                result.trigger() == null ? null : result.trigger().entryExtensionAtr(),
                result.invalidation() == null ? null : result.invalidation().invalidationLevel(),
                result.hardGates().stream().map(GateResponse::from).toList(),
                appended);
    }

    public record GateResponse(String gate, String status, String reason) {

        static GateResponse from(HardGateResult gate) {
            return new GateResponse(
                    gate.gateCode().name(),
                    gate.status().name(),
                    gate.reasonCode() == null ? null : gate.reasonCode().name());
        }
    }
}
