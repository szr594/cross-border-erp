package com.szr.crosserp.inventory.dto;

import java.util.List;

public record OrderInventoryCoverageResponse(
    Long orderId, String orderNo, List<OrderInventoryItemResponse> items
) {
}
