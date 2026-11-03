package com.szr.crosserp.procurement.dto;

import com.szr.crosserp.order.dto.UserSummaryResponse;
import com.szr.crosserp.procurement.model.PurchaseOrderStatus;
import java.time.Instant;
import java.util.List;

public record PurchaseOrderDetailResponse(
    Long id, String purchaseOrderNo, SupplierResponse supplier,
    PurchaseOrderStatus status, String externalOrderNo, String currency,
    long totalAmountMinor, Instant orderedAt, Instant expectedArrivalAt,
    UserSummaryResponse createdBy, String note, long version,
    Instant createdAt, Instant updatedAt,
    List<PurchaseOrderDetailItemResponse> items
) {
}
