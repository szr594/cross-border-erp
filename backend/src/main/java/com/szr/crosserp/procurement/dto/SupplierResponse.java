package com.szr.crosserp.procurement.dto;

import com.szr.crosserp.procurement.model.SupplierPlatform;
import com.szr.crosserp.procurement.model.SupplierStatus;
import java.time.Instant;

public record SupplierResponse(
    Long id,
    String code,
    String name,
    SupplierPlatform platform,
    String externalSupplierId,
    String contactName,
    String contactPhone,
    String shopUrl,
    String note,
    SupplierStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt
) {
}
