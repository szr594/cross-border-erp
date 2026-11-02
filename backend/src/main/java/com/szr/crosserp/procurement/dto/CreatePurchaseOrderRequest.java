package com.szr.crosserp.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreatePurchaseOrderRequest(
    @NotNull(message = "供应商不能为空") Long supplierId,
    @NotNull(message = "币种不能为空")
    @Pattern(regexp = "[A-Za-z]{3}", message = "币种必须为三位字母") String currency,
    @Size(max = 1000, message = "备注不能超过1000个字符") String note,
    @NotEmpty(message = "采购单必须包含采购明细") List<@Valid CreatePurchaseOrderItemCommand> items
) {
}
