package com.szr.crosserp.inventory.dto;

import java.util.List;

public record InventoryLotPageResponse(
    List<InventoryLotListItemResponse> items,
    int page, int size, long totalElements, int totalPages
) {
}
