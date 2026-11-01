package com.szr.crosserp.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerResponse(
    String customerId,
    String customerName,
    String recipientName,
    String recipientPhone,
    String countryCode,
    String province,
    String city,
    String district,
    String addressLine1,
    String addressLine2,
    String postalCode
) {
}
