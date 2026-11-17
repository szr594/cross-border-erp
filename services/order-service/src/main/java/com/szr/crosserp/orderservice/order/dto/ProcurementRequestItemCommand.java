package com.szr.crosserp.orderservice.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProcurementRequestItemCommand(@NotNull Long orderItemId, @Positive int quantity) {
}
