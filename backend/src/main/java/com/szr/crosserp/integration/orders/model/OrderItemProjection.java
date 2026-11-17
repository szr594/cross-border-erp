package com.szr.crosserp.integration.orders.model;

import com.szr.crosserp.order.model.OrderItemFulfillmentStatus;
import jakarta.persistence.*;

@Entity @Table(name="order_item_business_projections")
public class OrderItemProjection {
    @Id @Column(name="order_item_id") private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id") private OrderProjection order;
    @Column(name="product_name") private String productName;
    @Column(name="variant_name") private String variantName;
    @Column(name="external_sku") private String externalSku;
    @Column(name="erp_sku_id") private Long erpSkuId;
    private int quantity;
    @Enumerated(EnumType.STRING) @Column(name="fulfillment_status") private OrderItemFulfillmentStatus fulfillmentStatus;
    protected OrderItemProjection() {}
    public static OrderItemProjection fromLegacy(com.szr.crosserp.order.model.SalesOrderItem source){
        OrderItemProjection p=new OrderItemProjection();p.id=source.getId();p.order=OrderProjection.fromLegacy(source.getOrder());p.productName=source.getProductName();p.variantName=source.getVariantName();p.externalSku=source.getExternalSku();p.erpSkuId=source.getErpSkuId();p.quantity=source.getQuantity();p.fulfillmentStatus=source.getFulfillmentStatus();return p;
    }
    public Long getId(){return id;} public OrderProjection getOrder(){return order;} public String getProductName(){return productName;}
    public String getVariantName(){return variantName;} public String getExternalSku(){return externalSku;} public Long getErpSkuId(){return erpSkuId;}
    public int getQuantity(){return quantity;} public OrderItemFulfillmentStatus getFulfillmentStatus(){return fulfillmentStatus;}
}
