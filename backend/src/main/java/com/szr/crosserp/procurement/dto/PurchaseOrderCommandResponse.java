package com.szr.crosserp.procurement.dto;

import com.szr.crosserp.procurement.model.PurchaseOrderStatus;
import java.time.Instant;
import java.util.List;

public record PurchaseOrderCommandResponse(
    Long id,
    String purchaseOrderNo,
    Long supplierId,
    PurchaseOrderStatus status,
    String currency,
    long totalAmountMinor,
    long version,
    Instant createdAt,
    List<PurchaseOrderItemResponse> items
) {
}
