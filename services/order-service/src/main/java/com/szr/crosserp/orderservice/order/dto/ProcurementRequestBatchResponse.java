package com.szr.crosserp.orderservice.order.dto;

import java.util.List;

public record ProcurementRequestBatchResponse(List<ProcurementRequestResponse> requests, long version) {
}
