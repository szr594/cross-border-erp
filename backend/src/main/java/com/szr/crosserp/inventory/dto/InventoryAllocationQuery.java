package com.szr.crosserp.inventory.dto;

import com.szr.crosserp.inventory.model.InventoryAllocationStatus;

public record InventoryAllocationQuery(
    int page, int size, String orderNo, String lotNo, InventoryAllocationStatus status
) {
}
