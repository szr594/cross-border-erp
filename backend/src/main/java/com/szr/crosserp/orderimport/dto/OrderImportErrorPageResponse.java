package com.szr.crosserp.orderimport.dto;

import java.util.List;

public record OrderImportErrorPageResponse(
    List<OrderImportErrorResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
