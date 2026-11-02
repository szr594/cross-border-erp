package com.szr.crosserp.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ReasonVersionRequest(@NotBlank @Size(max = 500) String reason, @PositiveOrZero long version) {
}
