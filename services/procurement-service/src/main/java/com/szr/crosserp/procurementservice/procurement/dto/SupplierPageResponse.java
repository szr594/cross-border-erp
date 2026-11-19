package com.szr.crosserp.procurementservice.procurement.dto;

import java.util.List;

public record SupplierPageResponse(
    List<SupplierResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
