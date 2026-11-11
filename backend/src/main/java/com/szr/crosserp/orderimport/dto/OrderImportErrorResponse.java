package com.szr.crosserp.orderimport.dto;

public record OrderImportErrorResponse(
    int rowNumber,
    String field,
    String code,
    String message
) {}
