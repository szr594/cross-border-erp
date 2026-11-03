package com.szr.crosserp.receiving.dto;

public record ReceivingPurchaseItemResponse(
    Long id, String supplierSku, String productName, String variantName,
    int purchasedQuantity, int acceptedQuantity, int damagedQuantity, int remainingQuantity, long version
) {
}
