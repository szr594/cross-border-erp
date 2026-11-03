package com.szr.crosserp.receiving.dto;

public record GoodsReceiptItemResponse(
    Long id, Long purchaseOrderItemId, String productName, String variantName,
    int acceptedQuantity, int damagedQuantity, String note
) {
}
