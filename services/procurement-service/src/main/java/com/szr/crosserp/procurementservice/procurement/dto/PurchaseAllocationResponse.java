package com.szr.crosserp.procurementservice.procurement.dto;

public record PurchaseAllocationResponse(
    Long id,
    Long procurementRequestId,
    int quantity
) {
}
