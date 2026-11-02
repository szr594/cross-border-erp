package com.szr.crosserp.order.dto;

public record ProcurementOrderItemResponse(
    Long id, String externalSku, String productName, String variantName
) {
}
