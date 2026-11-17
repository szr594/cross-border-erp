package com.szr.crosserp.orderservice.order.dto;

import com.szr.crosserp.orderservice.order.model.ExternalOrderStatus;
import com.szr.crosserp.orderservice.order.model.FulfillmentStage;
import java.time.Instant;
import java.util.List;

public record OrderListItemResponse(
    Long id,
    String orderNo,
    String displayOrderNo,
    StoreSummaryResponse store,
    UserSummaryResponse responsibleUser,
    ExternalOrderStatus externalStatus,
    FulfillmentStage stage,
    String stageName,
    boolean exception,
    Instant orderedAt,
    Instant requestedShipAt,
    int itemCount,
    int totalQuantity,
    List<String> productNames
) {
}
