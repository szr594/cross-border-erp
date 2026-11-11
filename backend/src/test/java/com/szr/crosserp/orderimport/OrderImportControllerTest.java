package com.szr.crosserp.orderimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szr.crosserp.store.model.SalesChannel;
import com.szr.crosserp.store.model.SalesStore;
import com.szr.crosserp.store.repository.SalesChannelRepository;
import com.szr.crosserp.store.repository.SalesStoreRepository;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderImportControllerTest {
    private static final String HEADER = "store_code,external_order_id,display_order_no,external_status,"
        + "ordered_at,requested_ship_at,currency,responsible_login,customer_id,customer_name,recipient_name,recipient_phone,"
        + "country_code,province,city,district,address_line1,address_line2,postal_code,external_item_id,external_product_id,"
        + "external_variant_id,external_sku,product_name,variant_name,image_url,quantity,unit_price_minor,note";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired SalesChannelRepository channelRepository;
    @Autowired SalesStoreRepository storeRepository;

    private User owner;
    private User operations;
    private String storeCode;
    private String externalOrderId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = userRepository.save(new User("import-owner-" + suffix, "导入老板", "not-used", false));
        operations = userRepository.save(new User("import-ops-" + suffix, "导入运营", "not-used", false));
        SalesChannel channel = channelRepository.save(new SalesChannel("IMPORT-" + suffix, "CSV 测试渠道"));
        storeCode = "IMPORT-STORE-" + suffix;
        storeRepository.save(new SalesStore(channel, "import-store-" + suffix, storeCode, "CSV 测试店铺"));
        externalOrderId = "CSV-EXT-" + suffix;
    }

    @Test
    void ownerPreviewsConfirmsAndRepeatedConfirmationIsIdempotent() throws Exception {
        String csv = validCsv();
        MvcResult preview = mockMvc.perform(multipart("/api/order-imports/preview")
                .file(file(csv)).with(actor(owner, "OWNER")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("VALIDATED"))
            .andExpect(jsonPath("$.orderCount").value(1))
            .andExpect(jsonPath("$.itemCount").value(2))
            .andExpect(jsonPath("$.creatableOrderCount").value(1))
            .andExpect(jsonPath("$.errorCount").value(0))
            .andReturn();
        String importId = objectMapper.readTree(preview.getResponse().getContentAsByteArray()).get("importId").asText();

        mockMvc.perform(post("/api/order-imports/{importId}/confirm", importId).with(actor(owner, "OWNER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(post("/api/order-imports/{importId}/confirm", importId).with(actor(owner, "OWNER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales_orders WHERE external_order_id = ?", Integer.class, externalOrderId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sales_order_items item
            JOIN sales_orders orders ON orders.id = item.order_id
            WHERE orders.external_order_id = ?
            """, Integer.class, externalOrderId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM order_status_events event
            JOIN sales_orders orders ON orders.id = event.order_id
            WHERE orders.external_order_id = ? AND event.event_type = 'ORDER_IMPORTED'
            """, Integer.class, externalOrderId)).isEqualTo(1);
    }

    @Test
    void invalidRowsArePagedAndCannotBeConfirmed() throws Exception {
        String badCsv = HEADER + "\n" + String.join(",", List.of(
            storeCode, externalOrderId, "D-1", "PROCESSING", "not-a-time", "", "usd", "", "", "", "", "",
            "USA", "", "", "", "", "", "", "ITEM-1", "", "", "SKU-1", "演示商品", "", "", "0", "-1", ""
        ));
        MvcResult preview = mockMvc.perform(multipart("/api/order-imports/preview")
                .file(file(badCsv)).with(actor(owner, "OWNER")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("INVALID"))
            .andExpect(jsonPath("$.errorCount").value(4))
            .andReturn();
        String importId = objectMapper.readTree(preview.getResponse().getContentAsByteArray()).get("importId").asText();

        mockMvc.perform(get("/api/order-imports/{importId}/errors", importId)
                .param("page", "1").param("size", "2").with(actor(owner, "OWNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(4));
        mockMvc.perform(post("/api/order-imports/{importId}/confirm", importId).with(actor(owner, "OWNER")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_IMPORT_NOT_CONFIRMABLE"));
    }

    @Test
    void nonOwnerCannotPreviewOrReadImports() throws Exception {
        mockMvc.perform(multipart("/api/order-imports/preview")
                .file(file(validCsv())).with(actor(operations, "OPERATIONS")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ORDER_IMPORT_FORBIDDEN"));
    }

    private String validCsv() {
        String common = String.join(",", List.of(
            storeCode, externalOrderId, "D-CSV-1", "PROCESSING", "2026-11-11T02:30:00Z",
            "2026-11-18T02:30:00Z", "USD", owner.getLoginName(), "C-CSV", "演示客户", "Demo Recipient",
            "+15550199", "US", "California", "San Francisco", "", "99 Market Street", "", "94105"
        ));
        return "\uFEFF" + HEADER + "\n"
            + common + ",ITEM-1,PRODUCT-1,VARIANT-1,SKU-1,演示商品一,蓝色,,2,2599,虚构演示\n"
            + common + ",ITEM-2,PRODUCT-2,VARIANT-2,SKU-2,演示商品二,绿色,,1,1999,虚构演示\n";
    }

    private MockMultipartFile file(String content) {
        return new MockMultipartFile("file", "orders.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private JwtRequestPostProcessor actor(User user, String role) {
        return jwt().jwt(token -> token.subject(user.getId().toString()).claim("roles", List.of(role)));
    }
}
