package com.szr.crosserp.receiving.dto;

import java.util.List;

public record ReceivingEventPageResponse(
    List<ReceivingEventResponse> items, int page, int size, long totalElements, int totalPages
) {
}
