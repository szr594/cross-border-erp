package com.szr.crosserp.order.dto;

import com.szr.crosserp.order.model.ExternalOrderStatus;
import java.time.Instant;

public record OrderQuery(
    int page,
    int size,
    String keyword,
    Long storeId,
    Long responsibleUserId,
    ExternalOrderStatus externalStatus,
    String stage,
    Instant orderedFrom,
    Instant orderedTo,
    boolean exceptionOnly,
    String sort
) {
}
