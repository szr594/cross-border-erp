package com.szr.crosserp.order.model;

import com.szr.crosserp.store.model.SalesStore;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sales_orders")
public class SalesOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 32)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private SalesStore store;

    @Column(name = "external_order_id", nullable = false, length = 128)
    private String externalOrderId;

    @Column(name = "display_order_no", length = 128)
    private String displayOrderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "external_status", nullable = false, length = 64)
    private ExternalOrderStatus externalStatus;

    @Column(name = "external_status_raw", length = 128)
    private String externalStatusRaw;

    @Column(name = "fulfillment_exception", nullable = false)
    private boolean fulfillmentException;

    @Column(name = "exception_reason", length = 500)
    private String exceptionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_user_id")
    private User responsibleUser;

    @Column(length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @Column(name = "ship_after")
    private Instant shipAfter;

    @Column(name = "requested_ship_at")
    private Instant requestedShipAt;

    @Column(name = "external_updated_at")
    private Instant externalUpdatedAt;

    @Column(name = "customer_id", length = 128)
    private String customerId;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    @Column(name = "recipient_phone", length = 64)
    private String recipientPhone;

    @Column(name = "country_code", length = 2, columnDefinition = "CHAR(2)")
    private String countryCode;

    @Column(length = 128)
    private String province;

    @Column(length = 128)
    private String city;

    @Column(length = 128)
    private String district;

    @Column(name = "address_line1", length = 500)
    private String addressLine1;

    @Column(name = "address_line2", length = 500)
    private String addressLine2;

    @Column(name = "postal_code", length = 32)
    private String postalCode;

    @Column(length = 1000)
    private String note;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_payload", columnDefinition = "JSON")
    private String sourcePayload;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SalesOrder() {
    }

    public SalesOrder(String orderNo, SalesStore store, String externalOrderId, ExternalOrderStatus externalStatus) {
        this.orderNo = orderNo;
        this.store = store;
        this.externalOrderId = externalOrderId;
        this.externalStatus = externalStatus;
    }

    public static SalesOrder imported(
        String orderNo, SalesStore store, String externalOrderId, String displayOrderNo,
        ExternalOrderStatus status, String rawStatus, User responsibleUser, String currency,
        Instant orderedAt, Instant requestedShipAt, String customerId, String customerName,
        String recipientName, String recipientPhone, String countryCode, String province,
        String city, String district, String addressLine1, String addressLine2, String postalCode,
        String note, Instant now
    ) {
        SalesOrder order = new SalesOrder(orderNo, store, externalOrderId, status);
        order.displayOrderNo = displayOrderNo;
        order.externalStatusRaw = rawStatus;
        order.responsibleUser = responsibleUser;
        order.currency = currency;
        order.orderedAt = orderedAt;
        order.requestedShipAt = requestedShipAt;
        order.customerId = customerId;
        order.customerName = customerName;
        order.recipientName = recipientName;
        order.recipientPhone = recipientPhone;
        order.countryCode = countryCode;
        order.province = province;
        order.city = city;
        order.district = district;
        order.addressLine1 = addressLine1;
        order.addressLine2 = addressLine2;
        order.postalCode = postalCode;
        order.note = note;
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public SalesStore getStore() { return store; }
    public String getExternalOrderId() { return externalOrderId; }
    public String getDisplayOrderNo() { return displayOrderNo; }
    public ExternalOrderStatus getExternalStatus() { return externalStatus; }
    public String getExternalStatusRaw() { return externalStatusRaw; }
    public boolean isFulfillmentException() { return fulfillmentException; }
    public String getExceptionReason() { return exceptionReason; }
    public User getResponsibleUser() { return responsibleUser; }
    public String getCurrency() { return currency; }
    public Instant getOrderedAt() { return orderedAt; }
    public Instant getShipAfter() { return shipAfter; }
    public Instant getRequestedShipAt() { return requestedShipAt; }
    public Instant getExternalUpdatedAt() { return externalUpdatedAt; }
    public String getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getCountryCode() { return countryCode; }
    public String getProvince() { return province; }
    public String getCity() { return city; }
    public String getDistrict() { return district; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getPostalCode() { return postalCode; }
    public String getNote() { return note; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void changeResponsibleUser(User user, Instant now) {
        responsibleUser = user;
        updatedAt = now;
    }

    public void openException(String reason, Instant now) {
        fulfillmentException = true;
        exceptionReason = reason;
        updatedAt = now;
    }

    public void resolveException(Instant now) {
        fulfillmentException = false;
        exceptionReason = null;
        updatedAt = now;
    }

    public void cancel(Instant now) {
        externalStatus = ExternalOrderStatus.CANCELED;
        updatedAt = now;
    }

    public void touch(Instant now) {
        updatedAt = now;
    }
}
