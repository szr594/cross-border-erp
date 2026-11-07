package com.szr.crosserp.inventory.dto;

import java.util.List;

public record OrderInventoryItemResponse(
    Long orderItemId, String externalSku, String productName, String variantName,
    int requiredQuantity, int reservedQuantity, int consumedQuantity,
    int shortageQuantity, List<InventoryAllocationResponse> allocations
) {
}
