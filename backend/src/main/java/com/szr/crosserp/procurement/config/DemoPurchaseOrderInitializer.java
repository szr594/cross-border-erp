package com.szr.crosserp.procurement.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(30)
public class DemoPurchaseOrderInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public DemoPurchaseOrderInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long ownerId = jdbcTemplate.query(
            "SELECT id FROM app_users WHERE LOWER(login_name) = 'owner'",
            resultSet -> resultSet.next() ? resultSet.getLong(1) : null
        );
        if (ownerId == null) {
            return;
        }

        seedSupplier("DEMO-1688-HOME", "杭州木棉家居", "王女士", "13800001001", "https://example.com/demo-home");
        seedSupplier("DEMO-1688-TEXTILE", "绍兴云织纺织", "陈先生", "13800001002", "https://example.com/demo-textile");
        seedSupplier("DEMO-1688-CRAFT", "义乌拾光工艺", "林女士", "13800001003", "https://example.com/demo-craft");

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<DemoPurchaseOrder> orders = List.of(
            new DemoPurchaseOrder("DEMO-PO-240101", "DEMO-240101", "DEMO-1688-HOME", "DRAFT", 0, 4, 0, null, 8),
            new DemoPurchaseOrder("DEMO-PO-240102", "DEMO-240102", "DEMO-1688-TEXTILE", "ORDERED", 16800, 8, 0, "1688-DEMO-10002", 7),
            new DemoPurchaseOrder("DEMO-PO-240103", "DEMO-240103", "DEMO-1688-CRAFT", "PARTIALLY_RECEIVED", 9600, 2, 1, "1688-DEMO-10003", 6),
            new DemoPurchaseOrder("DEMO-PO-240104", "DEMO-240104", "DEMO-1688-HOME", "RECEIVED", 13500, 3, 3, "1688-DEMO-10004", 5),
            new DemoPurchaseOrder("DEMO-PO-240105", "DEMO-240105", "DEMO-1688-TEXTILE", "CANCELED", 11400, 6, 0, "1688-DEMO-10005", 4),
            new DemoPurchaseOrder("DEMO-PO-240106", "DEMO-240106", "DEMO-1688-CRAFT", "COMPLETED", 21600, 12, 12, "1688-DEMO-10006", 3)
        );
        for (DemoPurchaseOrder order : orders) {
            seedPurchaseOrder(ownerId, order, now.minus(order.ageDays(), ChronoUnit.DAYS));
        }
    }

    private void seedSupplier(String code, String name, String contactName, String contactPhone, String shopUrl) {
        jdbcTemplate.update("""
            INSERT INTO suppliers(
                code, name, platform, external_supplier_id, contact_name,
                contact_phone, shop_url, note, status, version
            )
            SELECT ?, ?, 'ALIBABA_1688', ?, ?, ?, ?, '仅供作品集展示的虚构供应商', 'ACTIVE', 0
            WHERE NOT EXISTS (SELECT 1 FROM suppliers WHERE code = ?)
            """, code, name, code, contactName, contactPhone, shopUrl, code);
    }

    private void seedPurchaseOrder(Long ownerId, DemoPurchaseOrder demo, Instant createdAt) {
        Long supplierId = jdbcTemplate.queryForObject(
            "SELECT id FROM suppliers WHERE code = ?", Long.class, demo.supplierCode()
        );
        Long orderItemId = jdbcTemplate.query("""
            SELECT item.id
            FROM sales_order_items item
            JOIN sales_orders orders ON orders.id = item.order_id
            WHERE orders.order_no = ?
            """, resultSet -> resultSet.next() ? resultSet.getLong(1) : null, demo.salesOrderNo());
        if (orderItemId == null) {
            return;
        }

        jdbcTemplate.update("""
            INSERT INTO order_procurement_requests(
                order_item_id, requested_quantity, status, requested_by, requested_at, note, version
            )
            SELECT ?, ?, 'REQUESTED', ?, ?, '演示采购申请', 0
            WHERE NOT EXISTS (
                SELECT 1 FROM order_procurement_requests
                WHERE order_item_id = ? AND status IN ('REQUESTED', 'CONVERTED')
            )
            """, orderItemId, demo.quantity(), ownerId, createdAt, orderItemId);
        Long requestId = jdbcTemplate.queryForObject("""
            SELECT id FROM order_procurement_requests
            WHERE order_item_id = ? AND status IN ('REQUESTED', 'CONVERTED')
            ORDER BY id DESC
            LIMIT 1
            """, Long.class, orderItemId);

        Instant orderedAt = "DRAFT".equals(demo.status()) ? null : createdAt.plus(1, ChronoUnit.HOURS);
        Instant expectedArrivalAt = orderedAt == null ? null : orderedAt.plus(7, ChronoUnit.DAYS);
        jdbcTemplate.update("""
            INSERT INTO purchase_orders(
                purchase_order_no, supplier_id, status, external_order_no, currency,
                total_amount_minor, ordered_at, expected_arrival_at, created_by, ordered_by,
                canceled_by, canceled_at, cancel_reason, note, version, created_at, updated_at
            )
            SELECT ?, ?, ?, ?, 'CNY', ?, ?, ?, ?, ?, ?, ?, ?, '仅供作品集展示的虚构采购单', 0, ?, ?
            WHERE NOT EXISTS (SELECT 1 FROM purchase_orders WHERE purchase_order_no = ?)
            """,
            demo.purchaseOrderNo(), supplierId, demo.status(), demo.externalOrderNo(), demo.totalAmountMinor(),
            orderedAt, expectedArrivalAt, ownerId, orderedAt == null ? null : ownerId,
            "CANCELED".equals(demo.status()) ? ownerId : null,
            "CANCELED".equals(demo.status()) ? createdAt.plus(2, ChronoUnit.HOURS) : null,
            "CANCELED".equals(demo.status()) ? "演示：销售订单取消" : null,
            createdAt, createdAt, demo.purchaseOrderNo()
        );
        Long purchaseOrderId = jdbcTemplate.queryForObject(
            "SELECT id FROM purchase_orders WHERE purchase_order_no = ?", Long.class, demo.purchaseOrderNo()
        );
        Long unitPriceMinor = demo.totalAmountMinor() == 0
            ? null
            : demo.totalAmountMinor() / demo.quantity();
        jdbcTemplate.update("""
            INSERT INTO purchase_order_items(
                purchase_order_id, supplier_product_id, supplier_sku, product_name, variant_name,
                product_url, image_url, purchased_quantity, received_quantity, unit_price_minor,
                currency, note, version, created_at, updated_at
            )
            SELECT ?, CONCAT('DEMO-PRODUCT-', item.id), item.external_sku, item.product_name, item.variant_name,
                   'https://example.com/demo-product', item.image_url, ?, ?, ?,
                   'CNY', '演示采购商品', 0, ?, ?
            FROM sales_order_items item
            WHERE item.id = ?
              AND NOT EXISTS (SELECT 1 FROM purchase_order_items WHERE purchase_order_id = ?)
            """,
            purchaseOrderId, demo.quantity(), demo.receivedQuantity(), unitPriceMinor,
            createdAt, createdAt, orderItemId, purchaseOrderId
        );
        Long purchaseOrderItemId = jdbcTemplate.queryForObject(
            "SELECT id FROM purchase_order_items WHERE purchase_order_id = ? ORDER BY id LIMIT 1",
            Long.class, purchaseOrderId
        );
        jdbcTemplate.update("""
            INSERT INTO purchase_order_allocations(purchase_order_item_id, procurement_request_id, allocated_quantity)
            SELECT ?, ?, ?
            WHERE NOT EXISTS (
                SELECT 1 FROM purchase_order_allocations
                WHERE purchase_order_item_id = ? AND procurement_request_id = ?
            )
            """, purchaseOrderItemId, requestId, demo.quantity(), purchaseOrderItemId, requestId);
        jdbcTemplate.update("UPDATE order_procurement_requests SET status = 'CONVERTED' WHERE id = ?", requestId);

        seedEvent(purchaseOrderId, "CREATED", null, "DRAFT", ownerId, "演示采购单初始化", createdAt);
        if (!"DRAFT".equals(demo.status())) {
            seedEvent(purchaseOrderId, demo.status(), "DRAFT", demo.status(), ownerId, statusRemark(demo.status()), createdAt.plus(1, ChronoUnit.HOURS));
        }
    }

    private void seedEvent(
        Long purchaseOrderId, String eventType, String fromValue, String toValue,
        Long ownerId, String remark, Instant occurredAt
    ) {
        jdbcTemplate.update("""
            INSERT INTO purchase_order_events(
                purchase_order_id, event_type, from_value, to_value, operator_id, remark, occurred_at
            )
            SELECT ?, ?, ?, ?, ?, ?, ?
            WHERE NOT EXISTS (
                SELECT 1 FROM purchase_order_events WHERE purchase_order_id = ? AND event_type = ?
            )
            """, purchaseOrderId, eventType, fromValue, toValue, ownerId, remark, occurredAt, purchaseOrderId, eventType);
    }

    private String statusRemark(String status) {
        return switch (status) {
            case "ORDERED" -> "演示：已向供应商下单";
            case "PARTIALLY_RECEIVED" -> "演示：部分商品已到货";
            case "RECEIVED" -> "演示：采购商品全部到货";
            case "COMPLETED" -> "演示：采购流程已完成";
            case "CANCELED" -> "演示：采购单已取消";
            default -> "演示采购状态更新";
        };
    }

    private record DemoPurchaseOrder(
        String purchaseOrderNo, String salesOrderNo, String supplierCode, String status,
        long totalAmountMinor, int quantity, int receivedQuantity, String externalOrderNo, long ageDays
    ) {
    }
}
