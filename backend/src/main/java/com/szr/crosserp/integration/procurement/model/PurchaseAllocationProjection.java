package com.szr.crosserp.integration.procurement.model;

import com.szr.crosserp.integration.orders.model.ProcurementRequestProjection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "purchase_allocation_projections")
public class PurchaseAllocationProjection {
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_item_id", nullable = false)
    private PurchaseItemProjection purchaseOrderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procurement_request_id", nullable = false)
    private ProcurementRequestProjection procurementRequest;

    @Column(name = "allocated_quantity", nullable = false)
    private int allocatedQuantity;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected PurchaseAllocationProjection() {
    }

    public PurchaseAllocationProjection(
        PurchaseItemProjection purchaseOrderItem,
        ProcurementRequestProjection procurementRequest,
        int allocatedQuantity
    ) {
        this.purchaseOrderItem = purchaseOrderItem;
        this.procurementRequest = procurementRequest;
        this.allocatedQuantity = allocatedQuantity;
    }

    public static PurchaseAllocationProjection fromLegacy(com.szr.crosserp.procurement.model.PurchaseOrderAllocation s){if(s==null)return null;var p=new PurchaseAllocationProjection();p.id=s.getId();p.purchaseOrderItem=PurchaseItemProjection.fromLegacy(s.getPurchaseOrderItem());p.procurementRequest=s.getProcurementRequest();p.allocatedQuantity=s.getAllocatedQuantity();return p;}
    public Long getId() { return id; }
    public PurchaseItemProjection getPurchaseItemProjection() { return purchaseOrderItem; }
    public PurchaseAllocationProjection(PurchaseItemProjection item,com.szr.crosserp.order.model.OrderProcurementRequest request,int quantity) {
        this(item,ProcurementRequestProjection.fromLegacy(request),quantity);
    }
    public ProcurementRequestProjection getProcurementRequest() { return procurementRequest; }
    public int getAllocatedQuantity() { return allocatedQuantity; }
    public Instant getCreatedAt() { return createdAt; }
}
