package com.szr.crosserp.orderservice.order.dto;

import com.szr.crosserp.orderservice.order.model.ExternalOrderStatus;

public record OrderCommandResponse(
    Long orderId, ExternalOrderStatus externalStatus, boolean exception, long version
) {
}
