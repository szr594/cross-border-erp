package com.szr.crosserp.inventory.dto;

import com.szr.crosserp.inventory.model.InventoryAllocationStatus;
import java.time.Instant;

public record InventoryAllocationResponse(
    Long id, String allocationNo, Long lotId, String lotNo,
    Long salesOrderId, String orderNo, Long salesOrderItemId,
    String productName, String variantName, int quantity,
    InventoryAllocationStatus status, long version, Instant allocatedAt
) {
}
