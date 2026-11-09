package com.szr.crosserp.inventory.dto;

public record InventoryCommandResponse(
    Long lotId,
    String lotNo,
    Long allocationId,
    String allocationNo,
    String allocationStatus,
    int availableQuantity,
    int reservedQuantity,
    int quarantinedQuantity,
    long lotVersion,
    Long allocationVersion,
    Long movementId,
    String movementType
) {
}
