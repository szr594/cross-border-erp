package com.szr.crosserp.receiving.model;

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
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "goods_receipts")
public class GoodsReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_no", nullable = false, unique = true, length = 64)
    private String receiptNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "domestic_package_id", nullable = false)
    private DomesticPackage domesticPackage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GoodsReceiptStatus status = GoodsReceiptStatus.CONFIRMED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Column(length = 500)
    private String note;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GoodsReceipt() {
    }

    public GoodsReceipt(
        String receiptNo, DomesticPackage domesticPackage, User operator,
        Instant confirmedAt, String idempotencyKey, String requestHash, String note
    ) {
        this.receiptNo = receiptNo;
        this.domesticPackage = domesticPackage;
        this.operator = operator;
        this.confirmedAt = confirmedAt;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.note = note;
    }

    public Long getId() { return id; }
    public String getReceiptNo() { return receiptNo; }
    public DomesticPackage getDomesticPackage() { return domesticPackage; }
    public GoodsReceiptStatus getStatus() { return status; }
    public User getOperator() { return operator; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public String getNote() { return note; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public Instant getCreatedAt() { return createdAt; }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
