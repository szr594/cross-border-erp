package com.szr.crosserp.procurement.dto;

import java.util.List;

public record PurchaseOrderItemResponse(
    Long id,
    String supplierProductId,
    String supplierSku,
    String productName,
    String variantName,
    int purchasedQuantity,
    Long unitPriceMinor,
    String currency,
    List<PurchaseAllocationResponse> allocations
) {
}
