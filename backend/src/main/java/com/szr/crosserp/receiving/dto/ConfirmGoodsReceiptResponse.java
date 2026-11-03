package com.szr.crosserp.receiving.dto;

import com.szr.crosserp.procurement.model.PurchaseOrderStatus;
import com.szr.crosserp.receiving.model.DomesticPackageStatus;

public record ConfirmGoodsReceiptResponse(
    Long receiptId, String receiptNo, Long packageId, DomesticPackageStatus packageStatus,
    PurchaseOrderStatus purchaseOrderStatus, boolean inventoryPosted
) {
}
