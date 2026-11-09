package com.szr.crosserp.order;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szr.crosserp.inventory.model.InventoryLot;
import com.szr.crosserp.inventory.model.InventoryQualityStatus;
import com.szr.crosserp.inventory.repository.InventoryLotRepository;
import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.model.OrderProcurementRequest;
import com.szr.crosserp.order.model.SalesOrder;
import com.szr.crosserp.order.model.SalesOrderItem;
import com.szr.crosserp.order.repository.OrderProcurementRequestRepository;
import com.szr.crosserp.order.repository.SalesOrderItemRepository;
import com.szr.crosserp.order.repository.SalesOrderRepository;
import com.szr.crosserp.procurement.model.PurchaseOrder;
import com.szr.crosserp.procurement.model.PurchaseOrderAllocation;
import com.szr.crosserp.procurement.model.PurchaseOrderItem;
import com.szr.crosserp.procurement.model.Supplier;
import com.szr.crosserp.procurement.model.SupplierPlatform;
import com.szr.crosserp.procurement.repository.PurchaseOrderAllocationRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderRepository;
import com.szr.crosserp.procurement.repository.SupplierRepository;
import com.szr.crosserp.receiving.model.DomesticPackage;
import com.szr.crosserp.receiving.model.GoodsReceipt;
import com.szr.crosserp.receiving.model.GoodsReceiptItem;
import com.szr.crosserp.receiving.repository.DomesticPackageRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptItemRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptRepository;
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
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderCancellationInventoryControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired SalesChannelRepository channelRepository;
    @Autowired SalesStoreRepository storeRepository;
    @Autowired SalesOrderRepository orderRepository;
    @Autowired SalesOrderItemRepository orderItemRepository;
    @Autowired OrderProcurementRequestRepository procurementRequestRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Autowired PurchaseOrderAllocationRepository purchaseAllocationRepository;
    @Autowired DomesticPackageRepository packageRepository;
    @Autowired GoodsReceiptRepository receiptRepository;
    @Autowired GoodsReceiptItemRepository receiptItemRepository;
    @Autowired InventoryLotRepository lotRepository;
    @PersistenceContext EntityManager entityManager;

    @Test
    void ownerCancellationReleasesReservedInventoryAndKeepsConvertedProcurement() throws Exception {
        Fixture fixture = fixture();
        allocate(fixture);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", fixture.orderId())
                .with(actor(fixture.owner()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"客户取消\",\"version\":0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.externalStatus").value("CANCELED"));

        mockMvc.perform(get("/api/inventory/lots/{lotId}", fixture.lotId()).with(actor(fixture.owner())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableQuantity").value(3))
            .andExpect(jsonPath("$.reservedQuantity").value(0))
            .andExpect(jsonPath("$.allocations[0].status").value("RELEASED"));
        mockMvc.perform(get("/api/inventory/lots/{lotId}/movements", fixture.lotId())
                .param("page", "1").param("size", "10").with(actor(fixture.owner())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].movementType").value("ALLOCATION_RELEASED"));

        String requestStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM order_procurement_requests WHERE id = ?",
            String.class, fixture.procurementRequestId()
        );
        org.assertj.core.api.Assertions.assertThat(requestStatus).isEqualTo("CONVERTED");
    }

    @Test
    void consumedInventoryBlocksCancellationWithoutPartialMutation() throws Exception {
        Fixture fixture = fixture();
        Long allocationId = allocate(fixture);
        jdbcTemplate.update(
            "UPDATE inventory_allocations SET status = 'CONSUMED' WHERE id = ?", allocationId
        );
        entityManager.clear();

        mockMvc.perform(post("/api/orders/{orderId}/cancel", fixture.orderId())
                .with(actor(fixture.owner()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"客户取消\",\"version\":0}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_CANCEL_BLOCKED_BY_CONSUMED_INVENTORY"));

        mockMvc.perform(get("/api/inventory/lots/{lotId}", fixture.lotId()).with(actor(fixture.owner())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reservedQuantity").value(3))
            .andExpect(jsonPath("$.allocations[0].status").value("CONSUMED"));
        String orderStatus = jdbcTemplate.queryForObject(
            "SELECT external_status FROM sales_orders WHERE id = ?", String.class, fixture.orderId()
        );
        org.assertj.core.api.Assertions.assertThat(orderStatus).isEqualTo("PROCESSING");
    }

    private Long allocate(Fixture fixture) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/inventory/lots/{lotId}/allocate", fixture.lotId())
                .with(actor(fixture.owner()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "orderItemId": %d,
                      "quantity": 3,
                      "reason": "取消测试预留",
                      "idempotencyKey": "cancel-allocation-%s",
                      "version": 0
                    }
                    """.formatted(fixture.orderItemId(), fixture.suffix())))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return response.get("allocationId").asLong();
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User owner = userRepository.save(new User(
            "cancel-owner-" + suffix, "取消测试老板", "not-used", false
        ));
        SalesChannel channel = channelRepository.save(new SalesChannel("CANCEL-CH-" + suffix, "取消渠道"));
        SalesStore store = storeRepository.save(new SalesStore(
            channel, "CANCEL-STORE-EXT-" + suffix, "CANCEL-STORE-" + suffix, "取消店铺"
        ));
        SalesOrder order = orderRepository.save(new SalesOrder(
            "CANCEL-SO-" + suffix, store, "CANCEL-EXT-" + suffix, ExternalOrderStatus.PROCESSING
        ));
        SalesOrderItem orderItem = orderItemRepository.save(new SalesOrderItem(
            order, "CANCEL-ITEM-" + suffix, "取消测试商品", 3
        ));
        OrderProcurementRequest request = procurementRequestRepository.save(new OrderProcurementRequest(
            orderItem, 3, owner, Instant.now()
        ));
        request.convert();
        procurementRequestRepository.save(request);
        Supplier supplier = supplierRepository.save(new Supplier(
            "CANCEL-SUP-" + suffix, "取消测试供应商", SupplierPlatform.MANUAL
        ));
        PurchaseOrder purchaseOrder = purchaseOrderRepository.save(new PurchaseOrder(
            "CANCEL-PO-" + suffix, supplier, "CNY", owner
        ));
        PurchaseOrderItem purchaseItem = purchaseOrderItemRepository.save(new PurchaseOrderItem(
            purchaseOrder, "取消测试商品", 3, "CNY"
        ));
        purchaseAllocationRepository.save(new PurchaseOrderAllocation(purchaseItem, request, 3));
        DomesticPackage domesticPackage = packageRepository.save(new DomesticPackage(
            "CANCEL-PKG-" + suffix, purchaseOrder, "CANCEL-TRACK-" + suffix, "SF_EXPRESS", owner
        ));
        GoodsReceipt receipt = receiptRepository.save(new GoodsReceipt(
            "CANCEL-GR-" + suffix, domesticPackage, owner, Instant.now(),
            "CANCEL-RCV-" + suffix, "hash-" + suffix, null
        ));
        GoodsReceiptItem receiptItem = receiptItemRepository.save(new GoodsReceiptItem(
            receipt, purchaseItem, 3, 0, null
        ));
        InventoryLot lot = lotRepository.saveAndFlush(new InventoryLot(
            "CANCEL-LOT-" + suffix, receiptItem, purchaseItem,
            InventoryQualityStatus.ACCEPTED, 3, Instant.now()
        ));
        return new Fixture(
            suffix, owner, order.getId(), orderItem.getId(), request.getId(), lot.getId()
        );
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor actor(
        User owner
    ) {
        return jwt().jwt(token -> token.subject(owner.getId().toString()).claim("roles", List.of("OWNER")));
    }

    private record Fixture(
        String suffix, User owner, Long orderId, Long orderItemId,
        Long procurementRequestId, Long lotId
    ) {
    }
}
