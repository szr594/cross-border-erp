package com.szr.crosserp.inventory.dto;

import com.szr.crosserp.inventory.model.InventoryLotStatus;
import com.szr.crosserp.inventory.model.InventoryQualityStatus;
import java.time.Instant;
import java.util.List;

public record InventoryLotDetailResponse(
    Long id, String lotNo, InventoryProductResponse product,
    InventoryPurchaseSourceResponse purchaseSource, InventoryReceiptSourceResponse receiptSource,
    InventoryQualityStatus qualityStatus, InventoryLotStatus status,
    int initialQuantity, int availableQuantity, int reservedQuantity,
    int quarantinedQuantity, int consumedQuantity, int adjustedOutQuantity,
    long version, Instant receivedAt, Instant createdAt, Instant updatedAt,
    List<InventoryAllocationResponse> allocations
) {
}
