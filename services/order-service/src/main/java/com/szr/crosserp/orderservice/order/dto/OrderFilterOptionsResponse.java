package com.szr.crosserp.orderservice.order.dto;

import java.util.List;

public record OrderFilterOptionsResponse(
    List<StoreSummaryResponse> stores,
    List<UserSummaryResponse> responsibleUsers,
    List<NamedOptionResponse> externalStatuses,
    List<NamedOptionResponse> fulfillmentStages
) {
}
