package com.szr.crosserp.orderimport.model;

import com.szr.crosserp.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "order_import_batches")
public class OrderImportBatch {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "file_sha256", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String fileSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderImportStatus status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;
    @Column(name = "order_count", nullable = false)
    private int orderCount;
    @Column(name = "item_count", nullable = false)
    private int itemCount;
    @Column(name = "creatable_order_count", nullable = false)
    private int creatableOrderCount;
    @Column(name = "skipped_order_count", nullable = false)
    private int skippedOrderCount;
    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "failure_code", length = 64)
    private String failureCode;

    protected OrderImportBatch() {}

    public OrderImportBatch(String id, String fileName, String sha256, User createdBy, Instant now, Instant expiresAt) {
        this.id = id;
        this.originalFileName = fileName;
        this.fileSha256 = sha256;
        this.status = OrderImportStatus.VALIDATING;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public String getOriginalFileName() { return originalFileName; }
    public String getFileSha256() { return fileSha256; }
    public OrderImportStatus getStatus() { return status; }
    public int getTotalRows() { return totalRows; }
    public int getOrderCount() { return orderCount; }
    public int getItemCount() { return itemCount; }
    public int getCreatableOrderCount() { return creatableOrderCount; }
    public int getSkippedOrderCount() { return skippedOrderCount; }
    public int getErrorCount() { return errorCount; }
    public User getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getFailureCode() { return failureCode; }

    public void finishValidation(int rows, int orders, int items, int creatable, int skipped, int errors) {
        totalRows = rows;
        orderCount = orders;
        itemCount = items;
        creatableOrderCount = creatable;
        skippedOrderCount = skipped;
        errorCount = errors;
        status = errors == 0 ? OrderImportStatus.VALIDATED : OrderImportStatus.INVALID;
    }

    public void startImport(Instant now) {
        status = OrderImportStatus.IMPORTING;
        confirmedAt = now;
    }

    public void complete(Instant now) {
        status = OrderImportStatus.COMPLETED;
        completedAt = now;
    }

    public void expire() { status = OrderImportStatus.EXPIRED; }
}
