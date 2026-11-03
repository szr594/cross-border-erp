package com.szr.crosserp.procurement.model;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_order_no", nullable = false, unique = true, length = 64)
    private String purchaseOrderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(name = "external_order_no", length = 128)
    private String externalOrderNo;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_amount_minor", nullable = false)
    private long totalAmountMinor;

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @Column(name = "expected_arrival_at")
    private Instant expectedArrivalAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordered_by")
    private User orderedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canceled_by")
    private User canceledBy;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(length = 1000)
    private String note;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PurchaseOrder() {
    }

    public PurchaseOrder(String purchaseOrderNo, Supplier supplier, String currency, User createdBy) {
        this.purchaseOrderNo = purchaseOrderNo;
        this.supplier = supplier;
        this.currency = currency;
        this.createdBy = createdBy;
    }

    public PurchaseOrder(
        String purchaseOrderNo, Supplier supplier, String currency,
        long totalAmountMinor, User createdBy, String note
    ) {
        this(purchaseOrderNo, supplier, currency, createdBy);
        this.totalAmountMinor = totalAmountMinor;
        this.note = note;
    }

    public Long getId() { return id; }
    public String getPurchaseOrderNo() { return purchaseOrderNo; }
    public Supplier getSupplier() { return supplier; }
    public PurchaseOrderStatus getStatus() { return status; }
    public String getExternalOrderNo() { return externalOrderNo; }
    public String getCurrency() { return currency; }
    public long getTotalAmountMinor() { return totalAmountMinor; }
    public Instant getOrderedAt() { return orderedAt; }
    public Instant getExpectedArrivalAt() { return expectedArrivalAt; }
    public User getCreatedBy() { return createdBy; }
    public User getOrderedBy() { return orderedBy; }
    public User getCanceledBy() { return canceledBy; }
    public Instant getCanceledAt() { return canceledAt; }
    public String getCancelReason() { return cancelReason; }
    public String getNote() { return note; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateReceivingStatus(boolean anyInspected, boolean allInspected) {
        if (status == PurchaseOrderStatus.CANCELED || status == PurchaseOrderStatus.COMPLETED) {
            throw new IllegalStateException("purchase order cannot receive goods in current status");
        }
        if (allInspected) {
            status = PurchaseOrderStatus.RECEIVED;
        } else if (anyInspected) {
            status = PurchaseOrderStatus.PARTIALLY_RECEIVED;
        } else {
            status = PurchaseOrderStatus.ORDERED;
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
