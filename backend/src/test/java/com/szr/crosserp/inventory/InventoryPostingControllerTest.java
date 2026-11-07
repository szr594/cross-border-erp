package com.szr.crosserp.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.szr.crosserp.inventory.repository.InventoryLotRepository;
import com.szr.crosserp.inventory.repository.InventoryAllocationRepository;
import com.szr.crosserp.inventory.repository.InventoryMovementRepository;
import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.model.OrderProcurementRequest;
import com.szr.crosserp.order.model.SalesOrder;
import com.szr.crosserp.order.model.SalesOrderItem;
import com.szr.crosserp.order.repository.OrderProcurementRequestRepository;
import com.szr.crosserp.order.repository.SalesOrderItemRepository;
import com.szr.crosserp.order.repository.SalesOrderRepository;
import com.szr.crosserp.procurement.model.PurchaseOrderAllocation;
import com.szr.crosserp.procurement.model.PurchaseOrder;
import com.szr.crosserp.procurement.model.PurchaseOrderItem;
import com.szr.crosserp.procurement.model.Supplier;
import com.szr.crosserp.procurement.model.SupplierPlatform;
import com.szr.crosserp.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderAllocationRepository;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InventoryPostingControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Autowired DomesticPackageRepository packageRepository;
    @Autowired GoodsReceiptRepository receiptRepository;
    @Autowired GoodsReceiptItemRepository receiptItemRepository;
    @Autowired InventoryLotRepository lotRepository;
    @Autowired InventoryAllocationRepository inventoryAllocationRepository;
    @Autowired InventoryMovementRepository movementRepository;
    @Autowired SalesChannelRepository salesChannelRepository;
    @Autowired SalesStoreRepository salesStoreRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderItemRepository salesOrderItemRepository;
    @Autowired OrderProcurementRequestRepository procurementRequestRepository;
    @Autowired PurchaseOrderAllocationRepository purchaseOrderAllocationRepository;

    @Test
    void ownerCanRetryHistoricalPostingWithoutDuplicatingLotsOrMovements() throws Exception {
        Fixture fixture = historicalReceipt(2, 1);

        mockMvc.perform(post("/api/inventory/receipts/{receiptId}/post", fixture.receipt().getId())
                .with(actor(fixture.owner(), "OWNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inventoryPosted").value(true))
            .andExpect(jsonPath("$.lotIds.length()").value(2));
        mockMvc.perform(post("/api/inventory/receipts/{receiptId}/post", fixture.receipt().getId())
                .with(actor(fixture.owner(), "OWNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inventoryPosted").value(true))
            .andExpect(jsonPath("$.lotIds.length()").value(2));

        assertThat(lotRepository.count()).isEqualTo(2);
        assertThat(movementRepository.count()).isEqualTo(2);
    }

    @Test
    void warehouseCannotRunHistoricalPosting() throws Exception {
        Fixture fixture = historicalReceipt(1, 0);

        mockMvc.perform(post("/api/inventory/receipts/{receiptId}/post", fixture.receipt().getId())
                .with(actor(fixture.owner(), "WAREHOUSE")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("INVENTORY_FORBIDDEN"));

        assertThat(lotRepository.count()).isZero();
    }

    @Test
    void historicalPostingAutomaticallyAllocatesByPurchaseSourceWithoutDuplicatingOnRetry() throws Exception {
        Fixture fixture = historicalAllocatedReceipt(2);

        mockMvc.perform(post("/api/inventory/receipts/{receiptId}/post", fixture.receipt().getId())
                .with(actor(fixture.owner(), "OWNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allocationIds.length()").value(1));
        mockMvc.perform(post("/api/inventory/receipts/{receiptId}/post", fixture.receipt().getId())
                .with(actor(fixture.owner(), "OWNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allocationIds.length()").value(0));

        assertThat(inventoryAllocationRepository.findAll())
            .singleElement()
            .satisfies(allocation -> {
                assertThat(allocation.getSalesOrderItem().getId()).isEqualTo(fixture.salesOrderItemId());
                assertThat(allocation.getQuantity()).isEqualTo(2);
            });
        assertThat(lotRepository.findAll())
            .singleElement()
            .satisfies(lot -> {
                assertThat(lot.getAvailableQuantity()).isZero();
                assertThat(lot.getReservedQuantity()).isEqualTo(2);
            });
        assertThat(movementRepository.count()).isEqualTo(2);
    }

    private Fixture historicalReceipt(int accepted, int damaged) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User owner = userRepository.save(new User(
            "inventory-owner-" + suffix, "库存老板", "not-used-in-this-test", false
        ));
        Supplier supplier = supplierRepository.save(new Supplier(
            "SUP-POST-" + suffix, "补偿测试供应商", SupplierPlatform.MANUAL
        ));
        PurchaseOrder purchaseOrder = purchaseOrderRepository.save(new PurchaseOrder(
            "PO-POST-" + suffix, supplier, "CNY", owner
        ));
        PurchaseOrderItem purchaseOrderItem = purchaseOrderItemRepository.save(new PurchaseOrderItem(
            purchaseOrder, "补偿测试商品", accepted + damaged, "CNY"
        ));
        DomesticPackage domesticPackage = packageRepository.save(new DomesticPackage(
            "PKG-POST-" + suffix, purchaseOrder, "TRACK-POST-" + suffix, "SF_EXPRESS", owner
        ));
        GoodsReceipt receipt = receiptRepository.save(new GoodsReceipt(
            "GR-POST-" + suffix, domesticPackage, owner, Instant.now(),
            "RCV-POST-" + suffix, "hash-" + suffix, null
        ));
        receiptItemRepository.saveAndFlush(new GoodsReceiptItem(
            receipt, purchaseOrderItem, accepted, damaged, damaged > 0 ? "包含破损" : null
        ));
        return new Fixture(owner, receipt, null);
    }

    private Fixture historicalAllocatedReceipt(int accepted) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User owner = userRepository.save(new User(
            "inventory-allocation-owner-" + suffix, "库存分配老板", "not-used-in-this-test", false
        ));
        SalesChannel channel = salesChannelRepository.save(new SalesChannel("CH-" + suffix, "测试渠道"));
        SalesStore store = salesStoreRepository.save(new SalesStore(
            channel, "EXT-STORE-" + suffix, "STORE-" + suffix, "库存测试店铺"
        ));
        SalesOrder salesOrder = salesOrderRepository.save(new SalesOrder(
            "SO-ALLOC-" + suffix, store, "EXT-ORDER-" + suffix, ExternalOrderStatus.PROCESSING
        ));
        SalesOrderItem salesOrderItem = salesOrderItemRepository.save(new SalesOrderItem(
            salesOrder, "EXT-ITEM-" + suffix, "待分配商品", accepted
        ));
        OrderProcurementRequest request = procurementRequestRepository.save(new OrderProcurementRequest(
            salesOrderItem, accepted, owner, Instant.now()
        ));
        request.convert();
        procurementRequestRepository.save(request);
        Supplier supplier = supplierRepository.save(new Supplier(
            "SUP-ALLOC-" + suffix, "库存分配供应商", SupplierPlatform.MANUAL
        ));
        PurchaseOrder purchaseOrder = purchaseOrderRepository.save(new PurchaseOrder(
            "PO-ALLOC-" + suffix, supplier, "CNY", owner
        ));
        PurchaseOrderItem purchaseOrderItem = purchaseOrderItemRepository.save(new PurchaseOrderItem(
            purchaseOrder, "待分配商品", accepted, "CNY"
        ));
        purchaseOrderAllocationRepository.save(new PurchaseOrderAllocation(
            purchaseOrderItem, request, accepted
        ));
        DomesticPackage domesticPackage = packageRepository.save(new DomesticPackage(
            "PKG-ALLOC-" + suffix, purchaseOrder, "TRACK-ALLOC-" + suffix, "SF_EXPRESS", owner
        ));
        GoodsReceipt receipt = receiptRepository.save(new GoodsReceipt(
            "GR-ALLOC-" + suffix, domesticPackage, owner, Instant.now(),
            "RCV-ALLOC-" + suffix, "hash-" + suffix, null
        ));
        receiptItemRepository.saveAndFlush(new GoodsReceiptItem(
            receipt, purchaseOrderItem, accepted, 0, null
        ));
        return new Fixture(owner, receipt, salesOrderItem.getId());
    }

    private JwtRequestPostProcessor actor(User user, String role) {
        return jwt().jwt(token -> token.subject(user.getId().toString()).claim("roles", List.of(role)));
    }

    private record Fixture(User owner, GoodsReceipt receipt, Long salesOrderItemId) {
    }
}
