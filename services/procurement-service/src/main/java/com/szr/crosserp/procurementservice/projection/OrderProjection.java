package com.szr.crosserp.procurementservice.projection;
import jakarta.persistence.*;
@Entity @Table(name="procurement_order_projections")
public class OrderProjection {
 @Id @Column(name="order_id") private Long id;
 @Column(name="order_no",length=32) private String orderNo;
 public String getOrderNo(){return orderNo;}
 protected OrderProjection() {}
 public Long getId(){return id;}
}
