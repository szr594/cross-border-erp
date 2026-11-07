package com.szr.crosserp.inventory.dto;

import java.time.Instant;

public record InventoryReceiptSourceResponse(
    Long receiptId, String receiptNo, Long packageId, String packageNo,
    String carrier, String trackingNumber, Instant confirmedAt
) {
}
