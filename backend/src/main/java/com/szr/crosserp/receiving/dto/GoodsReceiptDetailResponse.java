package com.szr.crosserp.receiving.dto;

import java.time.Instant;
import java.util.List;

public record GoodsReceiptDetailResponse(
    Long id, String receiptNo, Long packageId, String packageNo, String operatorName,
    Instant confirmedAt, String note, boolean inventoryPosted,
    List<GoodsReceiptItemResponse> items, List<UnexpectedReceiptItemResponse> unexpectedItems
) {
}
