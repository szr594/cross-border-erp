package com.szr.crosserp.procurement.dto;

import java.util.List;

public record SupplierPageResponse(
    List<SupplierResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
