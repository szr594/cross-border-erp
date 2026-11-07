package com.szr.crosserp.inventory.dto;

import com.szr.crosserp.procurement.model.PurchaseOrderStatus;

public record InventoryPurchaseSourceResponse(
    Long purchaseOrderId, String purchaseOrderNo, PurchaseOrderStatus status,
    Long supplierId, String supplierCode, String supplierName
) {
}
