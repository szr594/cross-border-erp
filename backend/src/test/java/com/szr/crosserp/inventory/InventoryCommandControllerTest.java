package com.szr.crosserp.inventory;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InventoryCommandControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
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

    @Test
    void warehouseManuallyAllocatesOnlyAgainstMatchingPurchaseSource() throws Exception {
        Fixture fixture = fixture();

        MvcResult allocationResult = mockMvc.perform(post("/api/inventory/lots/{lotId}/allocate", fixture.acceptedLotId())
                .with(actor(fixture.actor(), "WAREHOUSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(allocationBody(fixture.orderItemId(), 2, "manual-allocation", 0)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lotId").value(fixture.acceptedLotId()))
            .andExpect(jsonPath("$.allocationId").isNumber())
            .andExpect(jsonPath("$.allocationStatus").value("RESERVED"))
            .andExpect(jsonPath("$.availableQuantity").value(4))
            .andExpect(jsonPath("$.reservedQuantity").value(2))
            .andExpect(jsonPath("$.movementType").value("ORDER_ALLOCATED"))
            .andReturn();
        JsonNode allocation = objectMapper.readTree(allocationResult.getResponse().getContentAsByteArray());

        mockMvc.perform(post("/api/inventory/lots/{lotId}/adjustments", fixture.acceptedLotId())
                .with(actor(fixture.actor(), "WAREHOUSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustmentBody(
                    "OUT", 5, "reserved-stock-protected", allocation.get("lotVersion").asLong()
                )))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVENTORY_ADJUSTMENT_EXCEEDS_AVAILABLE"));

        mockMvc.perform(get("/api/inventory/lots/{lotId}", fixture.acceptedLotId())
                .with(actor(fixture.actor(), "OPERATIONS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableQuantity").value(4))
            .andExpect(jsonPath("$.reservedQuantity").value(2));
    }

    @Test
    void ownerReleasesReservedAllocationAndRestoresAvailableQuantity() throws Exception {
        Fixture fixture = fixture();
        JsonNode allocation = performAndRead(post("/api/inventory/lots/{lotId}/allocate", fixture.acceptedLotId())
            .with(actor(fixture.actor(), "OWNER"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(allocationBody(fixture.orderItemId(), 2, "release-source", 0)));

        MvcResult releaseResult = mockMvc.perform(post("/api/inventory/allocations/{allocationId}/release", allocation.get("allocationId").asLong())
                .with(actor(fixture.actor(), "OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "订单改配",
                      "idempotencyKey": "release-allocation",
                      "version": %d
                    }
                    """.formatted(allocation.get("allocationVersion").asLong())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allocationStatus").value("RELEASED"))
            .andExpect(jsonPath("$.availableQuantity").value(6))
            .andExpect(jsonPath("$.reservedQuantity").value(0))
            .andExpect(jsonPath("$.movementType").value("ALLOCATION_RELEASED"))
            .andReturn();
        JsonNode released = objectMapper.readTree(releaseResult.getResponse().getContentAsByteArray());

        mockMvc.perform(post("/api/inventory/allocations/{allocationId}/release", allocation.get("allocationId").asLong())
                .with(actor(fixture.actor(), "OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "重复释放",
                      "idempotencyKey": "release-again",
                      "version": %d
                    }
                    """.formatted(released.get("allocationVersion").asLong())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVENTORY_ALLOCATION_NOT_RELEASABLE"));
    }

    @Test
    void warehouseAdjustsOnlyAvailableQuantityAndIdempotencyIsVisibleOverHttp() throws Exception {
        Fixture fixture = fixture();
        String body = adjustmentBody("OUT", 2, "stock-count", 0);

        JsonNode first = performAndRead(post("/api/inventory/lots/{lotId}/adjustments", fixture.acceptedLotId())
            .with(actor(fixture.actor(), "WAREHOUSE"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
        JsonNode retry = performAndRead(post("/api/inventory/lots/{lotId}/adjustments", fixture.acceptedLotId())
            .with(actor(fixture.actor(), "WAREHOUSE"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));

        assertThat(retry.get("movementId").asLong()).isEqualTo(first.get("movementId").asLong());
        assertThat(retry.get("availableQuantity").asInt()).isEqualTo(4);

        mockMvc.perform(post("/api/inventory/lots/{lotId}/adjustments", fixture.acceptedLotId())
                .with(actor(fixture.actor(), "WAREHOUSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustmentBody("OUT", 1, "stock-count", 0)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVENTORY_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void ownerReleasesDamagedQuarantineAndCanThenAllocateIt() throws Exception {
        Fixture fixture = fixture();

        JsonNode released = performAndRead(post(
            "/api/inventory/lots/{lotId}/release-quarantine", fixture.damagedLotId())
            .with(actor(fixture.actor(), "OWNER"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "quantity": 1,
                  "reason": "复检合格",
                  "idempotencyKey": "release-quarantine",
                  "version": 0
                }
                """));

        assertThat(released.get("availableQuantity").asInt()).isEqualTo(1);
        assertThat(released.get("quarantinedQuantity").asInt()).isEqualTo(1);
        assertThat(released.get("movementType").asText()).isEqualTo("QUARANTINE_RELEASED");

        mockMvc.perform(post("/api/inventory/lots/{lotId}/allocate", fixture.damagedLotId())
                .with(actor(fixture.actor(), "OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(allocationBody(
                    fixture.orderItemId(), 1, "allocate-rechecked", released.get("lotVersion").asLong()
                )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableQuantity").value(0))
            .andExpect(jsonPath("$.reservedQuantity").value(1));

        mockMvc.perform(get("/api/inventory/lots/{lotId}", fixture.damagedLotId())
                .with(actor(fixture.actor(), "OPERATIONS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.qualityStatus").value("DAMAGED"));
    }

    @Test
    void nonInventoryRoleCannotExecuteCommands() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(post("/api/inventory/lots/{lotId}/adjustments", fixture.acceptedLotId())
                .with(actor(fixture.actor(), "OPERATIONS"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustmentBody("IN", 1, "forbidden-command", 0)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("INVENTORY_FORBIDDEN"));
    }

    @Test
    void commandResponseNeverExposesCustomerContactFields() throws Exception {
        Fixture fixture = fixture();

        MvcResult result = mockMvc.perform(post(
                "/api/inventory/lots/{lotId}/adjustments", fixture.acceptedLotId())
                .with(actor(fixture.actor(), "OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustmentBody("IN", 1, "safe-response", 0)))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(result.getResponse().getContentAsString())
            .doesNotContainIgnoringCase("phone")
            .doesNotContainIgnoringCase("address");
    }

    private JsonNode performAndRead(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
        throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String allocationBody(Long orderItemId, int quantity, String key, long version) {
        return """
            {
              "orderItemId": %d,
              "quantity": %d,
              "reason": "人工分配",
              "idempotencyKey": "%s",
              "version": %d
            }
            """.formatted(orderItemId, quantity, key, version);
    }

    private String adjustmentBody(String type, int quantity, String key, long version) {
        return """
            {
              "type": "%s",
              "quantity": %d,
              "reason": "库存盘点",
              "idempotencyKey": "%s",
              "version": %d
            }
            """.formatted(type, quantity, key, version);
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User actor = userRepository.save(new User(
            "inventory-command-" + suffix, "库存命令测试", "not-used-in-this-test", false
        ));
        SalesChannel channel = channelRepository.save(new SalesChannel("CMD-CH-" + suffix, "命令测试渠道"));
        SalesStore store = storeRepository.save(new SalesStore(
            channel, "CMD-STORE-EXT-" + suffix, "CMD-STORE-" + suffix, "命令测试店铺"
        ));
        SalesOrder order = orderRepository.save(new SalesOrder(
            "CMD-SO-" + suffix, store, "CMD-ORDER-EXT-" + suffix, ExternalOrderStatus.PROCESSING
        ));
        SalesOrderItem orderItem = orderItemRepository.save(new SalesOrderItem(
            order, "CMD-ITEM-EXT-" + suffix, "命令测试商品", 4
        ));
        OrderProcurementRequest procurementRequest = procurementRequestRepository.save(
            new OrderProcurementRequest(orderItem, 4, actor, Instant.now())
        );
        procurementRequest.convert();
        procurementRequestRepository.save(procurementRequest);
        Supplier supplier = supplierRepository.save(new Supplier(
            "CMD-SUP-" + suffix, "命令测试供应商", SupplierPlatform.MANUAL
        ));
        PurchaseOrder purchaseOrder = purchaseOrderRepository.save(new PurchaseOrder(
            "CMD-PO-" + suffix, supplier, "CNY", actor
        ));
        PurchaseOrderItem purchaseOrderItem = purchaseOrderItemRepository.save(new PurchaseOrderItem(
            purchaseOrder, "命令测试商品", 8, "CNY"
        ));
        purchaseAllocationRepository.save(new PurchaseOrderAllocation(
            purchaseOrderItem, procurementRequest, 4
        ));
        DomesticPackage domesticPackage = packageRepository.save(new DomesticPackage(
            "CMD-PKG-" + suffix, purchaseOrder, "CMD-TRACK-" + suffix, "SF_EXPRESS", actor
        ));
        GoodsReceipt receipt = receiptRepository.save(new GoodsReceipt(
            "CMD-GR-" + suffix, domesticPackage, actor, Instant.now(),
            "CMD-RCV-" + suffix, "hash-" + suffix, null
        ));
        GoodsReceiptItem receiptItem = receiptItemRepository.saveAndFlush(new GoodsReceiptItem(
            receipt, purchaseOrderItem, 6, 2, "含复检库存"
        ));
        InventoryLot acceptedLot = lotRepository.save(new InventoryLot(
            "CMD-LOT-A-" + suffix, receiptItem, purchaseOrderItem,
            InventoryQualityStatus.ACCEPTED, 6, Instant.now()
        ));
        InventoryLot damagedLot = lotRepository.saveAndFlush(new InventoryLot(
            "CMD-LOT-D-" + suffix, receiptItem, purchaseOrderItem,
            InventoryQualityStatus.DAMAGED, 2, Instant.now()
        ));
        return new Fixture(actor, orderItem.getId(), acceptedLot.getId(), damagedLot.getId());
    }

    private JwtRequestPostProcessor actor(User user, String role) {
        return jwt().jwt(token -> token.subject(user.getId().toString()).claim("roles", List.of(role)));
    }

    private record Fixture(User actor, Long orderItemId, Long acceptedLotId, Long damagedLotId) {
    }
}
