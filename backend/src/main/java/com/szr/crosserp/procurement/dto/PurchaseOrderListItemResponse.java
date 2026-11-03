package com.szr.crosserp.procurement.dto;

import com.szr.crosserp.procurement.model.PurchaseOrderStatus;
import java.time.Instant;

public record PurchaseOrderListItemResponse(
    Long id, String purchaseOrderNo, PurchaseSupplierSummaryResponse supplier,
    PurchaseOrderStatus status, String currency, long totalAmountMinor,
    int itemCount, int purchasedQuantity, Instant createdAt
) {
}
