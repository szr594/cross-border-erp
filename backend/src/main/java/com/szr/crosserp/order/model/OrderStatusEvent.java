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
import java.time.Instant;

@Entity
@Table(name = "order_status_events")
public class OrderStatusEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private SalesOrder order;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "from_value", length = 128)
    private String fromValue;

    @Column(name = "to_value", length = 128)
    private String toValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderEventSource source;

    @Column(length = 500)
    private String remark;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected OrderStatusEvent() {
    }

    public OrderStatusEvent(SalesOrder order, String eventType, User operator, OrderEventSource source, Instant occurredAt) {
        this.order = order;
        this.eventType = eventType;
        this.operator = operator;
        this.source = source;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public SalesOrder getOrder() { return order; }
    public String getEventType() { return eventType; }
    public String getFromValue() { return fromValue; }
    public String getToValue() { return toValue; }
    public User getOperator() { return operator; }
    public OrderEventSource getSource() { return source; }
    public String getRemark() { return remark; }
    public Instant getOccurredAt() { return occurredAt; }
}
