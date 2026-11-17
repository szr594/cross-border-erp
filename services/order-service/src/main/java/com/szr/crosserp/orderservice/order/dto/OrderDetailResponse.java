package com.szr.crosserp.orderservice.order.dto;

import com.szr.crosserp.orderservice.order.model.ExternalOrderStatus;
import com.szr.crosserp.orderservice.order.model.FulfillmentStage;
import java.time.Instant;
import java.util.List;

public record OrderDetailResponse(
    Long id,
    String orderNo,
    String externalOrderId,
    String displayOrderNo,
    StoreSummaryResponse store,
    UserSummaryResponse responsibleUser,
    ExternalOrderStatus externalStatus,
    String externalStatusRaw,
    FulfillmentStage stage,
    String stageName,
    boolean exception,
    String exceptionReason,
    String currency,
    Instant orderedAt,
    Instant shipAfter,
    Instant requestedShipAt,
    CustomerResponse customer,
    String note,
    long version,
    List<OrderItemResponse> items,
    List<String> availableActions
) {
}
