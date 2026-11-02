package com.szr.crosserp.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateProcurementRequestsRequest(
    @NotEmpty List<@Valid ProcurementRequestItemCommand> items,
    @Size(max = 500) String note,
    @PositiveOrZero long version
) {
}
