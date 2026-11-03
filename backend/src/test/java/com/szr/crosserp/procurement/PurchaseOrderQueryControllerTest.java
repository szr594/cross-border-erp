package com.szr.crosserp.procurement;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.model.OrderProcurementRequest;
import com.szr.crosserp.order.model.SalesOrder;
import com.szr.crosserp.order.model.SalesOrderItem;
import com.szr.crosserp.order.repository.OrderProcurementRequestRepository;
import com.szr.crosserp.order.repository.SalesOrderItemRepository;
import com.szr.crosserp.order.repository.SalesOrderRepository;
import com.szr.crosserp.procurement.model.PurchaseOrder;
import com.szr.crosserp.procurement.model.PurchaseOrderAllocation;
import com.szr.crosserp.procurement.model.PurchaseOrderEvent;
import com.szr.crosserp.procurement.model.PurchaseOrderItem;
import com.szr.crosserp.procurement.model.Supplier;
import com.szr.crosserp.procurement.model.SupplierPlatform;
import com.szr.crosserp.procurement.repository.PurchaseOrderAllocationRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderEventRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderRepository;
import com.szr.crosserp.procurement.repository.SupplierRepository;
import com.szr.crosserp.store.model.SalesChannel;
import com.szr.crosserp.store.model.SalesStore;
import com.szr.crosserp.store.repository.SalesChannelRepository;
import com.szr.crosserp.store.repository.SalesStoreRepository;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.UserRepository;
import java.time.Instant;
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
class PurchaseOrderQueryControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired SalesChannelRepository channelRepository;
    @Autowired SalesStoreRepository storeRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderItemRepository salesItemRepository;
    @Autowired OrderProcurementRequestRepository requestRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderItemRepository purchaseItemRepository;
    @Autowired PurchaseOrderAllocationRepository allocationRepository;
    @Autowired PurchaseOrderEventRepository eventRepository;

    private PurchaseOrder purchaseOrder;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User buyer = userRepository.save(new User("query-" + suffix, "查询采购员", "not-used", false));
        supplier = supplierRepository.save(new Supplier(
            "SUP-" + suffix, "深圳查询供应商", SupplierPlatform.MANUAL,
            null, "供应商联系人", "13800138000", null, null
        ));
        SalesChannel channel = channelRepository.save(new SalesChannel("PQ-" + suffix, "查询渠道"));
        SalesStore store = storeRepository.save(new SalesStore(channel, "EXT-" + suffix, "STORE-" + suffix, "查询店铺"));
        SalesOrder salesOrder = salesOrderRepository.save(
            new SalesOrder("ERP-" + suffix, store, "EXT-ORDER-" + suffix, ExternalOrderStatus.PROCESSING)
        );
        SalesOrderItem salesItem = salesItemRepository.save(
            new SalesOrderItem(salesOrder, "ITEM-" + suffix, "销售来源商品", 2)
        );
        OrderProcurementRequest request = requestRepository.save(
            new OrderProcurementRequest(salesItem, 2, buyer, Instant.now())
        );
        request.convert();
        requestRepository.save(request);
        purchaseOrder = purchaseOrderRepository.save(
            new PurchaseOrder("PO-QUERY-" + suffix, supplier, "CNY", 2000, buyer, "查询测试")
        );
        PurchaseOrderItem purchaseItem = purchaseItemRepository.save(new PurchaseOrderItem(
            purchaseOrder, "PRODUCT-1", "SKU-1", "采购商品", "白色/L",
            "https://example.test/product", null, 2, 1000L, "CNY"
        ));
        allocationRepository.save(new PurchaseOrderAllocation(purchaseItem, request, 2));
        eventRepository.save(new PurchaseOrderEvent(purchaseOrder, "CREATED", buyer, "创建采购单", Instant.now()));
    }

    @Test
    void listsBySupplierKeywordAndStatus() throws Exception {
        mockMvc.perform(get("/api/purchase-orders")
                .param("keyword", "深圳查询")
                .param("supplierId", supplier.getId().toString())
                .param("status", "DRAFT")
                .with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].purchaseOrderNo").value(purchaseOrder.getPurchaseOrderNo()))
            .andExpect(jsonPath("$.items[0].supplier.name").value("深圳查询供应商"))
            .andExpect(jsonPath("$.items[0].purchasedQuantity").value(2));
    }

    @Test
    void detailShowsSupplierItemsAndSafeSalesSourceWithoutCustomerFields() throws Exception {
        mockMvc.perform(get("/api/purchase-orders/{id}", purchaseOrder.getId()).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.supplier.contactPhone").value("13800138000"))
            .andExpect(jsonPath("$.items[0].productName").value("采购商品"))
            .andExpect(jsonPath("$.items[0].sources[0].salesOrderNo").exists())
            .andExpect(jsonPath("$.items[0].sources[0].productName").value("销售来源商品"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("recipientPhone"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("addressLine"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sourcePayload"))));
    }

    @Test
    void pagesEventsAndValidatesQueryParameters() throws Exception {
        mockMvc.perform(get("/api/purchase-orders/{id}/events", purchaseOrder.getId())
                .param("page", "1").param("size", "10").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].eventType").value("CREATED"))
            .andExpect(jsonPath("$.items[0].operator.displayName").value("查询采购员"));

        mockMvc.perform(get("/api/purchase-orders").param("size", "101").with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_QUERY_INVALID"));
    }

    @Test
    void returnsNotFoundForUnknownPurchaseOrder() throws Exception {
        mockMvc.perform(get("/api/purchase-orders/{id}", Long.MAX_VALUE).with(jwt()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_NOT_FOUND"));
    }
}
