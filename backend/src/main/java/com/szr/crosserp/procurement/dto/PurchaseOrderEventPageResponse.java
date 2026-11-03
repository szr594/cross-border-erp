package com.szr.crosserp.procurement.dto;

import java.util.List;

public record PurchaseOrderEventPageResponse(
    List<PurchaseOrderEventResponse> items,
    int page, int size, long totalElements, int totalPages
) {
}
