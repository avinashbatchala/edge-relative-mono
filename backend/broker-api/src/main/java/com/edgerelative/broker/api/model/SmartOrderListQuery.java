package com.edgerelative.broker.api.model;

import java.time.Instant;

/** Filter/pagination for smart order listing. */
public record SmartOrderListQuery(
        BrokerSegment segment,
        BrokerSmartOrderType smartOrderType,
        BrokerSmartOrderStatus status,
        Integer page,
        Integer pageSize,
        Instant startDateTime,
        Instant endDateTime) {
}
