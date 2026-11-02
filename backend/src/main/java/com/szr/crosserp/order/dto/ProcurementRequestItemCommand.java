package com.szr.crosserp.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProcurementRequestItemCommand(@NotNull Long orderItemId, @Positive int quantity) {
}
