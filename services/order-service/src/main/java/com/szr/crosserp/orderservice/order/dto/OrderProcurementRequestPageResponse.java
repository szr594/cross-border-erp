package com.szr.crosserp.orderservice.order.dto;

import java.util.List;

public record OrderProcurementRequestPageResponse(
    List<OrderProcurementRequestDetailResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
