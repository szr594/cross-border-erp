package com.szr.crosserp.packing.model;

import com.szr.crosserp.order.model.SalesOrder;
import com.szr.crosserp.user.model.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "packing_tasks")
public class PackingTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="task_no", nullable=false, unique=true, length=64) private String taskNo;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="sales_order_id", nullable=false) private SalesOrder order;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private PackingTaskStatus status = PackingTaskStatus.DRAFT;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="started_by", nullable=false) private User startedBy;
    @Column(name="started_at", nullable=false) private Instant startedAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="completed_by") private User completedBy;
    @Column(name="completed_at") private Instant completedAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="canceled_by") private User canceledBy;
    @Column(name="canceled_at") private Instant canceledAt;
    @Column(name="cancel_reason", length=500) private String cancelReason;
    @Column(name="completion_key", unique=true, length=64) private String completionKey;
    @Version @Column(nullable=false) private long version;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    protected PackingTask() {}
    public PackingTask(String taskNo, SalesOrder order, User actor, Instant now) { this.taskNo=taskNo; this.order=order; this.startedBy=actor; this.startedAt=now; this.createdAt=now; this.updatedAt=now; }
    public void complete(User actor, String key, Instant now) { requireDraft(); status=PackingTaskStatus.COMPLETED; completedBy=actor; completedAt=now; completionKey=key; updatedAt=now; }
    public void cancel(User actor, String reason, Instant now) { requireDraft(); status=PackingTaskStatus.CANCELED; canceledBy=actor; canceledAt=now; cancelReason=reason; updatedAt=now; }
    public void touch(Instant now) { requireDraft(); updatedAt=now; }
    private void requireDraft() { if(status != PackingTaskStatus.DRAFT) throw new IllegalStateException("Packing task is immutable"); }
    public Long getId(){return id;} public String getTaskNo(){return taskNo;} public SalesOrder getOrder(){return order;} public PackingTaskStatus getStatus(){return status;} public User getStartedBy(){return startedBy;} public Instant getStartedAt(){return startedAt;} public User getCompletedBy(){return completedBy;} public Instant getCompletedAt(){return completedAt;} public User getCanceledBy(){return canceledBy;} public Instant getCanceledAt(){return canceledAt;} public String getCancelReason(){return cancelReason;} public String getCompletionKey(){return completionKey;} public long getVersion(){return version;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
