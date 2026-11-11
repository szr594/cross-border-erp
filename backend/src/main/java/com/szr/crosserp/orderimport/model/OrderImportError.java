package com.szr.crosserp.orderimport.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_import_errors")
public class OrderImportError {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private OrderImportBatch batch;
    @Column(name = "csv_row_number", nullable = false)
    private int rowNumber;
    @Column(name = "field_name", length = 64)
    private String fieldName;
    @Column(name = "error_code", nullable = false, length = 64)
    private String errorCode;
    @Column(name = "error_message", nullable = false, length = 500)
    private String errorMessage;

    protected OrderImportError() {}

    public OrderImportError(OrderImportBatch batch, int rowNumber, String field, String code, String message) {
        this.batch = batch;
        this.rowNumber = rowNumber;
        this.fieldName = field;
        this.errorCode = code;
        this.errorMessage = message;
    }

    public Long getId() { return id; }
    public int getRowNumber() { return rowNumber; }
    public String getFieldName() { return fieldName; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}
