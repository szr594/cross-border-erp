package com.szr.crosserp.inventory.dto;

import com.szr.crosserp.inventory.model.InventoryLotStatus;
import com.szr.crosserp.inventory.model.InventoryQualityStatus;

public record InventoryLotQuery(
    int page, int size, String keyword, Long purchaseOrderId,
    InventoryLotStatus status, InventoryQualityStatus qualityStatus
) {
}
