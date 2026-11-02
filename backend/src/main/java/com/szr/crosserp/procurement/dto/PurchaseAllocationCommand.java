package com.szr.crosserp.procurement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record PurchaseAllocationCommand(
    @NotNull(message = "采购需求不能为空") Long procurementRequestId,
    @Positive(message = "分配数量必须大于0") int quantity,
    @PositiveOrZero(message = "版本号无效") long version
) {
}
