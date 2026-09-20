package com.edgerelative.application.broker;

import com.edgerelative.broker.api.model.BrokerOrder;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerSmartOrder;
import com.edgerelative.broker.api.model.BrokerSmartOrderStatus;
import com.edgerelative.broker.api.model.BrokerSmartOrderType;
import com.edgerelative.broker.api.model.BrokerTrade;
import com.edgerelative.broker.api.model.OrderListQuery;
import com.edgerelative.broker.api.model.SmartOrderListQuery;
import com.edgerelative.broker.api.model.TradeListQuery;
import com.edgerelative.broker.api.port.OrderQueryBroker;
import com.edgerelative.broker.api.port.SmartOrderQueryBroker;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only Groww order, trade and smart-order queries.
 */
@RestController
@RequestMapping("/api/v1/brokers/groww")
public class GrowwOrderQueryController {

    private final OrderQueryBroker orders;
    private final SmartOrderQueryBroker smartOrders;

    public GrowwOrderQueryController(OrderQueryBroker orders, SmartOrderQueryBroker smartOrders) {
        this.orders = orders;
        this.smartOrders = smartOrders;
    }

    @GetMapping("/orders/status/reference/{referenceId}")
    public BrokerOrder orderStatusByReference(
            @PathVariable String referenceId, @RequestParam BrokerSegment segment) {
        return orders.orderStatusByReference(referenceId, segment);
    }

    @GetMapping("/orders/status/{orderId}")
    public BrokerOrder orderStatus(@PathVariable String orderId, @RequestParam BrokerSegment segment) {
        return orders.orderStatus(orderId, segment);
    }

    @GetMapping("/orders/detail/{orderId}")
    public BrokerOrder orderDetail(@PathVariable String orderId, @RequestParam BrokerSegment segment) {
        return orders.orderDetail(orderId, segment);
    }

    @GetMapping("/orders")
    public List<BrokerOrder> orderList(
            @RequestParam(required = false) BrokerSegment segment,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return orders.orders(new OrderListQuery(segment, page, pageSize));
    }

    @GetMapping("/orders/{orderId}/trades")
    public List<BrokerTrade> trades(
            @PathVariable String orderId,
            @RequestParam BrokerSegment segment,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return orders.trades(orderId, new TradeListQuery(segment, page, pageSize));
    }

    @GetMapping("/smart-orders")
    public List<BrokerSmartOrder> smartOrderList(
            @RequestParam(required = false) BrokerSegment segment,
            @RequestParam(required = false) BrokerSmartOrderType type,
            @RequestParam(required = false) BrokerSmartOrderStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDateTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDateTime) {
        return smartOrders.smartOrders(
                new SmartOrderListQuery(segment, type, status, page, pageSize, startDateTime, endDateTime));
    }

    @GetMapping("/smart-orders/{segment}/{type}/{smartOrderId}")
    public BrokerSmartOrder smartOrder(
            @PathVariable BrokerSegment segment,
            @PathVariable BrokerSmartOrderType type,
            @PathVariable String smartOrderId) {
        return smartOrders.smartOrder(segment, type, smartOrderId);
    }
}
