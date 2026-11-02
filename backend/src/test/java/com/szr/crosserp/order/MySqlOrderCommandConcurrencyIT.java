package com.szr.crosserp.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("mysql-integration")
@SpringBootTest
@AutoConfigureMockMvc
class MySqlOrderCommandConcurrencyIT {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    private Long actorId;
    private Long orderId;
    private Long itemId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
            INSERT INTO app_users(login_name, display_name, password_hash, status, must_change_password)
            VALUES (?, '并发测试运营', 'not-used', 'ACTIVE', FALSE)
            """, "mysql-it-ops-" + suffix);
        actorId = jdbcTemplate.queryForObject(
            "SELECT id FROM app_users WHERE login_name = ?", Long.class, "mysql-it-ops-" + suffix
        );
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = 'OPERATIONS'", Long.class);
        jdbcTemplate.update("INSERT INTO user_roles(user_id, role_id) VALUES (?, ?)", actorId, roleId);

        jdbcTemplate.update(
            "INSERT INTO sales_channels(code, name, enabled) VALUES (?, 'MySQL 并发测试渠道', TRUE)",
            "MYSQL-IT-" + suffix
        );
        Long channelId = jdbcTemplate.queryForObject(
            "SELECT id FROM sales_channels WHERE code = ?", Long.class, "MYSQL-IT-" + suffix
        );
        jdbcTemplate.update("""
            INSERT INTO sales_stores(channel_id, external_store_id, code, name, status)
            VALUES (?, ?, ?, 'MySQL 并发测试店铺', 'ACTIVE')
            """, channelId, "mysql-it-store-" + suffix, "MYSQL-IT-STORE-" + suffix);
        Long storeId = jdbcTemplate.queryForObject(
            "SELECT id FROM sales_stores WHERE code = ?", Long.class, "MYSQL-IT-STORE-" + suffix
        );
        jdbcTemplate.update("""
            INSERT INTO sales_orders(
                order_no, store_id, external_order_id, external_status, fulfillment_exception,
                responsible_user_id, currency, version, created_at, updated_at
            ) VALUES (?, ?, ?, 'PROCESSING', FALSE, ?, 'USD', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            """, "MYSQL-IT-ORDER-" + suffix, storeId, "MYSQL-IT-EXT-" + suffix, actorId);
        orderId = jdbcTemplate.queryForObject(
            "SELECT id FROM sales_orders WHERE external_order_id = ?",
            Long.class, "MYSQL-IT-EXT-" + suffix
        );
        jdbcTemplate.update("""
            INSERT INTO sales_order_items(
                order_id, external_item_id, product_name, quantity, currency,
                fulfillment_status, version, created_at, updated_at
            ) VALUES (?, ?, '并发测试商品', 5, 'USD', 'NORMAL', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            """, orderId, "MYSQL-IT-ITEM-" + suffix);
        itemId = jdbcTemplate.queryForObject(
            "SELECT id FROM sales_order_items WHERE order_id = ?", Long.class, orderId
        );
    }

    @RepeatedTest(5)
    void concurrentExceptionCommandsAllowOnlyOneCommitForTheSameOrderVersion() throws Exception {
        List<MvcResult> results = runConcurrently(() -> post("/api/orders/{orderId}/exceptions", orderId)
            .with(jwt().jwt(token -> token.subject(actorId.toString()).claim("roles", List.of("OPERATIONS"))))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"MySQL并发异常\",\"version\":0}"));

        assertStatuses(results, 201, 409);
        assertConflictCode(results, "ORDER_VERSION_CONFLICT");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT fulfillment_exception FROM sales_orders WHERE id = ?", Boolean.class, orderId
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_status_events WHERE order_id = ? AND event_type = 'EXCEPTION_OPENED'",
            Integer.class, orderId
        )).isEqualTo(1);
    }

    @RepeatedTest(5)
    void concurrentProcurementCommandsNeverAllocateBeyondTheGap() throws Exception {
        String body = "{\"items\":[{\"orderItemId\":%d,\"quantity\":4}],\"note\":\"并发采购\",\"version\":0}"
            .formatted(itemId);
        List<MvcResult> results = runConcurrently(() -> post("/api/orders/{orderId}/procurement-requests", orderId)
            .with(jwt().jwt(token -> token.subject(actorId.toString()).claim("roles", List.of("OPERATIONS"))))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));

        assertStatuses(results, 201, 409);
        assertConflictCode(results, "ORDER_VERSION_CONFLICT");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(requested_quantity), 0)
            FROM order_procurement_requests
            WHERE order_item_id = ? AND status IN ('REQUESTED', 'CONVERTED')
            """, Integer.class, itemId)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_procurement_requests WHERE order_item_id = ?",
            Integer.class, itemId
        )).isEqualTo(1);
    }

    private List<MvcResult> runConcurrently(RequestFactory requestFactory) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return mockMvc.perform(requestFactory.create()).andReturn();
                }));
            }
            ready.await();
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertStatuses(List<MvcResult> results, int... expected) {
        assertThat(results.stream().map(result -> result.getResponse().getStatus()).sorted().toList())
            .containsExactly(java.util.Arrays.stream(expected).sorted().boxed().toArray(Integer[]::new));
    }

    private void assertConflictCode(List<MvcResult> results, String code) throws Exception {
        MvcResult conflict = results.stream()
            .filter(result -> result.getResponse().getStatus() == 409)
            .findFirst()
            .orElseThrow();
        String body = conflict.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"code\":\"" + code + "\"");
    }

    @FunctionalInterface
    private interface RequestFactory {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create();
    }
}
