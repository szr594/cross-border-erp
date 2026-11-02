package com.szr.crosserp.procurement.dto;

import com.szr.crosserp.procurement.model.SupplierPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateSupplierRequest(
    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 200, message = "供应商名称不能超过200个字符")
    String name,
    @NotNull(message = "供应商平台不能为空") SupplierPlatform platform,
    @Size(max = 128, message = "外部供应商编号不能超过128个字符") String externalSupplierId,
    @Size(max = 100, message = "联系人不能超过100个字符") String contactName,
    @Size(max = 64, message = "联系电话不能超过64个字符") String contactPhone,
    @Size(max = 1000, message = "店铺链接不能超过1000个字符") String shopUrl,
    @Size(max = 500, message = "备注不能超过500个字符") String note,
    @PositiveOrZero(message = "版本号无效") long version
) {
}
