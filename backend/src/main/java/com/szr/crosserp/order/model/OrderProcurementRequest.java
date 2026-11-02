package com.szr.crosserp.order.model;

import com.szr.crosserp.user.model.User;
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
@Table(name = "order_procurement_requests")
public class OrderProcurementRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private SalesOrderItem orderItem;

    @Column(name = "requested_quantity", nullable = false)
    private int requestedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProcurementRequestStatus status = ProcurementRequestStatus.REQUESTED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "withdrawn_by")
    private User withdrawnBy;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(length = 500)
    private String note;

    @Version
    @Column(nullable = false)
    private long version;

    protected OrderProcurementRequest() {
    }

    public OrderProcurementRequest(SalesOrderItem orderItem, int requestedQuantity, User requestedBy, Instant requestedAt) {
        this.orderItem = orderItem;
        this.requestedQuantity = requestedQuantity;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
    }

    public OrderProcurementRequest(SalesOrderItem orderItem, int requestedQuantity, User requestedBy, Instant requestedAt, String note) {
        this(orderItem, requestedQuantity, requestedBy, requestedAt);
        this.note = note;
    }

    public Long getId() { return id; }
    public SalesOrderItem getOrderItem() { return orderItem; }
    public int getRequestedQuantity() { return requestedQuantity; }
    public ProcurementRequestStatus getStatus() { return status; }
    public User getRequestedBy() { return requestedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public User getWithdrawnBy() { return withdrawnBy; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
    public String getNote() { return note; }
    public long getVersion() { return version; }

    public void withdraw(User actor, Instant now, String reason) {
        status = ProcurementRequestStatus.WITHDRAWN;
        withdrawnBy = actor;
        withdrawnAt = now;
        note = reason;
    }
}
