package com.szr.crosserp.procurementservice.procurement.dto;



public record PurchaseSourceResponse(
    Long allocationId, Long procurementRequestId, int allocatedQuantity,
    String procurementStatus,
    Long salesOrderId, String salesOrderNo,
    Long salesOrderItemId, String productName, String variantName
) {
}
