package com.szr.crosserp.orderimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql-integration")
class MySqlOrderImportIT {
    private static final String HEADER = "store_code,external_order_id,display_order_no,external_status,"
        + "ordered_at,requested_ship_at,currency,responsible_login,customer_id,customer_name,recipient_name,recipient_phone,"
        + "country_code,province,city,district,address_line1,address_line2,postal_code,external_item_id,external_product_id,"
        + "external_variant_id,external_sku,product_name,variant_name,image_url,quantity,unit_price_minor,note";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Flyway flyway;

    @Test
    void mysql84MigratesAndConfirmsOneCsvBatchIdempotently() throws Exception {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String login = "csv-mysql-" + suffix;
        jdbcTemplate.update("""
            INSERT INTO app_users(login_name, display_name, password_hash, status, must_change_password,
                failed_login_count, created_at, updated_at)
            VALUES (?, 'CSV MySQL 验收', 'not-used', 'ACTIVE', FALSE, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            """, login);
        Long actorId = jdbcTemplate.queryForObject("SELECT id FROM app_users WHERE login_name = ?", Long.class, login);
        String channelCode = "CSVIT-" + suffix;
        jdbcTemplate.update("INSERT INTO sales_channels(code, name, enabled) VALUES (?, 'CSV MySQL 渠道', TRUE)", channelCode);
        Long channelId = jdbcTemplate.queryForObject("SELECT id FROM sales_channels WHERE code = ?", Long.class, channelCode);
        String storeCode = "CSVIT-STORE-" + suffix;
        jdbcTemplate.update("""
            INSERT INTO sales_stores(channel_id, external_store_id, code, name, status)
            VALUES (?, ?, ?, 'CSV MySQL 店铺', 'ACTIVE')
            """, channelId, "csvit-store-" + suffix, storeCode);
        String externalOrderId = "CSVIT-ORDER-" + suffix;
        String csv = "\uFEFF" + HEADER + "\r\n" + String.join(",", List.of(
            storeCode, externalOrderId, "CSVIT-DISPLAY-" + suffix, "PROCESSING", "2026-11-11T02:30:00Z",
            "2026-11-18T02:30:00Z", "USD", login, "CSVIT-CUSTOMER", "虚构客户", "Demo Recipient",
            "+15550188", "US", "California", "San Francisco", "", "88 Market Street", "", "94105",
            "CSVIT-ITEM-" + suffix, "CSVIT-PRODUCT", "CSVIT-VARIANT", "CSVIT-SKU", "MySQL CSV 演示商品",
            "蓝色", "", "2", "2599", "MySQL 8.4 集成验证"
        )) + "\r\n";
        MockMultipartFile file = new MockMultipartFile(
            "file", "mysql-orders.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );
        var preview = mockMvc.perform(multipart("/api/order-imports/preview").file(file).with(actor(actorId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("VALIDATED"))
            .andExpect(jsonPath("$.creatableOrderCount").value(1))
            .andReturn();
        String importId = objectMapper.readTree(preview.getResponse().getContentAsByteArray()).get("importId").asText();

        mockMvc.perform(post("/api/order-imports/{importId}/confirm", importId).with(actor(actorId)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(post("/api/order-imports/{importId}/confirm", importId).with(actor(actorId)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales_orders WHERE external_order_id = ?", Integer.class, externalOrderId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM order_status_events event
            JOIN sales_orders orders ON orders.id = event.order_id
            WHERE orders.external_order_id = ? AND event.event_type = 'ORDER_IMPORTED'
            """, Integer.class, externalOrderId)).isEqualTo(1);
    }

    private JwtRequestPostProcessor actor(Long actorId) {
        return jwt().jwt(token -> token.subject(actorId.toString()).claim("roles", List.of("OWNER")));
    }
}
