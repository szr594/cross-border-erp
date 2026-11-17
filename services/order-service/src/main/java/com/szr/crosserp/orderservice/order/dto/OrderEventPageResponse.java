package com.szr.crosserp.orderservice.order.dto;

import java.util.List;

public record OrderEventPageResponse(
    List<OrderEventResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
