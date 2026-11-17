package com.szr.crosserp.orderservice.order.dto;

import com.szr.crosserp.orderservice.order.model.ProcurementRequestStatus;
import java.time.Instant;

public record OrderProcurementRequestDetailResponse(
    Long id,
    ProcurementOrderItemResponse orderItem,
    int requestedQuantity,
    ProcurementRequestStatus status,
    UserSummaryResponse requestedBy,
    Instant requestedAt,
    UserSummaryResponse withdrawnBy,
    Instant withdrawnAt,
    String note,
    long version,
    boolean canWithdraw
) {
}
