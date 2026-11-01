package com.szr.crosserp.order.model;

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
@Table(name = "sales_order_items")
public class SalesOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private SalesOrder order;

    @Column(name = "external_item_id", nullable = false, length = 128)
    private String externalItemId;

    @Column(name = "external_product_id", length = 128)
    private String externalProductId;

    @Column(name = "external_variant_id", length = 128)
    private String externalVariantId;

    @Column(name = "external_sku", length = 128)
    private String externalSku;

    @Column(name = "erp_sku_id")
    private Long erpSkuId;

    @Column(name = "product_name", nullable = false, length = 500)
    private String productName;

    @Column(name = "variant_name", length = 500)
    private String variantName;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_minor")
    private Long unitPriceMinor;

    @Column(length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status", nullable = false, length = 32)
    private OrderItemFulfillmentStatus fulfillmentStatus = OrderItemFulfillmentStatus.NORMAL;

    @Column(length = 500)
    private String note;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SalesOrderItem() {
    }

    public SalesOrderItem(SalesOrder order, String externalItemId, String productName, int quantity) {
        this.order = order;
        this.externalItemId = externalItemId;
        this.productName = productName;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public SalesOrder getOrder() { return order; }
    public String getExternalItemId() { return externalItemId; }
    public String getExternalProductId() { return externalProductId; }
    public String getExternalVariantId() { return externalVariantId; }
    public String getExternalSku() { return externalSku; }
    public Long getErpSkuId() { return erpSkuId; }
    public String getProductName() { return productName; }
    public String getVariantName() { return variantName; }
    public String getImageUrl() { return imageUrl; }
    public int getQuantity() { return quantity; }
    public Long getUnitPriceMinor() { return unitPriceMinor; }
    public String getCurrency() { return currency; }
    public OrderItemFulfillmentStatus getFulfillmentStatus() { return fulfillmentStatus; }
    public String getNote() { return note; }
    public long getVersion() { return version; }
}
