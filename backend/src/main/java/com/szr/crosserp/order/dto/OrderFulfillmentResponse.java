package com.szr.crosserp.order.dto;

import com.szr.crosserp.order.model.FulfillmentStage;
import java.util.List;

public record OrderFulfillmentResponse(
    Long orderId,
    FulfillmentStage stage,
    String stageName,
    List<OrderItemResponse> items
) {
}
