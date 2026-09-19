package com.edgerelative.application.broker;

import com.edgerelative.broker.api.model.BrokerCancelOrderRequest;
import com.edgerelative.broker.api.model.BrokerModifyOrderRequest;
import com.edgerelative.broker.api.model.BrokerOrderReference;
import com.edgerelative.broker.api.model.BrokerOrderRequest;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerSmartOrder;
import com.edgerelative.broker.api.model.BrokerSmartOrderModifyRequest;
import com.edgerelative.broker.api.model.BrokerSmartOrderRequest;
import com.edgerelative.broker.api.model.BrokerSmartOrderType;
import com.edgerelative.broker.api.port.ExecutionBroker;
import com.edgerelative.broker.api.port.SmartOrderBroker;
import com.edgerelative.application.broker.api.BrokerApiEnums;
import com.edgerelative.application.broker.api.CancelOrderApiRequest;
import com.edgerelative.application.broker.api.ModifyOrderApiRequest;
import com.edgerelative.application.broker.api.PlaceOrderApiRequest;
import com.edgerelative.application.broker.api.SmartOrderCreateApiRequest;
import com.edgerelative.application.broker.api.SmartOrderModifyApiRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Application contracts for broker-side mutations.
 *
 * <p>Every route maps to a broker-neutral request and delegates to the safe default adapter, which
 * refuses before any downstream call. There is no hidden enable flag: execution requires a separate
 * reviewed implementation of the ports.
 */
@RestController
@RequestMapping("/api/v1/brokers/groww")
public class GrowwMutationController {

    private final ExecutionBroker execution;
    private final SmartOrderBroker smartOrderExecution;

    public GrowwMutationController(ExecutionBroker execution, SmartOrderBroker smartOrderExecution) {
        this.execution = execution;
        this.smartOrderExecution = smartOrderExecution;
    }

    @PostMapping("/orders")
    public BrokerOrderReference placeOrder(@RequestBody PlaceOrderApiRequest request) {
        return execution.placeOrder(new BrokerOrderRequest(
                request.tradingSymbol(),
                request.quantity(),
                request.price(),
                request.triggerPrice(),
                BrokerApiEnums.validity(request.validity()),
                BrokerApiEnums.exchange(request.exchange()),
                BrokerApiEnums.segment(request.segment()),
                BrokerApiEnums.product(request.product()),
                BrokerApiEnums.orderType(request.orderType()),
                BrokerApiEnums.transactionType(request.transactionType()),
                request.orderReferenceId()));
    }

    @PutMapping("/orders")
    public BrokerOrderReference modifyOrder(@RequestBody ModifyOrderApiRequest request) {
        return execution.modifyOrder(new BrokerModifyOrderRequest(
                request.brokerOrderId(),
                request.quantity(),
                request.price(),
                request.triggerPrice(),
                BrokerApiEnums.orderType(request.orderType()),
                BrokerApiEnums.segment(request.segment())));
    }

    @PostMapping("/orders/cancel")
    public BrokerOrderReference cancelOrder(@RequestBody CancelOrderApiRequest request) {
        return execution.cancelOrder(
                new BrokerCancelOrderRequest(request.brokerOrderId(), BrokerApiEnums.segment(request.segment())));
    }

    @PostMapping("/smart-orders")
    public BrokerSmartOrder createSmartOrder(@RequestBody SmartOrderCreateApiRequest request) {
        return smartOrderExecution.createSmartOrder(new BrokerSmartOrderRequest(
                BrokerApiEnums.smartOrderType(request.smartOrderType()),
                request.referenceId(),
                BrokerApiEnums.exchange(request.exchange()),
                BrokerApiEnums.segment(request.segment()),
                request.tradingSymbol(),
                request.quantity(),
                BrokerApiEnums.product(request.product()),
                BrokerApiEnums.validity(request.duration()),
                request.triggerPrice(),
                request.triggerDirection(),
                request.limitPrice(),
                BrokerApiEnums.orderType(request.orderType()),
                BrokerApiEnums.transactionType(request.transactionType()),
                request.netPositionQuantity(),
                request.targetTriggerPrice(),
                request.stopLossTriggerPrice(),
                null));
    }

    @PutMapping("/smart-orders/{smartOrderId}")
    public BrokerSmartOrder modifySmartOrder(
            @PathVariable String smartOrderId, @RequestBody SmartOrderModifyApiRequest request) {
        return smartOrderExecution.modifySmartOrder(
                smartOrderId,
                new BrokerSmartOrderModifyRequest(
                        BrokerApiEnums.smartOrderType(request.smartOrderType()),
                        BrokerApiEnums.segment(request.segment()),
                        request.quantity(),
                        request.triggerPrice(),
                        request.triggerDirection(),
                        request.limitPrice(),
                        request.orderType() == null ? null : BrokerApiEnums.orderType(request.orderType()),
                        request.product() == null ? null : BrokerApiEnums.product(request.product()),
                        request.duration() == null ? null : BrokerApiEnums.validity(request.duration()),
                        request.targetTriggerPrice(),
                        request.stopLossTriggerPrice()));
    }

    @PostMapping("/smart-orders/cancel/{segment}/{type}/{smartOrderId}")
    public BrokerSmartOrder cancelSmartOrder(
            @PathVariable BrokerSegment segment,
            @PathVariable BrokerSmartOrderType type,
            @PathVariable String smartOrderId) {
        return smartOrderExecution.cancelSmartOrder(segment, type, smartOrderId);
    }
}
