package com.szr.crosserp.integration.orders.model;

import com.szr.crosserp.order.model.ProcurementRequestStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="procurement_request_projections")
public class ProcurementRequestProjection {
    @Id @Column(name="request_id") private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_item_id") private OrderItemProjection orderItem;
    @Column(name="requested_quantity") private int requestedQuantity;
    @Enumerated(EnumType.STRING) private ProcurementRequestStatus status;
    @Column(name="source_version") private long version;
    @Column(name="requested_at") private Instant requestedAt;
    protected ProcurementRequestProjection() {}
    public static ProcurementRequestProjection fromLegacy(com.szr.crosserp.order.model.OrderProcurementRequest source){
        ProcurementRequestProjection p=new ProcurementRequestProjection();p.id=source.getId();p.orderItem=OrderItemProjection.fromLegacy(source.getOrderItem());p.requestedQuantity=source.getRequestedQuantity();p.status=source.getStatus();p.version=source.getVersion();p.requestedAt=source.getRequestedAt();return p;
    }
    public Long getId(){return id;} public OrderItemProjection getOrderItem(){return orderItem;} public int getRequestedQuantity(){return requestedQuantity;}
    public ProcurementRequestStatus getStatus(){return status;} public long getVersion(){return version;} public Instant getRequestedAt(){return requestedAt;}
}
