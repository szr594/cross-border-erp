package com.szr.crosserp.receiving.model;

import com.szr.crosserp.procurement.model.PurchaseOrder;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "domestic_packages")
public class DomesticPackage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_no", nullable = false, unique = true, length = 64)
    private String packageNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "tracking_number", nullable = false, length = 128)
    private String trackingNumber;

    @Column(nullable = false, length = 100)
    private String carrier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DomesticPackageStatus status = DomesticPackageStatus.EXPECTED;

    @Column(name = "expected_at")
    private Instant expectedAt;

    @Column(name = "first_received_at")
    private Instant firstReceivedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(length = 500)
    private String note;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DomesticPackage() {
    }

    public DomesticPackage(
        String packageNo, PurchaseOrder purchaseOrder, String trackingNumber, String carrier,
        Instant expectedAt, String note, User createdBy
    ) {
        this.packageNo = packageNo;
        this.purchaseOrder = purchaseOrder;
        this.trackingNumber = trackingNumber;
        this.carrier = carrier;
        this.expectedAt = expectedAt;
        this.note = note;
        this.createdBy = createdBy;
    }

    public DomesticPackage(
        String packageNo, PurchaseOrder purchaseOrder, String trackingNumber, String carrier, User createdBy
    ) {
        this(packageNo, purchaseOrder, trackingNumber, carrier, null, null, createdBy);
    }

    public Long getId() { return id; }
    public String getPackageNo() { return packageNo; }
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getCarrier() { return carrier; }
    public DomesticPackageStatus getStatus() { return status; }
    public Instant getExpectedAt() { return expectedAt; }
    public Instant getFirstReceivedAt() { return firstReceivedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public User getCreatedBy() { return createdBy; }
    public String getNote() { return note; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void recordReceipt(Instant receivedAt, boolean completed, boolean hasException) {
        if (firstReceivedAt == null) {
            firstReceivedAt = receivedAt;
        }
        if (completed) {
            completedAt = receivedAt;
        }
        status = hasException
            ? DomesticPackageStatus.EXCEPTION
            : completed ? DomesticPackageStatus.RECEIVED : DomesticPackageStatus.RECEIVING;
    }

    public void cancel() {
        status = DomesticPackageStatus.CANCELED;
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
