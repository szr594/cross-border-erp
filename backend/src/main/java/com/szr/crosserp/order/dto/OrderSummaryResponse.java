package com.szr.crosserp.order.dto;

public record OrderSummaryResponse(
    long pending,
    long waitingProcurement,
    long readyForPacking,
    long exception
) {
}
