package com.szr.crosserp.procurement.dto;

import com.szr.crosserp.order.dto.UserSummaryResponse;
import java.time.Instant;

public record PurchaseOrderEventResponse(
    Long id, String eventType, String fromValue, String toValue,
    UserSummaryResponse operator, String remark, Instant occurredAt
) {
}
