package com.szr.crosserp.procurement.dto;

import java.util.List;

public record PurchaseOrderPageResponse(
    List<PurchaseOrderListItemResponse> items,
    int page, int size, long totalElements, int totalPages
) {
}
