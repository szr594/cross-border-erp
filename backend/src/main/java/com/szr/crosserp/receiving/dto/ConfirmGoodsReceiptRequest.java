package com.szr.crosserp.receiving.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ConfirmGoodsReceiptRequest(
    @NotBlank @Size(max = 64) String idempotencyKey,
    long packageVersion,
    @NotNull List<@Valid ConfirmReceiptItemCommand> items,
    @NotNull List<@Valid UnexpectedReceiptItemCommand> unexpectedItems,
    @Size(max = 500) String note
) {
}
