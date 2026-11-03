package com.szr.crosserp.receiving.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConfirmReceiptItemCommand(
    @NotNull Long purchaseOrderItemId,
    @Min(0) int acceptedQuantity,
    @Min(0) int damagedQuantity,
    @Size(max = 500) String note
) {
}
