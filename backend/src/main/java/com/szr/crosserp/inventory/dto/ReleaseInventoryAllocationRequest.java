package com.szr.crosserp.inventory.dto;

public record ReleaseInventoryAllocationRequest(
    String reason, String idempotencyKey, Long version
) {
}
