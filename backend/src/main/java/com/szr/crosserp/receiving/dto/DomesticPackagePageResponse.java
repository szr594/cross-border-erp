package com.szr.crosserp.receiving.dto;

import java.util.List;

public record DomesticPackagePageResponse(
    List<DomesticPackageListItemResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
