package com.szr.crosserp.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.szr.crosserp.inventory.repository.InventoryLotRepository;
import com.szr.crosserp.inventory.repository.InventoryAllocationRepository;
import com.szr.crosserp.inventory.repository.InventoryMovementRepository;
import com.szr.crosserp.inventory.model.InventoryQualityStatus;
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
import com.szr.crosserp.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderAllocationRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("mysql-integration")
class MySqlInventoryPostingIT {
    @Autowired InventoryPostingCommandService commandService;
    @Autowired UserRepository userRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Autowired DomesticPackageRepository packageRepository;
    @Autowired GoodsReceiptRepository receiptRepository;
    @Autowired GoodsReceiptItemRepository receiptItemRepository;
    @Autowired InventoryLotRepository lotRepository;
    @Autowired InventoryAllocationRepository allocationRepository;
    @Autowired InventoryMovementRepository movementRepository;
    @Autowired SalesChannelRepository salesChannelRepository;
    @Autowired SalesStoreRepository salesStoreRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderItemRepository salesOrderItemRepository;
    @Autowired OrderProcurementRequestRepository procurementRequestRepository;
    @Autowired PurchaseOrderAllocationRepository purchaseOrderAllocationRepository;

    @Test
    void mysqlHistoricalPostingIsIdempotent() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User owner = userRepository.save(new User(
            "mysql-inventory-" + suffix, "MySQL库存老板", "not-used-in-this-test", false
        ));
        Supplier supplier = supplierRepository.save(new Supplier(
            "MYSQL-POST-" + suffix, "MySQL补偿供应商", SupplierPlatform.MANUAL
        ));
        PurchaseOrder purchaseOrder = purchaseOrderRepository.save(new PurchaseOrder(
            "MYSQL-PO-POST-" + suffix, supplier, "CNY", owner
        ));
        PurchaseOrderItem purchaseOrderItem = purchaseOrderItemRepository.save(new PurchaseOrderItem(
            purchaseOrder, "MySQL补偿商品", 3, "CNY"
        ));
        DomesticPackage domesticPackage = packageRepository.save(new DomesticPackage(
            "MYSQL-PKG-POST-" + suffix, purchaseOrder, "MYSQL-TRACK-POST-" + suffix,
            "SF_EXPRESS", owner
        ));
        GoodsReceipt receipt = receiptRepository.save(new GoodsReceipt(
            "MYSQL-GR-POST-" + suffix, domesticPackage, owner, Instant.now(),
            "MYSQL-RCV-POST-" + suffix, "hash-" + suffix, null
        ));
        GoodsReceiptItem receiptItem = receiptItemRepository.save(new GoodsReceiptItem(
            receipt, purchaseOrderItem, 2, 1, "一件破损"
        ));

        commandService.postHistoricalReceipt(receipt.getId(), owner.getId().toString(), List.of("OWNER"));
        commandService.postHistoricalReceipt(receipt.getId(), owner.getId().toString(), List.of("OWNER"));

        var acceptedLot = lotRepository.findByGoodsReceiptItemIdAndQualityStatus(
            receiptItem.getId(), InventoryQualityStatus.ACCEPTED
        ).orElseThrow();
        var damagedLot = lotRepository.findByGoodsReceiptItemIdAndQualityStatus(
            receiptItem.getId(), InventoryQualityStatus.DAMAGED
        ).orElseThrow();
        assertThat(movementRepository.findByInventoryLotIdOrderByOccurredAtDescIdDesc(acceptedLot.getId()))
            .hasSize(1);
        assertThat(movementRepository.findByInventoryLotIdOrderByOccurredAtDescIdDesc(damagedLot.getId()))
            .hasSize(1);
    }

    @Test
    void mysqlConcurrentPostingDoesNotAllocateBeyondOrderOrPurchaseSource() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User owner = userRepository.save(new User(
            "mysql-allocation-" + suffix, "MySQL并发分配老板", "not-used-in-this-test", false
        ));
        SalesChannel channel = salesChannelRepository.save(new SalesChannel("MYSQL-CH-" + suffix, "MySQL渠道"));
        SalesStore store = salesStoreRepository.save(new SalesStore(
            channel, "MYSQL-STORE-EXT-" + suffix, "MYSQL-STORE-" + suffix, "MySQL店铺"
        ));
        SalesOrder salesOrder = salesOrderRepository.save(new SalesOrder(
            "MYSQL-SO-" + suffix, store, "MYSQL-EXT-ORDER-" + suffix, ExternalOrderStatus.PROCESSING
        ));
        SalesOrderItem salesOrderItem = salesOrderItemRepository.save(new SalesOrderItem(
            salesOrder, "MYSQL-EXT-ITEM-" + suffix, "MySQL并发商品", 2
        ));
        OrderProcurementRequest request = procurementRequestRepository.save(new OrderProcurementRequest(
            salesOrderItem, 2, owner, Instant.now()
        ));
        request.convert();
        procurementRequestRepository.save(request);
        Supplier supplier = supplierRepository.save(new Supplier(
            "MYSQL-ALLOC-" + suffix, "MySQL并发供应商", SupplierPlatform.MANUAL
        ));
        PurchaseOrder purchaseOrder = purchaseOrderRepository.save(new PurchaseOrder(
            "MYSQL-PO-ALLOC-" + suffix, supplier, "CNY", owner
        ));
        PurchaseOrderItem purchaseOrderItem = purchaseOrderItemRepository.save(new PurchaseOrderItem(
            purchaseOrder, "MySQL并发商品", 4, "CNY"
        ));
        purchaseOrderAllocationRepository.save(new PurchaseOrderAllocation(purchaseOrderItem, request, 2));
        GoodsReceipt firstReceipt = createReceipt(suffix + "A", purchaseOrder, purchaseOrderItem, owner, 2);
        GoodsReceipt secondReceipt = createReceipt(suffix + "B", purchaseOrder, purchaseOrderItem, owner, 2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return commandService.postHistoricalReceipt(firstReceipt.getId(), owner.getId().toString(), List.of("OWNER"));
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return commandService.postHistoricalReceipt(secondReceipt.getId(), owner.getId().toString(), List.of("OWNER"));
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(allocationRepository.sumActiveQuantityBySalesOrderItemId(salesOrderItem.getId())).isEqualTo(2);
        assertThat(allocationRepository.findBySalesOrderItemIdOrderByIdAsc(salesOrderItem.getId()))
            .singleElement()
            .satisfies(allocation -> assertThat(lotRepository.findById(
                allocation.getInventoryLot().getId()
            ).orElseThrow().getReservedQuantity()).isEqualTo(2));
    }

    private GoodsReceipt createReceipt(
        String suffix, PurchaseOrder purchaseOrder, PurchaseOrderItem purchaseOrderItem,
        User owner, int accepted
    ) {
        DomesticPackage domesticPackage = packageRepository.save(new DomesticPackage(
            "MYSQL-PKG-ALLOC-" + suffix, purchaseOrder, "MYSQL-TRACK-ALLOC-" + suffix,
            "SF_EXPRESS", owner
        ));
        GoodsReceipt receipt = receiptRepository.save(new GoodsReceipt(
            "MYSQL-GR-ALLOC-" + suffix, domesticPackage, owner, Instant.now(),
            "MYSQL-RCV-ALLOC-" + suffix, "hash-" + suffix, null
        ));
        receiptItemRepository.save(new GoodsReceiptItem(
            receipt, purchaseOrderItem, accepted, 0, null
        ));
        return receipt;
    }
}
