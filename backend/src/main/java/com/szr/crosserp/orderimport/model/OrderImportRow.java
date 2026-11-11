package com.szr.crosserp.orderimport.model;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_import_rows")
public class OrderImportRow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private OrderImportBatch batch;
    @Column(name = "csv_row_number", nullable = false)
    private int rowNumber;
    @Column(name = "order_key", nullable = false, length = 300)
    private String orderKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_payload", nullable = false, columnDefinition = "JSON")
    private String normalizedPayload;
    @Column(name = "row_sha256", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String rowSha256;
    @Enumerated(EnumType.STRING)
    @Column(name = "row_status", nullable = false, length = 20)
    private OrderImportRowStatus rowStatus;

    protected OrderImportRow() {}

    public OrderImportRow(OrderImportBatch batch, int rowNumber, String orderKey, String payload, String sha256,
                          OrderImportRowStatus status) {
        this.batch = batch;
        this.rowNumber = rowNumber;
        this.orderKey = orderKey;
        this.normalizedPayload = payload;
        this.rowSha256 = sha256;
        this.rowStatus = status;
    }

    public Long getId() { return id; }
    public int getRowNumber() { return rowNumber; }
    public String getOrderKey() { return orderKey; }
    public String getNormalizedPayload() { return normalizedPayload; }
    public OrderImportRowStatus getRowStatus() { return rowStatus; }
}
