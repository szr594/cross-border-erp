package com.szr.crosserp.order.dto;

import java.util.List;

public record OrderPageResponse(
    List<OrderListItemResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    OrderSummaryResponse summary
) {
}
