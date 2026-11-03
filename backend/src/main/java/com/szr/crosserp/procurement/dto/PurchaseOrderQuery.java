package com.szr.crosserp.procurement.dto;

import com.szr.crosserp.procurement.model.PurchaseOrderStatus;

public record PurchaseOrderQuery(
    int page,
    int size,
    String keyword,
    Long supplierId,
    PurchaseOrderStatus status,
    String sort
) {
}
