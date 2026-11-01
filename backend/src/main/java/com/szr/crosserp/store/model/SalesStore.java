package com.szr.crosserp.store.model;

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
@Table(name = "sales_stores")
public class SalesStore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private SalesChannel channel;

    @Column(name = "external_store_id", nullable = false, length = 128)
    private String externalStoreId;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_owner_id")
    private User currentOwner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StoreStatus status = StoreStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SalesStore() {
    }

    public SalesStore(SalesChannel channel, String externalStoreId, String code, String name) {
        this.channel = channel;
        this.externalStoreId = externalStoreId;
        this.code = code;
        this.name = name;
    }

    public Long getId() { return id; }
    public SalesChannel getChannel() { return channel; }
    public String getExternalStoreId() { return externalStoreId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public User getCurrentOwner() { return currentOwner; }
    public StoreStatus getStatus() { return status; }
}
