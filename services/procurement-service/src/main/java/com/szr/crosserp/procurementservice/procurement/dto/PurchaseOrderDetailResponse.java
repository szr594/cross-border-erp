package com.szr.crosserp.procurementservice.procurement.dto;

import com.szr.crosserp.procurementservice.procurement.dto.UserSummaryResponse;
import com.szr.crosserp.procurementservice.procurement.model.PurchaseOrderStatus;
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
