package com.szr.crosserp.order;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderQueryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired SalesChannelRepository channelRepository;
    @Autowired SalesStoreRepository storeRepository;

    private Long requestedOrderId;

    @BeforeEach
    void setUp() {
        Role ownerRole = roleRepository.findByCode("OWNER").orElseGet(() -> roleRepository.save(new Role("OWNER", "老板")));
        User owner = new User("order-owner", "订单负责人", "not-used", false);
        owner.addRole(ownerRole);
        owner = userRepository.save(owner);

        SalesChannel channel = channelRepository.save(new SalesChannel("TEST", "测试渠道"));
        SalesStore store = storeRepository.save(new SalesStore(channel, "external-test", "TEST-STORE", "测试店铺"));
        Instant now = Instant.parse("2026-10-30T02:00:00Z");

        requestedOrderId = insertOrder(
            "ERP-1001", "EXT-1001", "DISPLAY-1001", store.getId(), owner.getId(),
            "PROCESSING", false, now, "13800138000", "浙江省", "杭州市", "西湖区",
            "演示路 1 号", "A 座", "310000"
        );
        Long requestedItemId = insertItem(requestedOrderId, "ITEM-1001", "SKU-DEMO-RED", "演示红色商品", 3);
        jdbcTemplate.update("""
            INSERT INTO order_procurement_requests(
                order_item_id, requested_quantity, status, requested_by, requested_at, version
            ) VALUES (?, 2, 'REQUESTED', ?, ?, 0)
            """, requestedItemId, owner.getId(), now);
        jdbcTemplate.update("""
            INSERT INTO order_status_events(
                order_id, event_type, from_value, to_value, operator_id, source, remark, occurred_at
            ) VALUES (?, 'ORDER_IMPORTED', NULL, 'PROCESSING', ?, 'IMPORT', '演示导入', ?)
            """, requestedOrderId, owner.getId(), now);
        jdbcTemplate.update("""
            INSERT INTO order_status_events(
                order_id, event_type, from_value, to_value, operator_id, source, remark, occurred_at
            ) VALUES (?, 'PROCUREMENT_REQUESTED', '0', '2', ?, 'USER', '库存不足', ?)
            """, requestedOrderId, owner.getId(), now.plusSeconds(10));

        Long canceledOrderId = insertOrder(
            "ERP-1002", "EXT-1002", "DISPLAY-1002", store.getId(), owner.getId(),
            "CANCELED", false, now.plusSeconds(60), "13900139000", "江苏省", "南京市", "玄武区",
            "演示路 2 号", null, "210000"
        );
        insertItem(canceledOrderId, "ITEM-1002", "SKU-CANCELED", "已取消商品", 1);

        Long exceptionOrderId = insertOrder(
            "ERP-1003", "EXT-1003", "DISPLAY-1003", store.getId(), owner.getId(),
            "PROCESSING", true, now.plusSeconds(120), "13700137000", "广东省", "深圳市", "南山区",
            "演示路 3 号", null, "518000"
        );
        insertItem(exceptionOrderId, "ITEM-1003", "SKU-EXCEPTION", "异常商品", 1);
    }

    @Test
    void listsActionableOrdersWithKeywordAndStageSummaryWithoutSensitiveFields() throws Exception {
        mockMvc.perform(get("/api/orders")
                .param("keyword", "红色")
                .with(actor("OPERATIONS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].orderNo").value("ERP-1001"))
            .andExpect(jsonPath("$.items[0].stage").value("WAITING_PROCUREMENT"))
            .andExpect(jsonPath("$.items[0].stageName").value("待采购"))
            .andExpect(jsonPath("$.items[0].recipientPhone").doesNotExist())
            .andExpect(jsonPath("$.items[0].addressLine1").doesNotExist())
            .andExpect(jsonPath("$.summary.waitingProcurement").value(1));
    }

    @Test
    void defaultActionableFilterExcludesCanceledAndSupportsUnsupportedStageAsEmpty() throws Exception {
        mockMvc.perform(get("/api/orders").with(actor("WAREHOUSE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/orders")
                .param("stage", "READY_FOR_PACKING")
                .with(actor("WAREHOUSE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void procurementDetailOmitsPhoneAndEveryDetailedAddressField() throws Exception {
        mockMvc.perform(get("/api/orders/{orderId}", requestedOrderId).with(actor("PROCUREMENT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customer.recipientPhone").doesNotExist())
            .andExpect(jsonPath("$.customer.province").doesNotExist())
            .andExpect(jsonPath("$.customer.city").doesNotExist())
            .andExpect(jsonPath("$.customer.district").doesNotExist())
            .andExpect(jsonPath("$.customer.addressLine1").doesNotExist())
            .andExpect(jsonPath("$.customer.addressLine2").doesNotExist())
            .andExpect(jsonPath("$.customer.postalCode").doesNotExist())
            .andExpect(jsonPath("$.sourcePayload").doesNotExist())
            .andExpect(jsonPath("$.items[0].procurementRequestedQuantity").value(2))
            .andExpect(jsonPath("$.availableActions[0]").value("REQUEST_PROCUREMENT"))
            .andExpect(jsonPath("$.availableActions[1]").value("OPEN_EXCEPTION"));
    }

    @Test
    void ownerDetailIncludesContactDataAndOwnerActions() throws Exception {
        mockMvc.perform(get("/api/orders/{orderId}", requestedOrderId).with(actor("OWNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customer.recipientPhone").value("13800138000"))
            .andExpect(jsonPath("$.customer.addressLine1").value("演示路 1 号"))
            .andExpect(jsonPath("$.availableActions[0]").value("CHANGE_RESPONSIBLE"))
            .andExpect(jsonPath("$.availableActions[1]").value("CANCEL"));
    }

    @Test
    void ignoresProcurementHistoryForCanceledOrderItemsWhenCalculatingStage() throws Exception {
        jdbcTemplate.update(
            "UPDATE sales_order_items SET fulfillment_status = 'CANCELED' WHERE order_id = ?",
            requestedOrderId
        );

        mockMvc.perform(get("/api/orders/{orderId}", requestedOrderId).with(actor("OPERATIONS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stage").value("PENDING"));

        mockMvc.perform(get("/api/orders")
                .param("keyword", "ERP-1001")
                .param("stage", "WAITING_PROCUREMENT")
                .with(actor("OPERATIONS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void validatesPaginationAndRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/orders").param("size", "101").with(actor("OWNER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ORDER_QUERY_INVALID"));

        mockMvc.perform(get("/api/orders"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void returnsFilterOptionsForAuthenticatedEmployees() throws Exception {
        mockMvc.perform(get("/api/orders/filter-options").with(actor("OPERATIONS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stores[0].code").value("TEST-STORE"))
            .andExpect(jsonPath("$.responsibleUsers[*].loginName").value(hasItem("order-owner")))
            .andExpect(jsonPath("$.externalStatuses[0].value").value("NEW"))
            .andExpect(jsonPath("$.fulfillmentStages[0].value").value("ACTIONABLE"));
    }

    @Test
    void returnsIndependentFulfillmentProjectionWithoutCustomerData() throws Exception {
        mockMvc.perform(get("/api/orders/{orderId}/fulfillment", requestedOrderId)
                .with(actor("PROCUREMENT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stage").value("WAITING_PROCUREMENT"))
            .andExpect(jsonPath("$.items[0].procurementRequestedQuantity").value(2))
            .andExpect(jsonPath("$.customer").doesNotExist())
            .andExpect(jsonPath("$.recipientPhone").doesNotExist());
    }

    @Test
    void returnsOrderEventsNewestFirstWithPagination() throws Exception {
        mockMvc.perform(get("/api/orders/{orderId}/events", requestedOrderId)
                .param("page", "1")
                .param("size", "1")
                .with(actor("WAREHOUSE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].eventType").value("PROCUREMENT_REQUESTED"))
            .andExpect(jsonPath("$.items[0].operator.loginName").value("order-owner"))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/orders/{orderId}/events", requestedOrderId)
                .with(actor("PROCUREMENT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].remark").doesNotExist())
            .andExpect(jsonPath("$.items[0].fromValue").doesNotExist())
            .andExpect(jsonPath("$.items[0].toValue").doesNotExist());
    }

    private Long insertOrder(
        String orderNo,
        String externalOrderId,
        String displayOrderNo,
        Long storeId,
        Long userId,
        String status,
        boolean exception,
        Instant orderedAt,
        String phone,
        String province,
        String city,
        String district,
        String addressLine1,
        String addressLine2,
        String postalCode
    ) {
        jdbcTemplate.update("""
            INSERT INTO sales_orders(
                order_no, store_id, external_order_id, display_order_no, external_status,
                fulfillment_exception, exception_reason, responsible_user_id, currency,
                ordered_at, requested_ship_at, customer_id, customer_name, recipient_name,
                recipient_phone, country_code, province, city, district, address_line1,
                address_line2, postal_code, note, version, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'USD', ?, ?, 'CUSTOMER-1', '演示客户', '演示收件人',
                ?, 'CN', ?, ?, ?, ?, ?, ?, '演示备注', 0, ?, ?)
            """,
            orderNo, storeId, externalOrderId, displayOrderNo, status, exception,
            exception ? "需要人工处理" : null, userId, orderedAt, orderedAt.plusSeconds(86400),
            phone, province, city, district, addressLine1, addressLine2, postalCode, orderedAt, orderedAt
        );
        return jdbcTemplate.queryForObject("SELECT id FROM sales_orders WHERE order_no = ?", Long.class, orderNo);
    }

    private Long insertItem(Long orderId, String externalItemId, String sku, String productName, int quantity) {
        jdbcTemplate.update("""
            INSERT INTO sales_order_items(
                order_id, external_item_id, external_sku, product_name, quantity, currency,
                fulfillment_status, version, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, 'USD', 'NORMAL', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, orderId, externalItemId, sku, productName, quantity);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM sales_order_items WHERE order_id = ? AND external_item_id = ?",
            Long.class,
            orderId,
            externalItemId
        );
    }

    private JwtRequestPostProcessor actor(String role) {
        return jwt().jwt(token -> token.subject("1").claim("roles", List.of(role)));
    }
}
