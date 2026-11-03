package com.szr.crosserp.receiving.dto;

import com.szr.crosserp.receiving.model.DomesticPackageStatus;
import java.time.Instant;

public record DomesticPackageListItemResponse(
    Long id, String packageNo, String trackingNumber, String carrier, DomesticPackageStatus status,
    Long purchaseOrderId, String purchaseOrderNo, ReceivingSupplierResponse supplier,
    int purchasedQuantity, int acceptedQuantity, int damagedQuantity,
    Instant firstReceivedAt, Instant completedAt, Instant createdAt
) {
}
