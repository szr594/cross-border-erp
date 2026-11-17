package com.szr.crosserp.integration.orders.model;

import com.szr.crosserp.order.model.ExternalOrderStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="order_business_projections")
public class OrderProjection {
    @Id @Column(name="order_id") private Long id;
    @Column(name="order_no") private String orderNo;
    @Enumerated(EnumType.STRING) @Column(name="external_status") private ExternalOrderStatus externalStatus;
    @Column(name="fulfillment_exception") private boolean fulfillmentException;
    @Column(name="requested_ship_at") private Instant requestedShipAt;
    @Column(name="store_name") private String storeName;
    @Column(name="source_version") private long version;
    private String authority;
    protected OrderProjection() {}
    public static OrderProjection fromLegacy(com.szr.crosserp.order.model.SalesOrder source){
        OrderProjection p=new OrderProjection();p.id=source.getId();p.orderNo=source.getOrderNo();p.externalStatus=source.getExternalStatus();p.fulfillmentException=source.isFulfillmentException();p.requestedShipAt=source.getRequestedShipAt();p.storeName=source.getStore().getName();p.version=source.getVersion();p.authority="LEGACY";return p;
    }
    public Long getId(){return id;} public String getOrderNo(){return orderNo;} public ExternalOrderStatus getExternalStatus(){return externalStatus;}
    public boolean isFulfillmentException(){return fulfillmentException;} public Instant getRequestedShipAt(){return requestedShipAt;}
    public String getStoreName(){return storeName;} public long getVersion(){return version;} public String getAuthority(){return authority;}
}
