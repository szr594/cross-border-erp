package com.szr.crosserp.orderservice.order.dto;

import com.szr.crosserp.orderservice.order.model.FulfillmentStage;
import java.util.List;

public record OrderFulfillmentResponse(
    Long orderId,
    FulfillmentStage stage,
    String stageName,
    List<OrderItemResponse> items
) {
}
