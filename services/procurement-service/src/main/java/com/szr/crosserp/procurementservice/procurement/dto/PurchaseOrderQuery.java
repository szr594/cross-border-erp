package com.szr.crosserp.procurementservice.procurement.dto;

import com.szr.crosserp.procurementservice.procurement.model.PurchaseOrderStatus;

public record PurchaseOrderQuery(
    int page,
    int size,
    String keyword,
    Long supplierId,
    PurchaseOrderStatus status,
    String sort
) {
}
