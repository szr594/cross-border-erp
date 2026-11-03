package com.szr.crosserp.receiving.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateDomesticPackageRequest(
    @NotNull Long purchaseOrderId,
    @NotBlank @Size(max = 128) String trackingNumber,
    @NotBlank @Size(max = 100) String carrier,
    Instant expectedAt,
    @Size(max = 500) String note
) {
}
