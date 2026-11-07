package com.szr.crosserp.inventory.dto;

import java.util.List;

public record InventoryMovementPageResponse(
    List<InventoryMovementResponse> items,
    int page, int size, long totalElements, int totalPages
) {
}
