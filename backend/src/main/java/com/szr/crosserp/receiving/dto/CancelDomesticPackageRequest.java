package com.szr.crosserp.receiving.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelDomesticPackageRequest(
    @NotBlank @Size(max = 500) String reason,
    long version
) {
}
