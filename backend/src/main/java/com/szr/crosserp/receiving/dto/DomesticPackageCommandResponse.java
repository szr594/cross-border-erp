package com.szr.crosserp.receiving.dto;

import com.szr.crosserp.receiving.model.DomesticPackageStatus;

public record DomesticPackageCommandResponse(
    Long id, String packageNo, String trackingNumber, DomesticPackageStatus status, long version
) {
}
