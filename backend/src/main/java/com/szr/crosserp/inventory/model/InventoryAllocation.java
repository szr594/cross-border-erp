package com.szr.crosserp.inventory.model;

import com.szr.crosserp.order.model.SalesOrderItem;
import com.szr.crosserp.procurement.model.PurchaseOrderAllocation;
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
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "inventory_allocations")
public class InventoryAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "allocation_no", nullable = false, unique = true, length = 64)
    private String allocationNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_lot_id", nullable = false)
    private InventoryLot inventoryLot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_item_id", nullable = false)
    private SalesOrderItem salesOrderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_allocation_id")
    private PurchaseOrderAllocation purchaseOrderAllocation;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InventoryAllocationStatus status = InventoryAllocationStatus.RESERVED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocated_by")
    private User allocatedBy;

    @Column(name = "allocated_at", nullable = false)
    private Instant allocatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "released_by")
    private User releasedBy;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "release_reason", length = 500)
    private String releaseReason;

    @Version
    @Column(nullable = false)
    private long version;

    protected InventoryAllocation() {
    }

    public InventoryAllocation(
        String allocationNo, InventoryLot inventoryLot, SalesOrderItem salesOrderItem,
        PurchaseOrderAllocation purchaseOrderAllocation, int quantity, User allocatedBy, Instant allocatedAt
    ) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.allocationNo = allocationNo;
        this.inventoryLot = inventoryLot;
        this.salesOrderItem = salesOrderItem;
        this.purchaseOrderAllocation = purchaseOrderAllocation;
        this.quantity = quantity;
        this.allocatedBy = allocatedBy;
        this.allocatedAt = allocatedAt;
    }

    public Long getId() { return id; }
    public String getAllocationNo() { return allocationNo; }
    public InventoryLot getInventoryLot() { return inventoryLot; }
    public SalesOrderItem getSalesOrderItem() { return salesOrderItem; }
    public PurchaseOrderAllocation getPurchaseOrderAllocation() { return purchaseOrderAllocation; }
    public int getQuantity() { return quantity; }
    public InventoryAllocationStatus getStatus() { return status; }
    public User getAllocatedBy() { return allocatedBy; }
    public Instant getAllocatedAt() { return allocatedAt; }
    public User getReleasedBy() { return releasedBy; }
    public Instant getReleasedAt() { return releasedAt; }
    public String getReleaseReason() { return releaseReason; }
    public long getVersion() { return version; }
}
