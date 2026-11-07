package com.szr.crosserp.inventory.dto;

import com.szr.crosserp.inventory.model.InventoryMovementType;
import com.szr.crosserp.order.dto.UserSummaryResponse;
import java.time.Instant;

public record InventoryMovementResponse(
    Long id, String movementNo, InventoryMovementType movementType, int quantity,
    int availableAfter, int reservedAfter, int quarantinedAfter,
    Long allocationId, String allocationNo, UserSummaryResponse operator,
    String reason, String idempotencyKey, Instant occurredAt
) {
}
