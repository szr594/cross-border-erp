package com.szr.crosserp.inventory.dto;

public record InventoryProductResponse(
    Long purchaseOrderItemId, String supplierSku, String productName,
    String variantName, String imageUrl
) {
}
