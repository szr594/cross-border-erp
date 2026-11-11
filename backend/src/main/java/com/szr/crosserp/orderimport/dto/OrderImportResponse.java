package com.szr.crosserp.orderimport.dto;

import java.time.Instant;

public record OrderImportResponse(
    String importId,
    String status,
    String fileName,
    int totalRows,
    int orderCount,
    int itemCount,
    int creatableOrderCount,
    int skippedOrderCount,
    int errorCount,
    Instant createdAt,
    Instant expiresAt,
    Instant confirmedAt,
    Instant completedAt
) {}
