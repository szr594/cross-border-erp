package com.szr.crosserp.inventory.dto;

public record ManualInventoryAllocationRequest(
    Long orderItemId, Integer quantity, String reason, String idempotencyKey, Long version
) {
}
