package com.szr.crosserp.order.dto;

import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.model.FulfillmentStage;
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
