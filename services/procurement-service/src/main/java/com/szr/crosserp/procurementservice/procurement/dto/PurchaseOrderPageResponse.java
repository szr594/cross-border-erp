package com.szr.crosserp.procurementservice.procurement.dto;

import java.util.List;

public record PurchaseOrderPageResponse(
    List<PurchaseOrderListItemResponse> items,
    int page, int size, long totalElements, int totalPages
) {
}
