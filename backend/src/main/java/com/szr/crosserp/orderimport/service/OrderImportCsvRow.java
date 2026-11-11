package com.szr.crosserp.orderimport.service;

record OrderImportCsvRow(
    int rowNumber,
    String storeCode,
    String externalOrderId,
    String displayOrderNo,
    String externalStatus,
    String orderedAt,
    String requestedShipAt,
    String currency,
    String responsibleLogin,
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
    String postalCode,
    String externalItemId,
    String externalProductId,
    String externalVariantId,
    String externalSku,
    String productName,
    String variantName,
    String imageUrl,
    String quantity,
    String unitPriceMinor,
    String note
) {
    String orderKey() { return storeCode + "\u001f" + externalOrderId; }
}
