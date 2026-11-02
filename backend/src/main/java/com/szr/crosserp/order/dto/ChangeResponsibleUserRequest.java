package com.szr.crosserp.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ChangeResponsibleUserRequest(
    @NotNull Long userId,
    @NotBlank @Size(max = 500) String reason,
    @PositiveOrZero long version
) {
}
