package com.szr.crosserp.procurementservice.procurement.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record SupplierStatusRequest(
    @PositiveOrZero(message = "版本号无效") long version
) {
}
