package com.szr.crosserp.procurement;

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
import org.junit.jupiter.api.Test;
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
class MySqlPurchaseOrderCommandConcurrencyIT {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void concurrentConversionOfTheSameRequestCreatesOnlyOnePurchaseOrder() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Long actorId = insertActor(suffix);
        Long supplierId = insertSupplier(suffix);
        Long requestId = insertProcurementRequest(suffix, actorId);
        String body = """
            {"supplierId":%d,"currency":"CNY","items":[{
              "productName":"MySQL并发采购商品","unitPriceMinor":1000,
              "allocations":[{"procurementRequestId":%d,"quantity":2,"version":0}]
            }]}
            """.formatted(supplierId, requestId);

        List<MvcResult> results = runConcurrently(actorId, body);

        assertThat(results.stream().map(result -> result.getResponse().getStatus()).sorted().toList())
            .containsExactly(201, 409);
        MvcResult conflict = results.stream().filter(result -> result.getResponse().getStatus() == 409).findFirst().orElseThrow();
        assertThat(conflict.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .contains("\"code\":\"PROCUREMENT_REQUEST_UNAVAILABLE\"");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM purchase_order_allocations WHERE procurement_request_id = ?",
            Integer.class, requestId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM order_procurement_requests WHERE id = ?", String.class, requestId
        )).isEqualTo("CONVERTED");
    }

    private Long insertActor(String suffix) {
        String login = "mysql-po-" + suffix;
        jdbcTemplate.update("""
            INSERT INTO app_users(login_name, display_name, password_hash, status, must_change_password)
            VALUES (?, '并发采购员', 'not-used', 'ACTIVE', FALSE)
            """, login);
        Long actorId = jdbcTemplate.queryForObject("SELECT id FROM app_users WHERE login_name = ?", Long.class, login);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = 'PROCUREMENT'", Long.class);
        jdbcTemplate.update("INSERT INTO user_roles(user_id, role_id) VALUES (?, ?)", actorId, roleId);
        return actorId;
    }

    private Long insertSupplier(String suffix) {
        String code = "MYSQL-PO-SUP-" + suffix;
        jdbcTemplate.update("""
            INSERT INTO suppliers(code, name, platform, status, version)
            VALUES (?, 'MySQL并发供应商', 'MANUAL', 'ACTIVE', 0)
            """, code);
        return jdbcTemplate.queryForObject("SELECT id FROM suppliers WHERE code = ?", Long.class, code);
    }

    private Long insertProcurementRequest(String suffix, Long actorId) {
        String channelCode = "MYSQL-PO-" + suffix;
        jdbcTemplate.update("INSERT INTO sales_channels(code, name, enabled) VALUES (?, '并发渠道', TRUE)", channelCode);
        Long channelId = jdbcTemplate.queryForObject("SELECT id FROM sales_channels WHERE code = ?", Long.class, channelCode);
        String storeCode = "MYSQL-PO-STORE-" + suffix;
        jdbcTemplate.update("""
            INSERT INTO sales_stores(channel_id, external_store_id, code, name, status)
            VALUES (?, ?, ?, '并发店铺', 'ACTIVE')
            """, channelId, storeCode, storeCode);
        Long storeId = jdbcTemplate.queryForObject("SELECT id FROM sales_stores WHERE code = ?", Long.class, storeCode);
        String orderNo = "MYSQL-PO-ORDER-" + suffix;
        jdbcTemplate.update("""
            INSERT INTO sales_orders(order_no, store_id, external_order_id, external_status,
                fulfillment_exception, currency, version, created_at, updated_at)
            VALUES (?, ?, ?, 'PROCESSING', FALSE, 'CNY', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            """, orderNo, storeId, orderNo);
        Long orderId = jdbcTemplate.queryForObject("SELECT id FROM sales_orders WHERE order_no = ?", Long.class, orderNo);
        jdbcTemplate.update("""
            INSERT INTO sales_order_items(order_id, external_item_id, product_name, quantity, currency,
                fulfillment_status, version, created_at, updated_at)
            VALUES (?, ?, '并发商品', 2, 'CNY', 'NORMAL', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            """, orderId, "ITEM-" + suffix);
        Long itemId = jdbcTemplate.queryForObject(
            "SELECT id FROM sales_order_items WHERE order_id = ?", Long.class, orderId
        );
        jdbcTemplate.update("""
            INSERT INTO order_procurement_requests(order_item_id, requested_quantity, status,
                requested_by, requested_at, version)
            VALUES (?, 2, 'REQUESTED', ?, CURRENT_TIMESTAMP(6), 0)
            """, itemId, actorId);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM order_procurement_requests WHERE order_item_id = ?", Long.class, itemId
        );
    }

    private List<MvcResult> runConcurrently(Long actorId, String body) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return mockMvc.perform(post("/api/purchase-orders")
                        .with(jwt().jwt(token -> token.subject(actorId.toString()).claim("roles", List.of("PROCUREMENT"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)).andReturn();
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
}
