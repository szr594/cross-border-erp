package com.szr.crosserp.receiving.model;

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
@Table(name = "unexpected_receipt_items")
public class UnexpectedReceiptItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt goodsReceipt;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "supplier_sku", length = 128)
    private String supplierSku;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 500)
    private String note;

    protected UnexpectedReceiptItem() {
    }

    public UnexpectedReceiptItem(
        GoodsReceipt goodsReceipt, String description, String supplierSku, int quantity, String note
    ) {
        this.goodsReceipt = goodsReceipt;
        this.description = description;
        this.supplierSku = supplierSku;
        this.quantity = quantity;
        this.note = note;
    }

    public Long getId() { return id; }
    public GoodsReceipt getGoodsReceipt() { return goodsReceipt; }
    public String getDescription() { return description; }
    public String getSupplierSku() { return supplierSku; }
    public int getQuantity() { return quantity; }
    public String getNote() { return note; }
}
