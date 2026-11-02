package com.szr.crosserp.order.dto;

import com.szr.crosserp.order.model.ProcurementRequestStatus;

public record ProcurementRequestResponse(
    Long id, Long orderItemId, int quantity, ProcurementRequestStatus status, long version
) {
}
