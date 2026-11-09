package com.szr.crosserp.inventory.dto;

public record AdjustInventoryRequest(
    InventoryAdjustmentType type, Integer quantity, String reason, String idempotencyKey, Long version
) {
}
