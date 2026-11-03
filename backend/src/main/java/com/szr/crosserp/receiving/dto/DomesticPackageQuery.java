package com.szr.crosserp.receiving.dto;

import com.szr.crosserp.receiving.model.DomesticPackageStatus;

public record DomesticPackageQuery(
    int page, int size, String keyword, Long supplierId, DomesticPackageStatus status, String sort
) {
}
