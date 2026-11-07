package com.szr.crosserp.inventory.dto;

import com.szr.crosserp.inventory.model.InventoryLotStatus;
import com.szr.crosserp.inventory.model.InventoryQualityStatus;
import java.time.Instant;

public record InventoryLotListItemResponse(
    Long id, String lotNo, InventoryProductResponse product,
    InventoryPurchaseSourceResponse purchaseSource, InventoryQualityStatus qualityStatus,
    InventoryLotStatus status, int initialQuantity, int availableQuantity,
    int reservedQuantity, int quarantinedQuantity, int consumedQuantity,
    int adjustedOutQuantity, Instant receivedAt
) {
}
