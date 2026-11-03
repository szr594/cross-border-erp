package com.szr.crosserp.procurement;

import static org.assertj.core.api.Assertions.assertThat;

import com.szr.crosserp.order.config.DemoOrderInitializer;
import com.szr.crosserp.procurement.config.DemoPurchaseOrderInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class DemoPurchaseOrderInitializerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void createsSixTraceablePurchaseOrdersIdempotently() {
        jdbcTemplate.update("""
            INSERT INTO app_users(login_name, display_name, password_hash, status)
            SELECT 'owner', '演示老板', 'test-only-password-hash', 'ACTIVE'
            WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE login_name = 'owner')
            """);
        DemoOrderInitializer orderInitializer = new DemoOrderInitializer(jdbcTemplate);
        DemoPurchaseOrderInitializer purchaseInitializer = new DemoPurchaseOrderInitializer(jdbcTemplate);

        orderInitializer.run(null);
        purchaseInitializer.run(null);
        purchaseInitializer.run(null);

        assertThat(count("SELECT COUNT(*) FROM suppliers WHERE code LIKE 'DEMO-1688-%'"))
            .isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM purchase_orders WHERE purchase_order_no LIKE 'DEMO-PO-%'"))
            .isEqualTo(6);
        assertThat(count("""
            SELECT COUNT(*) FROM purchase_order_items item
            JOIN purchase_orders orders ON orders.id = item.purchase_order_id
            WHERE orders.purchase_order_no LIKE 'DEMO-PO-%'
            """))
            .isEqualTo(6);
        assertThat(count("""
            SELECT COUNT(*) FROM purchase_order_allocations allocation
            JOIN purchase_order_items item ON item.id = allocation.purchase_order_item_id
            JOIN purchase_orders orders ON orders.id = item.purchase_order_id
            WHERE orders.purchase_order_no LIKE 'DEMO-PO-%'
            """))
            .isEqualTo(6);
        assertThat(count("""
            SELECT COUNT(*) FROM purchase_order_events event
            JOIN purchase_orders orders ON orders.id = event.purchase_order_id
            WHERE orders.purchase_order_no LIKE 'DEMO-PO-%'
            """))
            .isEqualTo(11);
        assertThat(count("""
            SELECT COUNT(DISTINCT orders.status)
            FROM purchase_orders orders
            WHERE orders.purchase_order_no LIKE 'DEMO-PO-%'
            """))
            .isEqualTo(6);
    }

    private Integer count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
