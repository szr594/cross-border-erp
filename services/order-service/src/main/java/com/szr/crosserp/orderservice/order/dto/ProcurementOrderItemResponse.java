package com.szr.crosserp.orderservice.order.dto;

public record ProcurementOrderItemResponse(
    Long id, String externalSku, String productName, String variantName
) {
}
