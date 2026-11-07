package com.szr.crosserp.inventory.dto;

import java.util.List;

public record InventoryPostingResponse(
    Long receiptId,
    boolean inventoryPosted,
    List<Long> lotIds,
    List<Long> allocationIds
) {
}
