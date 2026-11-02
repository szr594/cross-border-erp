package com.szr.crosserp.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreatePurchaseOrderItemCommand(
    @Size(max = 128, message = "供应商商品编号不能超过128个字符") String supplierProductId,
    @Size(max = 128, message = "供应商SKU不能超过128个字符") String supplierSku,
    @NotBlank(message = "商品名称不能为空") @Size(max = 500, message = "商品名称不能超过500个字符") String productName,
    @Size(max = 500, message = "规格名称不能超过500个字符") String variantName,
    @Size(max = 1000, message = "商品链接不能超过1000个字符") String productUrl,
    @Size(max = 1000, message = "图片链接不能超过1000个字符") String imageUrl,
    @PositiveOrZero(message = "采购单价不能小于0") Long unitPriceMinor,
    @NotEmpty(message = "采购明细必须包含需求分配") List<@Valid PurchaseAllocationCommand> allocations
) {
}
