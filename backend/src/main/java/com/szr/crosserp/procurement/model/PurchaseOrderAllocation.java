package com.szr.crosserp.procurement.model;

import com.szr.crosserp.order.model.OrderProcurementRequest;
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
@Table(name = "purchase_order_allocations")
public class PurchaseOrderAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_item_id", nullable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procurement_request_id", nullable = false)
    private OrderProcurementRequest procurementRequest;

    @Column(name = "allocated_quantity", nullable = false)
    private int allocatedQuantity;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected PurchaseOrderAllocation() {
    }

    public PurchaseOrderAllocation(
        PurchaseOrderItem purchaseOrderItem,
        OrderProcurementRequest procurementRequest,
        int allocatedQuantity
    ) {
        this.purchaseOrderItem = purchaseOrderItem;
        this.procurementRequest = procurementRequest;
        this.allocatedQuantity = allocatedQuantity;
    }

    public Long getId() { return id; }
    public PurchaseOrderItem getPurchaseOrderItem() { return purchaseOrderItem; }
    public OrderProcurementRequest getProcurementRequest() { return procurementRequest; }
    public int getAllocatedQuantity() { return allocatedQuantity; }
    public Instant getCreatedAt() { return createdAt; }
}
