package com.szr.crosserp.procurement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;

@Entity
@Table(name = "suppliers")
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SupplierPlatform platform;

    @Column(name = "external_supplier_id", length = 128)
    private String externalSupplierId;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_phone", length = 64)
    private String contactPhone;

    @Column(name = "shop_url", length = 1000)
    private String shopUrl;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SupplierStatus status = SupplierStatus.ACTIVE;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Supplier() {
    }

    public Supplier(String code, String name, SupplierPlatform platform) {
        this.code = code;
        this.name = name;
        this.platform = platform;
    }

    public Supplier(
        String code, String name, SupplierPlatform platform, String externalSupplierId,
        String contactName, String contactPhone, String shopUrl, String note
    ) {
        this(code, name, platform);
        this.externalSupplierId = externalSupplierId;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.shopUrl = shopUrl;
        this.note = note;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public SupplierPlatform getPlatform() { return platform; }
    public String getExternalSupplierId() { return externalSupplierId; }
    public String getContactName() { return contactName; }
    public String getContactPhone() { return contactPhone; }
    public String getShopUrl() { return shopUrl; }
    public String getNote() { return note; }
    public SupplierStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(
        String name, SupplierPlatform platform, String externalSupplierId,
        String contactName, String contactPhone, String shopUrl, String note
    ) {
        this.name = name;
        this.platform = platform;
        this.externalSupplierId = externalSupplierId;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.shopUrl = shopUrl;
        this.note = note;
    }

    public void enable() {
        status = SupplierStatus.ACTIVE;
    }

    public void disable() {
        status = SupplierStatus.INACTIVE;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
