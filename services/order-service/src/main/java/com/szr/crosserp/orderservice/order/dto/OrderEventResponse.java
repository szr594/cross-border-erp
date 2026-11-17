package com.szr.crosserp.orderservice.order.dto;

import com.szr.crosserp.orderservice.order.model.OrderEventSource;
import java.time.Instant;

public record OrderEventResponse(
    Long id,
    String eventType,
    String fromValue,
    String toValue,
    UserSummaryResponse operator,
    OrderEventSource source,
    String remark,
    Instant occurredAt
) {
}
