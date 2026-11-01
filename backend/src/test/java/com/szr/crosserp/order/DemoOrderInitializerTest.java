package com.szr.crosserp.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.szr.crosserp.order.config.DemoOrderInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class DemoOrderInitializerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void createsSixFictionalOrdersIdempotentlyWithoutRequiringDemoOwner() {
        DemoOrderInitializer initializer = new DemoOrderInitializer(jdbcTemplate);

        initializer.run(null);
        initializer.run(null);

        Integer orderCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sales_orders orders
            JOIN sales_stores store ON store.id = orders.store_id
            WHERE store.code = 'DEMO-FAIRE'
            """, Integer.class);
        Integer eventCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM order_status_events event
            JOIN sales_orders orders ON orders.id = event.order_id
            JOIN sales_stores store ON store.id = orders.store_id
            WHERE store.code = 'DEMO-FAIRE'
            """, Integer.class);

        assertThat(orderCount).isEqualTo(6);
        assertThat(eventCount).isEqualTo(6);
    }
}
