package com.szr.crosserp.order;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.szr.crosserp.store.model.SalesChannel;
import com.szr.crosserp.store.model.SalesStore;
import com.szr.crosserp.store.repository.SalesChannelRepository;
import com.szr.crosserp.store.repository.SalesStoreRepository;
import com.szr.crosserp.user.model.Role;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.RoleRepository;
import com.szr.crosserp.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderCommandControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired SalesChannelRepository channelRepository;
    @Autowired SalesStoreRepository storeRepository;

    private User owner;
    private User operations;
    private User procurement;
    private User warehouse;
    private User replacement;
    private Long orderId;
    private Long firstItemId;
    private Long secondItemId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = user("cmd-owner-" + suffix, "命令老板", "OWNER");
        operations = user("cmd-ops-" + suffix, "命令运营", "OPERATIONS");
        procurement = user("cmd-buy-" + suffix, "命令采购", "PROCUREMENT");
        warehouse = user("cmd-warehouse-" + suffix, "命令仓库", "WAREHOUSE");
        replacement = user("cmd-replacement-" + suffix, "新负责人", "OPERATIONS");
        SalesChannel channel = channelRepository.save(new SalesChannel("CMD-" + suffix, "命令测试渠道"));
        SalesStore store = storeRepository.save(new SalesStore(channel, "cmd-" + suffix, "CMD-STORE-" + suffix, "命令店铺"));
        Instant now = Instant.parse("2026-11-02T01:00:00Z");
        jdbcTemplate.update("""
            INSERT INTO sales_orders(
                order_no, store_id, external_order_id, external_status, fulfillment_exception,
                responsible_user_id, currency, ordered_at, requested_ship_at, version, created_at, updated_at
            ) VALUES (?, ?, ?, 'PROCESSING', FALSE, ?, 'USD', ?, ?, 0, ?, ?)
            """, "CMD-ORDER-" + suffix, store.getId(), "CMD-EXT-" + suffix, operations.getId(),
            now, now.plusSeconds(86400), now, now);
        orderId = jdbcTemplate.queryForObject("SELECT id FROM sales_orders WHERE external_order_id = ?", Long.class, "CMD-EXT-" + suffix);
        firstItemId = insertItem("CMD-ITEM-1-" + suffix, "CMD-SKU-1", 5);
        secondItemId = insertItem("CMD-ITEM-2-" + suffix, "CMD-SKU-2", 3);
    }

    @Test
    void ownerChangesResponsibleUserAndCreatesAuditEvent() throws Exception {
        mockMvc.perform(put("/api/orders/{orderId}/responsible-user", orderId)
                .with(actor(owner, "OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"userId": %d, "reason": "运营排班调整", "version": 0}
                    """.formatted(replacement.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.responsibleUser.loginName").value(replacement.getLoginName()))
            .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put("/api/orders/{orderId}/responsible-user", orderId)
                .with(actor(operations, "OPERATIONS"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"userId": %d, "reason": "越权尝试", "version": 1}
                    """.formatted(operations.getId())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ORDER_COMMAND_FORBIDDEN"));

        List<String> eventTypes = jdbcTemplate.queryForList(
            "SELECT event_type FROM order_status_events WHERE order_id = ?", String.class, orderId
        );
        org.assertj.core.api.Assertions.assertThat(eventTypes).containsExactly("RESPONSIBLE_CHANGED");
    }

    @Test
    void createsProcurementRequestsAtomicallyAndRejectsQuantityBeyondGap() throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/procurement-requests", orderId)
                .with(actor(procurement, "PROCUREMENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items":[{"orderItemId":%d,"quantity":4},{"orderItemId":%d,"quantity":2}],"note":"库存不足","version":0}
                    """.formatted(firstItemId, secondItemId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.requests.length()").value(2))
            .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(post("/api/orders/{orderId}/procurement-requests", orderId)
                .with(actor(operations, "OPERATIONS"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items":[{"orderItemId":%d,"quantity":2},{"orderItemId":%d,"quantity":1}],"note":"超过缺口","version":1}
                    """.formatted(firstItemId, secondItemId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROCUREMENT_GAP_CHANGED"));

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_procurement_requests WHERE order_item_id IN (?, ?)",
            Integer.class, firstItemId, secondItemId
        );
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(2);
    }

    @Test
    void withdrawsRequestedProcurementButNotConvertedProcurement() throws Exception {
        Long requestedId = insertRequest(firstItemId, "REQUESTED", procurement.getId());
        Long convertedId = insertRequest(secondItemId, "CONVERTED", procurement.getId());

        mockMvc.perform(post("/api/orders/{orderId}/procurement-requests/{requestId}/withdraw", orderId, requestedId)
                .with(actor(operations, "OPERATIONS"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"改用现货\",\"version\":0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WITHDRAWN"))
            .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(post("/api/orders/{orderId}/procurement-requests/{requestId}/withdraw", orderId, convertedId)
                .with(actor(procurement, "PROCUREMENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"错误撤回\",\"version\":0}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROCUREMENT_ALREADY_CONVERTED"));
    }

    @Test
    void opensAndResolvesExceptionWithOptimisticVersion() throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/exceptions", orderId)
                .with(actor(warehouse, "WAREHOUSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"发现商品规格不一致\",\"version\":0}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.exception").value(true))
            .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(post("/api/orders/{orderId}/exceptions/resolve", orderId)
                .with(actor(operations, "OPERATIONS"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"已与客户确认继续履约\",\"version\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exception").value(false))
            .andExpect(jsonPath("$.version").value(2));

        List<String> events = jdbcTemplate.queryForList(
            "SELECT event_type FROM order_status_events WHERE order_id = ? ORDER BY id", String.class, orderId
        );
        org.assertj.core.api.Assertions.assertThat(events).containsExactly("EXCEPTION_OPENED", "EXCEPTION_RESOLVED");
    }

    @Test
    void onlyOwnerCancelsAndConvertedProcurementBlocksCancellation() throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                .with(actor(operations, "OPERATIONS"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"越权取消\",\"version\":0}"))
            .andExpect(status().isForbidden());

        insertRequest(firstItemId, "CONVERTED", procurement.getId());
        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                .with(actor(owner, "OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"外部订单取消\",\"version\":0}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_CANCEL_BLOCKED"));
    }

    @Test
    void ownerCancelsOrderAndPendingRequestsAreWithdrawn() throws Exception {
        insertRequest(firstItemId, "REQUESTED", procurement.getId());

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                .with(actor(owner, "OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"外部订单取消\",\"version\":0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.externalStatus").value("CANCELED"))
            .andExpect(jsonPath("$.version").value(1));

        List<String> statuses = jdbcTemplate.queryForList(
            "SELECT status FROM order_procurement_requests WHERE order_item_id = ?", String.class, firstItemId
        );
        org.assertj.core.api.Assertions.assertThat(statuses).containsExactly("WITHDRAWN");
    }

    @Test
    void staleOrderVersionReturnsConflictWithoutMutation() throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/exceptions", orderId)
                .with(actor(operations, "OPERATIONS"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"过期请求\",\"version\":9}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_VERSION_CONFLICT"));

        Boolean exception = jdbcTemplate.queryForObject(
            "SELECT fulfillment_exception FROM sales_orders WHERE id = ?", Boolean.class, orderId
        );
        org.assertj.core.api.Assertions.assertThat(exception).isFalse();
    }

    private User user(String login, String name, String roleCode) {
        Role role = roleRepository.findByCode(roleCode)
            .orElseGet(() -> roleRepository.save(new Role(roleCode, roleCode)));
        User user = new User(login, name, "not-used", false);
        user.addRole(role);
        return userRepository.save(user);
    }

    private Long insertItem(String externalItemId, String sku, int quantity) {
        jdbcTemplate.update("""
            INSERT INTO sales_order_items(
                order_id, external_item_id, external_sku, product_name, quantity, currency,
                fulfillment_status, version, created_at, updated_at
            ) VALUES (?, ?, ?, '命令测试商品', ?, 'USD', 'NORMAL', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, orderId, externalItemId, sku, quantity);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM sales_order_items WHERE order_id = ? AND external_item_id = ?",
            Long.class, orderId, externalItemId
        );
    }

    private Long insertRequest(Long itemId, String status, Long actorId) {
        jdbcTemplate.update("""
            INSERT INTO order_procurement_requests(
                order_item_id, requested_quantity, status, requested_by, requested_at, version
            ) VALUES (?, 1, ?, ?, CURRENT_TIMESTAMP, 0)
            """, itemId, status, actorId);
        return jdbcTemplate.queryForObject(
            "SELECT MAX(id) FROM order_procurement_requests WHERE order_item_id = ?",
            Long.class, itemId
        );
    }

    private JwtRequestPostProcessor actor(User user, String role) {
        return jwt().jwt(token -> token.subject(user.getId().toString()).claim("roles", List.of(role)));
    }
}
