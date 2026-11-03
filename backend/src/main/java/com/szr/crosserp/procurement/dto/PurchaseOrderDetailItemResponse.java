package com.szr.crosserp.procurement.dto;

import java.util.List;

public record PurchaseOrderDetailItemResponse(
    Long id, String supplierProductId, String supplierSku,
    String productName, String variantName, String productUrl, String imageUrl,
    int purchasedQuantity, int receivedQuantity, Long unitPriceMinor,
    String currency, String note, long version,
    List<PurchaseSourceResponse> sources
) {
}
