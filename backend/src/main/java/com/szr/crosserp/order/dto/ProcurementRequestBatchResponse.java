package com.szr.crosserp.order.dto;

import java.util.List;

public record ProcurementRequestBatchResponse(List<ProcurementRequestResponse> requests, long version) {
}
