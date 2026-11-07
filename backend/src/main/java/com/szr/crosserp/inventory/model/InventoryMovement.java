package com.szr.crosserp.inventory.model;

import com.szr.crosserp.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "movement_no", nullable = false, unique = true, length = 64)
    private String movementNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_lot_id", nullable = false)
    private InventoryLot inventoryLot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id")
    private InventoryAllocation allocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 32)
    private InventoryMovementType movementType;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "available_after", nullable = false)
    private int availableAfter;

    @Column(name = "reserved_after", nullable = false)
    private int reservedAfter;

    @Column(name = "quarantined_after", nullable = false)
    private int quarantinedAfter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @Column(length = 500)
    private String reason;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected InventoryMovement() {
    }

    public InventoryMovement(
        String movementNo, InventoryLot inventoryLot, InventoryAllocation allocation,
        InventoryMovementType movementType, int quantity, int availableAfter,
        int reservedAfter, int quarantinedAfter, User operator, String reason,
        String idempotencyKey, Instant occurredAt
    ) {
        if (quantity <= 0 || availableAfter < 0 || reservedAfter < 0 || quarantinedAfter < 0) {
            throw new IllegalArgumentException("movement quantities are invalid");
        }
        this.movementNo = movementNo;
        this.inventoryLot = inventoryLot;
        this.allocation = allocation;
        this.movementType = movementType;
        this.quantity = quantity;
        this.availableAfter = availableAfter;
        this.reservedAfter = reservedAfter;
        this.quarantinedAfter = quarantinedAfter;
        this.operator = operator;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public String getMovementNo() { return movementNo; }
    public InventoryLot getInventoryLot() { return inventoryLot; }
    public InventoryAllocation getAllocation() { return allocation; }
    public InventoryMovementType getMovementType() { return movementType; }
    public int getQuantity() { return quantity; }
    public int getAvailableAfter() { return availableAfter; }
    public int getReservedAfter() { return reservedAfter; }
    public int getQuarantinedAfter() { return quarantinedAfter; }
    public User getOperator() { return operator; }
    public String getReason() { return reason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getOccurredAt() { return occurredAt; }
}
