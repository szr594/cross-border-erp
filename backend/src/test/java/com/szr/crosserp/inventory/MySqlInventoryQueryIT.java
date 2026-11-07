package com.szr.crosserp.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.szr.crosserp.inventory.dto.InventoryAllocationQuery;
import com.szr.crosserp.inventory.dto.InventoryLotQuery;
import com.szr.crosserp.inventory.model.InventoryAllocationStatus;
import com.szr.crosserp.inventory.model.InventoryQualityStatus;
import com.szr.crosserp.inventory.repository.InventoryLotRepository;
import com.szr.crosserp.inventory.service.InventoryPostingCommandService;
import com.szr.crosserp.inventory.service.InventoryQueryService;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("mysql-integration")
class MySqlInventoryQueryIT {
    @Autowired InventoryQueryService queryService;
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

    @Test
    void mysqlQueriesLotsAllocationsMovementsAndOrderCoverage() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User owner = userRepository.save(new User("mysql-query-" + suffix, "MySQL库存查询", "not-used", false));
        SalesChannel channel = channelRepository.save(new SalesChannel("MQ-" + suffix, "MySQL查询渠道"));
        SalesStore store = storeRepository.save(new SalesStore(
            channel, "MQ-STORE-EXT-" + suffix, "MQ-STORE-" + suffix, "MySQL查询店铺"
        ));
        SalesOrder order = salesOrderRepository.save(new SalesOrder(
            "MQ-SO-" + suffix, store, "MQ-EXT-" + suffix, ExternalOrderStatus.PROCESSING
        ));
        SalesOrderItem salesItem = salesItemRepository.save(new SalesOrderItem(
            order, "MQ-ITEM-" + suffix, "MySQL库存查询商品", 2
        ));
        OrderProcurementRequest request = requestRepository.save(new OrderProcurementRequest(
            salesItem, 2, owner, Instant.now()
        ));
        request.convert();
        requestRepository.save(request);
        Supplier supplier = supplierRepository.save(new Supplier(
            "MQ-SUP-" + suffix, "MySQL库存查询供应商", SupplierPlatform.MANUAL
        ));
        PurchaseOrder purchaseOrder = purchaseOrderRepository.save(new PurchaseOrder(
            "MQ-PO-" + suffix, supplier, "CNY", owner
        ));
        PurchaseOrderItem purchaseItem = purchaseItemRepository.save(new PurchaseOrderItem(
            purchaseOrder, "MySQL库存查询商品", 2, "CNY"
        ));
        purchaseAllocationRepository.save(new PurchaseOrderAllocation(purchaseItem, request, 2));
        DomesticPackage domesticPackage = packageRepository.save(new DomesticPackage(
            "MQ-PKG-" + suffix, purchaseOrder, "MQ-TRACK-" + suffix, "SF_EXPRESS", owner
        ));
        GoodsReceipt receipt = receiptRepository.save(new GoodsReceipt(
            "MQ-GR-" + suffix, domesticPackage, owner, Instant.now(), "MQ-RCV-" + suffix,
            "hash-" + suffix, null
        ));
        GoodsReceiptItem receiptItem = receiptItemRepository.save(new GoodsReceiptItem(
            receipt, purchaseItem, 2, 0, null
        ));
        postingCommandService.postHistoricalReceipt(receipt.getId(), owner.getId().toString(), List.of("OWNER"));
        var lot = lotRepository.findByGoodsReceiptItemIdAndQualityStatus(
            receiptItem.getId(), InventoryQualityStatus.ACCEPTED
        ).orElseThrow();

        var lots = queryService.findLots(new InventoryLotQuery(
            1, 20, suffix, purchaseOrder.getId(), lot.getStatus(), InventoryQualityStatus.ACCEPTED
        ));
        var allocations = queryService.findAllocations(new InventoryAllocationQuery(
            1, 20, order.getOrderNo(), lot.getLotNo(), InventoryAllocationStatus.RESERVED
        ));
        var movements = queryService.findMovements(lot.getId(), 1, 20);
        var coverage = queryService.findOrderCoverage(order.getId());

        assertThat(lots.items()).singleElement().satisfies(item ->
            assertThat(item.purchaseSource().purchaseOrderNo()).isEqualTo(purchaseOrder.getPurchaseOrderNo())
        );
        assertThat(allocations.items()).singleElement().satisfies(allocation ->
            assertThat(allocation.quantity()).isEqualTo(2)
        );
        assertThat(movements.items()).hasSize(2);
        assertThat(coverage.items()).singleElement().satisfies(item -> {
            assertThat(item.reservedQuantity()).isEqualTo(2);
            assertThat(item.shortageQuantity()).isZero();
        });
    }
}
