package com.szr.crosserp.receiving.dto;

import java.time.Instant;

public record GoodsReceiptSummaryResponse(
    Long id, String receiptNo, String operatorName, Instant confirmedAt,
    int acceptedQuantity, int damagedQuantity, int unexpectedQuantity
) {
}
