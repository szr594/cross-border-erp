package com.szr.crosserp.receiving.model;

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
@Table(name = "receiving_events")
public class ReceivingEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "domestic_package_id", nullable = false)
    private DomesticPackage domesticPackage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_receipt_id")
    private GoodsReceipt goodsReceipt;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    @Column(length = 500)
    private String remark;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ReceivingEvent() {
    }

    public ReceivingEvent(
        DomesticPackage domesticPackage, GoodsReceipt goodsReceipt, String eventType,
        User operator, String remark, Instant occurredAt
    ) {
        this.domesticPackage = domesticPackage;
        this.goodsReceipt = goodsReceipt;
        this.eventType = eventType;
        this.operator = operator;
        this.remark = remark;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public DomesticPackage getDomesticPackage() { return domesticPackage; }
    public GoodsReceipt getGoodsReceipt() { return goodsReceipt; }
    public String getEventType() { return eventType; }
    public User getOperator() { return operator; }
    public String getRemark() { return remark; }
    public Instant getOccurredAt() { return occurredAt; }
}
