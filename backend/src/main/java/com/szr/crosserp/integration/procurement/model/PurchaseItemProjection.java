package com.szr.crosserp.integration.procurement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "purchase_item_projections")
public class PurchaseItemProjection {
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrderProjection purchaseOrder;

    @Column(name = "supplier_product_id", length = 128)
    private String supplierProductId;

    @Column(name = "supplier_sku", length = 128)
    private String supplierSku;

    @Column(name = "product_name", nullable = false, length = 500)
    private String productName;

    @Column(name = "variant_name", length = 500)
    private String variantName;

    @Column(name = "product_url", length = 1000)
    private String productUrl;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "purchased_quantity", nullable = false)
    private int purchasedQuantity;

    @Column(name = "received_quantity", nullable = false)
    private int receivedQuantity;

    @Column(name = "unit_price_minor")
    private Long unitPriceMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 500)
    private String note;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PurchaseItemProjection() {
    }

    public PurchaseItemProjection(PurchaseOrderProjection purchaseOrder, String productName, int purchasedQuantity, String currency) {
        this.purchaseOrder = purchaseOrder;
        this.productName = productName;
        this.purchasedQuantity = purchasedQuantity;
        this.currency = currency;
    }

    public PurchaseItemProjection(
        PurchaseOrderProjection purchaseOrder, String supplierProductId, String supplierSku,
        String productName, String variantName, String productUrl, String imageUrl,
        int purchasedQuantity, Long unitPriceMinor, String currency
    ) {
        this(purchaseOrder, productName, purchasedQuantity, currency);
        this.supplierProductId = supplierProductId;
        this.supplierSku = supplierSku;
        this.variantName = variantName;
        this.productUrl = productUrl;
        this.imageUrl = imageUrl;
        this.unitPriceMinor = unitPriceMinor;
    }

    public static PurchaseItemProjection fromLegacy(com.szr.crosserp.procurement.model.PurchaseOrderItem source) {
        var p=new PurchaseItemProjection();p.id=source.getId();p.purchaseOrder=PurchaseOrderProjection.fromLegacy(source.getPurchaseOrder());p.productName=source.getProductName();p.variantName=source.getVariantName();p.supplierSku=source.getSupplierSku();p.purchasedQuantity=source.getPurchasedQuantity();p.receivedQuantity=source.getReceivedQuantity();p.currency=source.getCurrency();return p;
    }
    public Long getId() { return id; }
    public PurchaseOrderProjection getPurchaseOrder() { return purchaseOrder; }
    public String getSupplierProductId() { return supplierProductId; }
    public String getSupplierSku() { return supplierSku; }
    public String getProductName() { return productName; }
    public String getVariantName() { return variantName; }
    public String getProductUrl() { return productUrl; }
    public String getImageUrl() { return imageUrl; }
    public int getPurchasedQuantity() { return purchasedQuantity; }
    public int getReceivedQuantity() { return receivedQuantity; }
    public Long getUnitPriceMinor() { return unitPriceMinor; }
    public String getCurrency() { return currency; }
    public String getNote() { return note; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void receiveAccepted(int quantity) {
        if (quantity < 0 || receivedQuantity + quantity > purchasedQuantity) {
            throw new IllegalArgumentException("accepted quantity exceeds purchased quantity");
        }
        receivedQuantity += quantity;
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
