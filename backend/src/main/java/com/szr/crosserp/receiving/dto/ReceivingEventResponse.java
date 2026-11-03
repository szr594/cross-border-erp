package com.szr.crosserp.receiving.dto;

import java.time.Instant;

public record ReceivingEventResponse(
    Long id, String eventType, Long receiptId, String operatorName, String remark, Instant occurredAt
) {
}
