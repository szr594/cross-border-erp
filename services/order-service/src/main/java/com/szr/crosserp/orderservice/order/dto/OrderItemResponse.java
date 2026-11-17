package com.szr.crosserp.orderservice.order.dto;

import com.szr.crosserp.orderservice.order.model.OrderItemFulfillmentStatus;

public record OrderItemResponse(
    Long id,
    String externalItemId,
    String externalSku,
    Long erpSkuId,
    String productName,
    String variantName,
    String imageUrl,
    int quantity,
    Long unitPriceMinor,
    String currency,
    OrderItemFulfillmentStatus fulfillmentStatus,
    int inventoryAllocatedQuantity,
    int procurementRequestedQuantity,
    int procurementInProgressQuantity,
    int receivedQuantity,
    int missingQuantity,
    boolean ready
) {
}
