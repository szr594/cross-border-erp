package com.szr.crosserp.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.model.OrderProcurementRequest;
import com.szr.crosserp.order.model.SalesOrder;
import com.szr.crosserp.order.model.SalesOrderItem;
import com.szr.crosserp.order.repository.OrderProcurementRequestRepository;
import com.szr.crosserp.order.repository.SalesOrderItemRepository;
import com.szr.crosserp.order.repository.SalesOrderRepository;
import com.szr.crosserp.procurement.model.Supplier;
import com.szr.crosserp.procurement.model.SupplierPlatform;
import com.szr.crosserp.procurement.repository.SupplierRepository;
import com.szr.crosserp.store.model.SalesChannel;
import com.szr.crosserp.store.model.SalesStore;
import com.szr.crosserp.store.repository.SalesChannelRepository;
import com.szr.crosserp.store.repository.SalesStoreRepository;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
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
class PurchaseOrderCommandControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired SalesChannelRepository channelRepository;
    @Autowired SalesStoreRepository storeRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderItemRepository salesOrderItemRepository;
    @Autowired OrderProcurementRequestRepository requestRepository;
    @Autowired EntityManager entityManager;

    @Test
    void ownerCreatesDraftAndAggregatesCompleteRequestsIntoOneItem() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(post("/api/purchase-orders")
                .with(actor(fixture.buyer(), "OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(command(fixture, fixture.first().getRequestedQuantity(), fixture.second().getRequestedQuantity())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.supplierId").value(fixture.supplier().getId()))
            .andExpect(jsonPath("$.items[0].purchasedQuantity").value(5))
            .andExpect(jsonPath("$.items[0].allocations.length()").value(2))
            .andExpect(jsonPath("$.totalAmountMinor").value(5000));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM purchase_orders WHERE supplier_id = ?", Integer.class, fixture.supplier().getId()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
            "SELECT status FROM order_procurement_requests WHERE id IN (?, ?) ORDER BY id",
            String.class, fixture.first().getId(), fixture.second().getId()
        )).containsExactly("CONVERTED", "CONVERTED");
        assertThat(jdbcTemplate.queryForList(
            "SELECT event_type FROM purchase_order_events", String.class
        )).containsExactly("CREATED");
    }

    @Test
    void rejectsPartialAllocationAndRollsBackEverything() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(post("/api/purchase-orders")
                .with(actor(fixture.buyer(), "PROCUREMENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(command(fixture, 1, fixture.second().getRequestedQuantity())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PURCHASE_ALLOCATION_INVALID"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM purchase_orders", Integer.class)).isZero();
        assertThat(requestRepository.findById(fixture.first().getId()).orElseThrow().getStatus().name())
            .isEqualTo("REQUESTED");
    }

    @Test
    void operationsCannotCreateAndInactiveSupplierIsRejected() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(post("/api/purchase-orders")
                .with(actor(fixture.buyer(), "OPERATIONS"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(command(fixture, 2, 3)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_COMMAND_FORBIDDEN"));

        jdbcTemplate.update("UPDATE suppliers SET status = 'INACTIVE' WHERE id = ?", fixture.supplier().getId());
        entityManager.clear();
        mockMvc.perform(post("/api/purchase-orders")
                .with(actor(fixture.buyer(), "PROCUREMENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(command(fixture, 2, 3)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SUPPLIER_INACTIVE"));
    }

    @Test
    void convertedOrStaleRequestRejectsWholeCommand() throws Exception {
        Fixture fixture = fixture();
        jdbcTemplate.update("UPDATE order_procurement_requests SET status = 'CONVERTED' WHERE id = ?", fixture.first().getId());
        entityManager.clear();

        mockMvc.perform(post("/api/purchase-orders")
                .with(actor(fixture.buyer(), "OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(command(fixture, 2, 3)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROCUREMENT_REQUEST_UNAVAILABLE"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM purchase_orders", Integer.class)).isZero();
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User buyer = userRepository.save(new User("po-" + suffix, "采购员", "not-used", false));
        Supplier supplier = supplierRepository.save(new Supplier("SUP-" + suffix, "采购供应商", SupplierPlatform.MANUAL));
        SalesChannel channel = channelRepository.save(new SalesChannel("PO-" + suffix, "采购渠道"));
        SalesStore store = storeRepository.save(new SalesStore(channel, "EXT-" + suffix, "STORE-" + suffix, "采购店铺"));
        SalesOrder order = salesOrderRepository.save(new SalesOrder("ERP-" + suffix, store, "ORDER-" + suffix, ExternalOrderStatus.NEW));
        SalesOrderItem firstItem = salesOrderItemRepository.save(new SalesOrderItem(order, "ITEM-A-" + suffix, "商品A", 2));
        SalesOrderItem secondItem = salesOrderItemRepository.save(new SalesOrderItem(order, "ITEM-B-" + suffix, "商品B", 3));
        OrderProcurementRequest first = requestRepository.save(new OrderProcurementRequest(firstItem, 2, buyer, Instant.now()));
        OrderProcurementRequest second = requestRepository.save(new OrderProcurementRequest(secondItem, 3, buyer, Instant.now()));
        return new Fixture(buyer, supplier, first, second);
    }

    private String command(Fixture fixture, int firstQuantity, int secondQuantity) {
        return """
            {"supplierId":%d,"currency":"CNY","note":"合并采购","items":[{
              "supplierProductId":"SKU-PRODUCT","supplierSku":"SKU-WHITE-L","productName":"合并采购商品",
              "variantName":"白色/L","productUrl":"https://example.test/product","imageUrl":null,
              "unitPriceMinor":1000,"allocations":[
                {"procurementRequestId":%d,"quantity":%d,"version":0},
                {"procurementRequestId":%d,"quantity":%d,"version":0}
              ]}]}
            """.formatted(
                fixture.supplier().getId(), fixture.first().getId(), firstQuantity,
                fixture.second().getId(), secondQuantity
            );
    }

    private JwtRequestPostProcessor actor(User user, String role) {
        return jwt().jwt(token -> token.subject(user.getId().toString()).claim("roles", List.of(role)));
    }

    private record Fixture(
        User buyer, Supplier supplier, OrderProcurementRequest first, OrderProcurementRequest second
    ) {
    }
}
