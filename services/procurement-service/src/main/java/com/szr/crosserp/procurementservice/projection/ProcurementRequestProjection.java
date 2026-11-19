package com.szr.crosserp.procurementservice.projection;
import jakarta.persistence.*;
@Entity @Table(name="procurement_request_projections")
public class ProcurementRequestProjection {
    @Id @Column(name="request_id") private Long id;
    @Column(name="order_item_id",nullable=false) private Long orderItemId;
    @Column(name="requested_quantity",nullable=false) private int requestedQuantity;
    @Column(nullable=false,length=32) private String status;
    @Column(name="source_version",nullable=false) private long sourceVersion;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="order_item_id",insertable=false,updatable=false)
    private OrderItemProjection orderItem;
    public OrderItemProjection getOrderItem(){return orderItem;}
    protected ProcurementRequestProjection() {}
    public Long getId(){return id;}
    public Long getOrderItemId(){return orderItemId;}
    public int getRequestedQuantity(){return requestedQuantity;}
    public String getStatus(){return status;}
    public long getSourceVersion(){return sourceVersion;}
}
