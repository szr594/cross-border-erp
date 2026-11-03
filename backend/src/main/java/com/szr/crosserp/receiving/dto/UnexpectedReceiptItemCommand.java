package com.szr.crosserp.receiving.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnexpectedReceiptItemCommand(
    @NotBlank @Size(max = 500) String description,
    @Size(max = 128) String supplierSku,
    @Min(1) int quantity,
    @Size(max = 500) String note
) {
}
