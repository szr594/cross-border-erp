package com.szr.crosserp.order.dto;

import com.szr.crosserp.order.model.ExternalOrderStatus;

public record OrderCommandResponse(
    Long orderId, ExternalOrderStatus externalStatus, boolean exception, long version
) {
}
