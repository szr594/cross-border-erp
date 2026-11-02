package com.szr.crosserp.procurement.dto;

public record PurchaseAllocationResponse(
    Long id,
    Long procurementRequestId,
    int quantity
) {
}
