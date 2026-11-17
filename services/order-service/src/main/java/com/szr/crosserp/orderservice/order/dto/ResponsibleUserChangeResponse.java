package com.szr.crosserp.orderservice.order.dto;

public record ResponsibleUserChangeResponse(Long orderId, UserSummaryResponse responsibleUser, long version) {
}
