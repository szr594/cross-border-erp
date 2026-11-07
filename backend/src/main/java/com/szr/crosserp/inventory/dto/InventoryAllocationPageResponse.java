package com.szr.crosserp.inventory.dto;

import java.util.List;

public record InventoryAllocationPageResponse(
    List<InventoryAllocationResponse> items,
    int page, int size, long totalElements, int totalPages
) {
}
