package com.szr.crosserp.inventory;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.szr.crosserp.inventory.model.InventoryQualityStatus;
import com.szr.crosserp.inventory.repository.InventoryLotRepository;
import com.szr.crosserp.inventory.service.InventoryPostingCommandService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InventoryQueryControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired InventoryPostingCommandService postingCommandService;
    @Autowired InventoryLotRepository lotRepository;
    @Autowired UserRepository userRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired SalesChannelRepository channelRepository;
    @Autowired SalesStoreRepository storeRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderItemRepository salesItemRepository;
    @Autowired OrderProcurementRequestRepository requestRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderItemRepository purchaseItemRepository;
    @Autowired PurchaseOrderAllocationRepository purchaseAllocationRepository;
    @Autowired DomesticPackageRepository packageRepository;
    @Autowired GoodsReceiptRepository receiptRepository;
    @Autowired GoodsReceiptItemRepository receiptItemRepository;

    private SalesOrder order;
    private PurchaseOrder purchaseOrder;
    private GoodsReceipt receipt;
    private Long acceptedLotId;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User owner = userRepository.save(new User("inventory-query-" + suffix, "库存查询老板", "not-used", false));
        SalesChannel channel = channelRepository.save(new SalesChannel("IQ-" + suffix, "库存查询渠道"));
        SalesStore store = storeRepository.save(new SalesStore(
            channel, "IQ-STORE-EXT-" + suffix, "IQ-STORE-" + suffix, "库存查询店铺"
        ));
        order = salesOrderRepository.save(new SalesOrder(
            "IQ-SO-" + suffix, store, "IQ-EXT-" + suffix, ExternalOrderStatus.PROCESSING
        ));
        SalesOrderItem salesItem = salesItemRepository.save(new SalesOrderItem(
            order, "IQ-ITEM-" + suffix, "库存查询商品", 3
        ));
        OrderProcurementRequest request = requestRepository.save(new OrderProcurementRequest(
            salesItem, 3, owner, Instant.now()
        ));
        request.convert();
        requestRepository.save(request);
        Supplier supplier = supplierRepository.save(new Supplier(
            "IQ-SUP-" + suffix, "库存查询供应商", SupplierPlatform.MANUAL
        ));
        purchaseOrder = purchaseOrderRepository.save(new PurchaseOrder(
            "IQ-PO-" + suffix, supplier, "CNY", owner
        ));
        PurchaseOrderItem purchaseItem = purchaseItemRepository.save(new PurchaseOrderItem(
            purchaseOrder, "库存查询商品", 3, "CNY"
        ));
        purchaseAllocationRepository.save(new PurchaseOrderAllocation(purchaseItem, request, 3));
        DomesticPackage domesticPackage = packageRepository.save(new DomesticPackage(
            "IQ-PKG-" + suffix, purchaseOrder, "IQ-TRACK-" + suffix, "SF_EXPRESS", owner
        ));
        receipt = receiptRepository.save(new GoodsReceipt(
            "IQ-GR-" + suffix, domesticPackage, owner, Instant.now(),
            "IQ-RCV-" + suffix, "hash-" + suffix, null
        ));
        GoodsReceiptItem receiptItem = receiptItemRepository.save(new GoodsReceiptItem(
            receipt, purchaseItem, 3, 1, "一件破损"
        ));
        postingCommandService.postHistoricalReceipt(receipt.getId(), owner.getId().toString(), List.of("OWNER"));
        acceptedLotId = lotRepository.findByGoodsReceiptItemIdAndQualityStatus(
            receiptItem.getId(), InventoryQualityStatus.ACCEPTED
        ).orElseThrow().getId();
    }

    @Test
    void listsLotsByKeywordPurchaseOrderStatusAndQuality() throws Exception {
        mockMvc.perform(get("/api/inventory/lots")
                .param("keyword", "库存查询商品")
                .param("purchaseOrderId", purchaseOrder.getId().toString())
                .param("status", "ALLOCATED")
                .param("qualityStatus", "ACCEPTED")
                .with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].product.productName").value("库存查询商品"))
            .andExpect(jsonPath("$.items[0].purchaseSource.purchaseOrderNo").value(purchaseOrder.getPurchaseOrderNo()))
            .andExpect(jsonPath("$.items[0].reservedQuantity").value(3));
    }

    @Test
    void detailShowsTraceabilityAllocationsAndNoCustomerSensitiveFields() throws Exception {
        mockMvc.perform(get("/api/inventory/lots/{id}", acceptedLotId).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.receiptSource.receiptNo").value(receipt.getReceiptNo()))
            .andExpect(jsonPath("$.purchaseSource.supplierName").value("库存查询供应商"))
            .andExpect(jsonPath("$.allocations[0].orderNo").value(order.getOrderNo()))
            .andExpect(content().string(not(containsString("recipientPhone"))))
            .andExpect(content().string(not(containsString("addressLine"))))
            .andExpect(content().string(not(containsString("customerName"))));
    }

    @Test
    void pagesMovementsAndAllocations() throws Exception {
        mockMvc.perform(get("/api/inventory/lots/{id}/movements", acceptedLotId)
                .param("page", "1").param("size", "10").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/inventory/allocations")
                .param("orderNo", order.getOrderNo())
                .param("status", "RESERVED").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].lotId").value(acceptedLotId));
    }

    @Test
    void returnsOrderInventoryCoverageWithoutReleasedAllocations() throws Exception {
        mockMvc.perform(get("/api/orders/{id}/inventory-allocations", order.getId()).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderNo").value(order.getOrderNo()))
            .andExpect(jsonPath("$.items[0].requiredQuantity").value(3))
            .andExpect(jsonPath("$.items[0].reservedQuantity").value(3))
            .andExpect(jsonPath("$.items[0].consumedQuantity").value(0))
            .andExpect(jsonPath("$.items[0].shortageQuantity").value(0));
    }

    @Test
    void validatesParametersAndReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/inventory/lots").param("size", "101").with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVENTORY_QUERY_INVALID"));
        mockMvc.perform(get("/api/inventory/lots").param("qualityStatus", "UNKNOWN").with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVENTORY_QUERY_INVALID"));
        mockMvc.perform(get("/api/inventory/lots/{id}", Long.MAX_VALUE).with(jwt()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INVENTORY_LOT_NOT_FOUND"));
    }
}
