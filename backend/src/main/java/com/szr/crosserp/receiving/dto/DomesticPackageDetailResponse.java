package com.szr.crosserp.receiving.dto;

import com.szr.crosserp.procurement.model.PurchaseOrderStatus;
import com.szr.crosserp.receiving.model.DomesticPackageStatus;
import java.time.Instant;
import java.util.List;

public record DomesticPackageDetailResponse(
    Long id, String packageNo, String trackingNumber, String carrier, DomesticPackageStatus status,
    Long purchaseOrderId, String purchaseOrderNo, PurchaseOrderStatus purchaseOrderStatus,
    ReceivingSupplierResponse supplier, Instant expectedAt, Instant firstReceivedAt, Instant completedAt,
    String createdByName, String note, long version, Instant createdAt, Instant updatedAt,
    List<ReceivingPurchaseItemResponse> items, List<GoodsReceiptSummaryResponse> receipts,
    List<String> availableActions
) {
}
