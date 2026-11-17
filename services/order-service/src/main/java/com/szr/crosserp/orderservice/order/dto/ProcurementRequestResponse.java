package com.szr.crosserp.orderservice.order.dto;

import com.szr.crosserp.orderservice.order.model.ProcurementRequestStatus;

public record ProcurementRequestResponse(
    Long id, Long orderItemId, int quantity, ProcurementRequestStatus status, long version
) {
}
