package com.szr.crosserp.receiving.model;

import com.szr.crosserp.procurement.model.PurchaseOrderItem;
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
@Table(name = "goods_receipt_items")
public class GoodsReceiptItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt goodsReceipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_item_id", nullable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @Column(name = "accepted_quantity", nullable = false)
    private int acceptedQuantity;

    @Column(name = "damaged_quantity", nullable = false)
    private int damagedQuantity;

    @Column(length = 500)
    private String note;

    protected GoodsReceiptItem() {
    }

    public GoodsReceiptItem(
        GoodsReceipt goodsReceipt, PurchaseOrderItem purchaseOrderItem,
        int acceptedQuantity, int damagedQuantity, String note
    ) {
        this.goodsReceipt = goodsReceipt;
        this.purchaseOrderItem = purchaseOrderItem;
        this.acceptedQuantity = acceptedQuantity;
        this.damagedQuantity = damagedQuantity;
        this.note = note;
    }

    public Long getId() { return id; }
    public GoodsReceipt getGoodsReceipt() { return goodsReceipt; }
    public PurchaseOrderItem getPurchaseOrderItem() { return purchaseOrderItem; }
    public int getAcceptedQuantity() { return acceptedQuantity; }
    public int getDamagedQuantity() { return damagedQuantity; }
    public String getNote() { return note; }
}
