package com.szr.crosserp.order.dto;

import java.util.List;

public record OrderEventPageResponse(
    List<OrderEventResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
