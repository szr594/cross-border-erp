package com.szr.crosserp.procurementservice.projection;
import jakarta.persistence.*;
@Entity @Table(name="procurement_item_projections")
public class OrderItemProjection {
 @Id @Column(name="order_item_id") private Long id;
 @Column(name="product_name",length=500) private String productName;
 public String getProductName(){return productName;}
 @Column(name="variant_name",length=500) private String variantName;
 public String getVariantName(){return variantName;}
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id") private OrderProjection order;
 public OrderProjection getOrder(){return order;}
 protected OrderItemProjection() {}
 public Long getId(){return id;}
}
