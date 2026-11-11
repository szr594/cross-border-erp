package com.szr.crosserp.packing.model;

import com.szr.crosserp.user.model.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="packing_events")
public class PackingEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="packing_task_id",nullable=false) private PackingTask task;
    @Enumerated(EnumType.STRING) @Column(name="event_type",nullable=false,length=32) private PackingEventType eventType;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="operator_id") private User operator;
    @Column(length=500) private String remark;
    @Column(name="occurred_at",nullable=false) private Instant occurredAt;
    protected PackingEvent() {}
    public PackingEvent(PackingTask task,PackingEventType type,User operator,String remark,Instant now){this.task=task;this.eventType=type;this.operator=operator;this.remark=remark;this.occurredAt=now;}
    public Long getId(){return id;} public PackingTask getTask(){return task;} public PackingEventType getEventType(){return eventType;} public User getOperator(){return operator;} public String getRemark(){return remark;} public Instant getOccurredAt(){return occurredAt;}
}
