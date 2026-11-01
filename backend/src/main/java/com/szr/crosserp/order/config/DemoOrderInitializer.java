package com.szr.crosserp.order.config;

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
@Order(20)
public class DemoOrderInitializer implements ApplicationRunner {

    private static final String STORE_CODE = "DEMO-FAIRE";

    private final JdbcTemplate jdbcTemplate;

    public DemoOrderInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long ownerId = jdbcTemplate.query(
            "SELECT id FROM app_users WHERE LOWER(login_name) = 'owner'",
            resultSet -> resultSet.next() ? resultSet.getLong(1) : null
        );

        jdbcTemplate.update("""
            INSERT INTO sales_channels(code, name, enabled)
            SELECT 'DEMO', '演示销售渠道', TRUE
            WHERE NOT EXISTS (SELECT 1 FROM sales_channels WHERE code = 'DEMO')
            """);
        Long channelId = jdbcTemplate.queryForObject(
            "SELECT id FROM sales_channels WHERE code = 'DEMO'",
            Long.class
        );
        jdbcTemplate.update("""
            INSERT INTO sales_stores(channel_id, external_store_id, code, name, current_owner_id, status)
            SELECT ?, 'demo-faire-store', ?, 'Faire 演示店铺', ?, 'ACTIVE'
            WHERE NOT EXISTS (SELECT 1 FROM sales_stores WHERE code = ?)
            """, channelId, STORE_CODE, ownerId, STORE_CODE);
        Long storeId = jdbcTemplate.queryForObject(
            "SELECT id FROM sales_stores WHERE code = ?",
            Long.class,
            STORE_CODE
        );

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<DemoOrder> orders = List.of(
            new DemoOrder("DEMO-240101", "FAIRE-10001", "F-10001", "PROCESSING", false,
                "北欧风陶瓷花瓶", "SKU-VASE-GREEN", 4, now.minus(5, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS)),
            new DemoOrder("DEMO-240102", "FAIRE-10002", "F-10002", "PROCESSING", false,
                "手工亚麻桌旗", "SKU-RUNNER-NATURAL", 8, now.minus(4, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS)),
            new DemoOrder("DEMO-240103", "FAIRE-10003", "F-10003", "PROCESSING", true,
                "黄铜烛台组合", "SKU-CANDLE-BRASS", 2, now.minus(3, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS)),
            new DemoOrder("DEMO-240104", "FAIRE-10004", "F-10004", "DELIVERED", false,
                "胡桃木托盘", "SKU-TRAY-WALNUT", 3, now.minus(12, ChronoUnit.DAYS), now.minus(2, ChronoUnit.DAYS)),
            new DemoOrder("DEMO-240105", "FAIRE-10005", "F-10005", "CANCELED", false,
                "棉麻靠垫套", "SKU-CUSHION-BEIGE", 6, now.minus(8, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS)),
            new DemoOrder("DEMO-240106", "FAIRE-10006", "F-10006", "NEW", false,
                "玻璃储物罐", "SKU-JAR-AMBER", 12, now.minus(1, ChronoUnit.DAYS), now.plus(5, ChronoUnit.DAYS))
        );
        for (int index = 0; index < orders.size(); index++) {
            seedOrder(storeId, ownerId, orders.get(index), index + 1);
        }
        if (ownerId != null) {
            seedProcurementRequest(ownerId);
        }
    }

    private void seedOrder(Long storeId, Long ownerId, DemoOrder demo, int customerIndex) {
        jdbcTemplate.update("""
            INSERT INTO sales_orders(
                order_no, store_id, external_order_id, display_order_no, external_status,
                fulfillment_exception, exception_reason, responsible_user_id, currency,
                ordered_at, requested_ship_at, customer_id, customer_name, recipient_name,
                recipient_phone, country_code, province, city, district, address_line1,
                postal_code, note, version, created_at, updated_at
            )
            SELECT ?, ?, ?, ?, ?, ?, ?, ?, 'USD', ?, ?, ?, ?, ?, ?, 'US', ?, ?, ?, ?, ?, ?, 0, ?, ?
            WHERE NOT EXISTS (SELECT 1 FROM sales_orders WHERE order_no = ?)
            """,
            demo.orderNo(), storeId, demo.externalOrderId(), demo.displayOrderNo(), demo.externalStatus(),
            demo.exception(), demo.exception() ? "演示：商品规格需要人工确认" : null, ownerId,
            demo.orderedAt(), demo.requestedShipAt(), "DEMO-CUSTOMER-" + customerIndex,
            "演示客户 " + customerIndex, "Demo Recipient " + customerIndex, "+1 555 010" + customerIndex,
            "California", "San Francisco", "District " + customerIndex, customerIndex + " Market Street",
            "9410" + customerIndex, "仅供作品集演示的虚构订单", demo.orderedAt(), demo.orderedAt(), demo.orderNo()
        );
        Long orderId = jdbcTemplate.queryForObject(
            "SELECT id FROM sales_orders WHERE order_no = ?",
            Long.class,
            demo.orderNo()
        );
        jdbcTemplate.update("""
            INSERT INTO sales_order_items(
                order_id, external_item_id, external_sku, product_name, variant_name,
                quantity, unit_price_minor, currency, fulfillment_status, version
            )
            SELECT ?, ?, ?, ?, '标准款', ?, 2599, 'USD', 'NORMAL', 0
            WHERE NOT EXISTS (
                SELECT 1 FROM sales_order_items WHERE order_id = ? AND external_item_id = ?
            )
            """, orderId, demo.externalOrderId() + "-ITEM", demo.sku(), demo.productName(), demo.quantity(),
            orderId, demo.externalOrderId() + "-ITEM");
        jdbcTemplate.update("""
            INSERT INTO order_status_events(order_id, event_type, to_value, operator_id, source, remark, occurred_at)
            SELECT ?, 'ORDER_IMPORTED', ?, ?, 'IMPORT', '演示订单初始化', ?
            WHERE NOT EXISTS (
                SELECT 1 FROM order_status_events WHERE order_id = ? AND event_type = 'ORDER_IMPORTED'
            )
            """, orderId, demo.externalStatus(), ownerId, demo.orderedAt(), orderId);
    }

    private void seedProcurementRequest(Long ownerId) {
        Long itemId = jdbcTemplate.query("""
            SELECT item.id
            FROM sales_order_items item
            JOIN sales_orders orders ON orders.id = item.order_id
            WHERE orders.order_no = 'DEMO-240102'
            """, resultSet -> resultSet.next() ? resultSet.getLong(1) : null);
        if (itemId == null) {
            return;
        }
        jdbcTemplate.update("""
            INSERT INTO order_procurement_requests(
                order_item_id, requested_quantity, status, requested_by, requested_at, note, version
            )
            SELECT ?, 8, 'REQUESTED', ?, CURRENT_TIMESTAMP, '演示采购申请', 0
            WHERE NOT EXISTS (
                SELECT 1 FROM order_procurement_requests
                WHERE order_item_id = ? AND status IN ('REQUESTED', 'CONVERTED')
            )
            """, itemId, ownerId, itemId);
    }

    private record DemoOrder(
        String orderNo,
        String externalOrderId,
        String displayOrderNo,
        String externalStatus,
        boolean exception,
        String productName,
        String sku,
        int quantity,
        Instant orderedAt,
        Instant requestedShipAt
    ) {
    }
}
