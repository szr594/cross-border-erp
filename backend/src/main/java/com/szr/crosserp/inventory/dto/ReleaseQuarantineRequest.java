package com.szr.crosserp.inventory.dto;

public record ReleaseQuarantineRequest(
    Integer quantity, String reason, String idempotencyKey, Long version
) {
}
