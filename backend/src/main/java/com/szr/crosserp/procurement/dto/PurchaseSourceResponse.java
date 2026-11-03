package com.szr.crosserp.procurement.dto;

import com.szr.crosserp.order.model.ProcurementRequestStatus;

public record PurchaseSourceResponse(
    Long allocationId, Long procurementRequestId, int allocatedQuantity,
    ProcurementRequestStatus procurementStatus,
    Long salesOrderId, String salesOrderNo,
    Long salesOrderItemId, String productName, String variantName
) {
}
