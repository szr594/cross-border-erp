package com.szr.crosserp.procurement.model;

import com.szr.crosserp.user.model.User;
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
@Table(name = "purchase_order_events")
public class PurchaseOrderEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "from_value", length = 128)
    private String fromValue;

    @Column(name = "to_value", length = 128)
    private String toValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @Column(length = 500)
    private String remark;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected PurchaseOrderEvent() {
    }

    public PurchaseOrderEvent(PurchaseOrder purchaseOrder, String eventType, User operator, Instant occurredAt) {
        this.purchaseOrder = purchaseOrder;
        this.eventType = eventType;
        this.operator = operator;
        this.occurredAt = occurredAt;
    }

    public PurchaseOrderEvent(
        PurchaseOrder purchaseOrder, String eventType, User operator, String remark, Instant occurredAt
    ) {
        this(purchaseOrder, eventType, operator, occurredAt);
        this.remark = remark;
    }

    public Long getId() { return id; }
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public String getEventType() { return eventType; }
    public String getFromValue() { return fromValue; }
    public String getToValue() { return toValue; }
    public User getOperator() { return operator; }
    public String getRemark() { return remark; }
    public Instant getOccurredAt() { return occurredAt; }
}
