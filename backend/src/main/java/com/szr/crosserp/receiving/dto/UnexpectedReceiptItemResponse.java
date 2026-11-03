package com.szr.crosserp.receiving.dto;

public record UnexpectedReceiptItemResponse(
    Long id, String description, String supplierSku, int quantity, String note
) {
}
