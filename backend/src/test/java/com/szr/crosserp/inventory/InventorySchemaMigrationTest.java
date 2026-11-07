package com.szr.crosserp.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.szr.crosserp.inventory.model.InventoryAllocation;
import com.szr.crosserp.inventory.model.InventoryLot;
import com.szr.crosserp.inventory.model.InventoryMovement;
import com.szr.crosserp.inventory.model.InventoryMovementType;
import com.szr.crosserp.inventory.model.InventoryQualityStatus;
import com.szr.crosserp.inventory.repository.InventoryAllocationRepository;
import com.szr.crosserp.inventory.repository.InventoryLotRepository;
import com.szr.crosserp.inventory.repository.InventoryMovementRepository;
import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.model.SalesOrder;
import com.szr.crosserp.order.model.SalesOrderItem;
import com.szr.crosserp.order.repository.SalesOrderItemRepository;
import com.szr.crosserp.order.repository.SalesOrderRepository;
import com.szr.crosserp.procurement.model.PurchaseOrder;
import com.szr.crosserp.procurement.model.PurchaseOrderItem;
import com.szr.crosserp.procurement.model.Supplier;
import com.szr.crosserp.procurement.model.SupplierPlatform;
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
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InventorySchemaMigrationTest {
    private static final Set<String> INVENTORY_TABLES = Set.of(
        "INVENTORY_LOTS",
        "INVENTORY_ALLOCATIONS",
        "INVENTORY_MOVEMENTS"
    );

    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;
    @Autowired UserRepository userRepository;
    @Autowired SalesChannelRepository channelRepository;
    @Autowired SalesStoreRepository storeRepository;
    @Autowired SalesOrderRepository orderRepository;
    @Autowired SalesOrderItemRepository orderItemRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Autowired DomesticPackageRepository packageRepository;
    @Autowired GoodsReceiptRepository receiptRepository;
    @Autowired GoodsReceiptItemRepository receiptItemRepository;
    @Autowired InventoryLotRepository lotRepository;
    @Autowired InventoryAllocationRepository allocationRepository;
    @Autowired InventoryMovementRepository movementRepository;

    @Test
    void flywayAppliesVersionFiveAndCreatesInventoryTables() throws Exception {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("5");

        Set<String> tables = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet result = connection.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME").toUpperCase());
            }
        }
        assertThat(tables).containsAll(INVENTORY_TABLES);
    }

    @Test
    void repositoriesPersistAndFindInventoryTraceabilityChain() {
        Fixture fixture = fixture();
        Instant now = Instant.now();
        InventoryLot lot = lotRepository.save(new InventoryLot(
            "LOT-" + fixture.suffix(), fixture.receiptItem(), fixture.purchaseOrderItem(),
            InventoryQualityStatus.ACCEPTED, 2, now
        ));
        InventoryAllocation allocation = allocationRepository.save(new InventoryAllocation(
            "IAL-" + fixture.suffix(), lot, fixture.salesOrderItem(), null, 1, fixture.operator(), now
        ));
        InventoryMovement movement = movementRepository.save(new InventoryMovement(
            "IMV-" + fixture.suffix(), lot, allocation, InventoryMovementType.ORDER_ALLOCATED,
            1, 1, 1, 0, fixture.operator(), null, "IDEMP-" + fixture.suffix(), now
        ));

        assertThat(lotRepository.findByLotNo(lot.getLotNo())).contains(lot);
        assertThat(lotRepository.findByGoodsReceiptItemIdAndQualityStatus(
            fixture.receiptItem().getId(), InventoryQualityStatus.ACCEPTED
        )).contains(lot);
        assertThat(lotRepository.findByIdForUpdate(lot.getId())).contains(lot);
        assertThat(allocationRepository.findByInventoryLotIdOrderByIdAsc(lot.getId()))
            .containsExactly(allocation);
        assertThat(allocationRepository.findBySalesOrderItemIdOrderByIdAsc(fixture.salesOrderItem().getId()))
            .containsExactly(allocation);
        assertThat(allocationRepository.findByIdForUpdate(allocation.getId())).contains(allocation);
        assertThat(movementRepository.findByInventoryLotIdOrderByOccurredAtDescIdDesc(lot.getId()))
            .containsExactly(movement);
    }

    @Test
    void damagedLotStartsFullyQuarantined() {
        Fixture fixture = fixture();
        InventoryLot lot = lotRepository.saveAndFlush(new InventoryLot(
            "LOT-DMG-" + fixture.suffix(), fixture.receiptItem(), fixture.purchaseOrderItem(),
            InventoryQualityStatus.DAMAGED, 2, Instant.now()
        ));

        assertThat(lot.getStatus().name()).isEqualTo("QUARANTINED");
        assertThat(lot.getAvailableQuantity()).isZero();
        assertThat(lot.getQuarantinedQuantity()).isEqualTo(2);
    }

    @Test
    void oneReceiptItemCannotCreateDuplicateLotsForTheSameQuality() {
        Fixture fixture = fixture();
        lotRepository.saveAndFlush(new InventoryLot(
            "LOT-A-" + fixture.suffix(), fixture.receiptItem(), fixture.purchaseOrderItem(),
            InventoryQualityStatus.ACCEPTED, 1, Instant.now()
        ));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> lotRepository.saveAndFlush(new InventoryLot(
            "LOT-B-" + fixture.suffix(), fixture.receiptItem(), fixture.purchaseOrderItem(),
            InventoryQualityStatus.ACCEPTED, 1, Instant.now()
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User operator = userRepository.save(new User(
            "inventory-" + suffix, "库存测试", "not-used-in-this-test", false
        ));
        SalesChannel channel = channelRepository.save(new SalesChannel("INV-" + suffix, "库存测试渠道"));
        SalesStore store = storeRepository.save(new SalesStore(
            channel, "EXT-STORE-" + suffix, "STORE-" + suffix, "库存测试店铺"
        ));
        SalesOrder order = orderRepository.save(new SalesOrder(
            "SO-" + suffix, store, "EXT-ORDER-" + suffix, ExternalOrderStatus.PROCESSING
        ));
        SalesOrderItem salesOrderItem = orderItemRepository.save(new SalesOrderItem(
            order, "EXT-ITEM-" + suffix, "库存测试商品", 2
        ));
        Supplier supplier = supplierRepository.save(new Supplier(
            "SUP-INV-" + suffix, "库存测试供应商", SupplierPlatform.MANUAL
        ));
        PurchaseOrder purchaseOrder = purchaseOrderRepository.save(new PurchaseOrder(
            "PO-INV-" + suffix, supplier, "CNY", operator
        ));
        PurchaseOrderItem purchaseOrderItem = purchaseOrderItemRepository.save(new PurchaseOrderItem(
            purchaseOrder, "库存测试商品", 2, "CNY"
        ));
        DomesticPackage domesticPackage = packageRepository.save(new DomesticPackage(
            "PKG-INV-" + suffix, purchaseOrder, "TRACK-INV-" + suffix, "SF_EXPRESS", operator
        ));
        GoodsReceipt receipt = receiptRepository.save(new GoodsReceipt(
            "GR-INV-" + suffix, domesticPackage, operator, Instant.now(),
            "RCV-IDEMP-" + suffix, "hash-" + suffix, null
        ));
        GoodsReceiptItem receiptItem = receiptItemRepository.save(new GoodsReceiptItem(
            receipt, purchaseOrderItem, 2, 0, null
        ));
        return new Fixture(suffix, operator, salesOrderItem, purchaseOrderItem, receiptItem);
    }

    private record Fixture(
        String suffix,
        User operator,
        SalesOrderItem salesOrderItem,
        PurchaseOrderItem purchaseOrderItem,
        GoodsReceiptItem receiptItem
    ) {
    }
}
