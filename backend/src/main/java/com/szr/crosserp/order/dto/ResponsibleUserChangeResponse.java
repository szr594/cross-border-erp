package com.szr.crosserp.order.dto;

public record ResponsibleUserChangeResponse(Long orderId, UserSummaryResponse responsibleUser, long version) {
}
