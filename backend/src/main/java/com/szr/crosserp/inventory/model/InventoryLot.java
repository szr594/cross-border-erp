package com.szr.crosserp.inventory.model;

import com.szr.crosserp.procurement.model.PurchaseOrderItem;
import com.szr.crosserp.receiving.model.GoodsReceiptItem;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "inventory_lots")
public class InventoryLot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lot_no", nullable = false, unique = true, length = 64)
    private String lotNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_item_id", nullable = false)
    private GoodsReceiptItem goodsReceiptItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_item_id", nullable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", nullable = false, length = 32)
    private InventoryQualityStatus qualityStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InventoryLotStatus status;

    @Column(name = "initial_quantity", nullable = false)
    private int initialQuantity;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "quarantined_quantity", nullable = false)
    private int quarantinedQuantity;

    @Column(name = "consumed_quantity", nullable = false)
    private int consumedQuantity;

    @Column(name = "adjusted_out_quantity", nullable = false)
    private int adjustedOutQuantity;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryLot() {
    }

    public InventoryLot(
        String lotNo, GoodsReceiptItem goodsReceiptItem, PurchaseOrderItem purchaseOrderItem,
        InventoryQualityStatus qualityStatus, int initialQuantity, Instant receivedAt
    ) {
        if (initialQuantity <= 0) {
            throw new IllegalArgumentException("initialQuantity must be positive");
        }
        this.lotNo = lotNo;
        this.goodsReceiptItem = goodsReceiptItem;
        this.purchaseOrderItem = purchaseOrderItem;
        this.qualityStatus = qualityStatus;
        this.initialQuantity = initialQuantity;
        this.receivedAt = receivedAt;
        if (qualityStatus == InventoryQualityStatus.ACCEPTED) {
            status = InventoryLotStatus.AVAILABLE;
            availableQuantity = initialQuantity;
        } else {
            status = InventoryLotStatus.QUARANTINED;
            quarantinedQuantity = initialQuantity;
        }
    }

    public Long getId() { return id; }
    public String getLotNo() { return lotNo; }
    public GoodsReceiptItem getGoodsReceiptItem() { return goodsReceiptItem; }
    public PurchaseOrderItem getPurchaseOrderItem() { return purchaseOrderItem; }
    public InventoryQualityStatus getQualityStatus() { return qualityStatus; }
    public InventoryLotStatus getStatus() { return status; }
    public int getInitialQuantity() { return initialQuantity; }
    public int getAvailableQuantity() { return availableQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public int getQuarantinedQuantity() { return quarantinedQuantity; }
    public int getConsumedQuantity() { return consumedQuantity; }
    public int getAdjustedOutQuantity() { return adjustedOutQuantity; }
    public Instant getReceivedAt() { return receivedAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void reserve(int quantity) {
        if (quantity <= 0 || quantity > availableQuantity) {
            throw new IllegalArgumentException("Reservation quantity exceeds available inventory");
        }
        availableQuantity -= quantity;
        reservedQuantity += quantity;
        refreshStatus();
    }

    public void releaseReservation(int quantity) {
        if (quantity <= 0 || quantity > reservedQuantity) {
            throw new IllegalArgumentException("Release quantity exceeds reserved inventory");
        }
        reservedQuantity -= quantity;
        availableQuantity += quantity;
        refreshStatus();
    }

    public void consumeReservation(int quantity) {
        if (quantity <= 0 || quantity > reservedQuantity) {
            throw new IllegalArgumentException("Consume quantity exceeds reserved inventory");
        }
        reservedQuantity -= quantity;
        consumedQuantity += quantity;
        refreshStatus();
    }

    public void adjustIn(int quantity) {
        requirePositive(quantity);
        initialQuantity += quantity;
        availableQuantity += quantity;
        refreshStatus();
    }

    public void adjustOut(int quantity) {
        if (quantity <= 0 || quantity > availableQuantity) {
            throw new IllegalArgumentException("Adjustment quantity exceeds available inventory");
        }
        availableQuantity -= quantity;
        adjustedOutQuantity += quantity;
        refreshStatus();
    }

    public void releaseQuarantine(int quantity) {
        if (quantity <= 0 || quantity > quarantinedQuantity) {
            throw new IllegalArgumentException("Release quantity exceeds quarantined inventory");
        }
        quarantinedQuantity -= quantity;
        availableQuantity += quantity;
        refreshStatus();
    }

    private void refreshStatus() {
        if (availableQuantity > 0) {
            status = InventoryLotStatus.AVAILABLE;
        } else if (reservedQuantity > 0) {
            status = InventoryLotStatus.ALLOCATED;
        } else if (quarantinedQuantity > 0) {
            status = InventoryLotStatus.QUARANTINED;
        } else {
            status = InventoryLotStatus.DEPLETED;
        }
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
