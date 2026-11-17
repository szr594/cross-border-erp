package com.szr.crosserp.orderservice.orderimport.dto;

public record OrderImportErrorResponse(
    int rowNumber,
    String field,
    String code,
    String message
) {}
