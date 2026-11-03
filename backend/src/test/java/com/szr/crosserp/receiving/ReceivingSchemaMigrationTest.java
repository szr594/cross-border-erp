package com.szr.crosserp.receiving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.szr.crosserp.receiving.model.ReceivingEvent;
import com.szr.crosserp.receiving.model.UnexpectedReceiptItem;
import com.szr.crosserp.receiving.repository.DomesticPackageRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptItemRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptRepository;
import com.szr.crosserp.receiving.repository.ReceivingEventRepository;
import com.szr.crosserp.receiving.repository.UnexpectedReceiptItemRepository;
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
class ReceivingSchemaMigrationTest {
    private static final Set<String> RECEIVING_TABLES = Set.of(
        "DOMESTIC_PACKAGES",
        "GOODS_RECEIPTS",
        "GOODS_RECEIPT_ITEMS",
        "UNEXPECTED_RECEIPT_ITEMS",
        "RECEIVING_EVENTS"
    );

    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;
    @Autowired UserRepository userRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Autowired DomesticPackageRepository domesticPackageRepository;
    @Autowired GoodsReceiptRepository goodsReceiptRepository;
    @Autowired GoodsReceiptItemRepository goodsReceiptItemRepository;
    @Autowired UnexpectedReceiptItemRepository unexpectedReceiptItemRepository;
    @Autowired ReceivingEventRepository receivingEventRepository;

    @Test
    void flywayAppliesVersionFourAndCreatesReceivingTables() throws Exception {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");

        Set<String> tables = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet result = connection.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME").toUpperCase());
            }
        }
        assertThat(tables).containsAll(RECEIVING_TABLES);
    }

    @Test
    void repositoriesPersistAndFindTheMinimumReceivingChain() {
        Fixture fixture = fixture();
        GoodsReceipt receipt = goodsReceiptRepository.save(new GoodsReceipt(
            "GR-" + fixture.suffix(), fixture.domesticPackage(), fixture.operator(),
            Instant.now(), "IDEMP-" + fixture.suffix(), "hash-" + fixture.suffix(), null
        ));
        GoodsReceiptItem receiptItem = goodsReceiptItemRepository.save(
            new GoodsReceiptItem(receipt, fixture.purchaseOrderItem(), 1, 1, "一件破损")
        );
        UnexpectedReceiptItem unexpectedItem = unexpectedReceiptItemRepository.save(
            new UnexpectedReceiptItem(receipt, "未知配件", "WRONG-SKU", 1, null)
        );
        ReceivingEvent event = receivingEventRepository.save(new ReceivingEvent(
            fixture.domesticPackage(), receipt, "RECEIPT_CONFIRMED",
            fixture.operator(), null, Instant.now()
        ));

        assertThat(domesticPackageRepository.findByPackageNo(fixture.domesticPackage().getPackageNo()))
            .contains(fixture.domesticPackage());
        assertThat(goodsReceiptRepository.findByIdempotencyKey(receipt.getIdempotencyKey())).contains(receipt);
        assertThat(goodsReceiptItemRepository.findByGoodsReceiptIdOrderByIdAsc(receipt.getId()))
            .containsExactly(receiptItem);
        assertThat(unexpectedReceiptItemRepository.findByGoodsReceiptIdOrderByIdAsc(receipt.getId()))
            .containsExactly(unexpectedItem);
        assertThat(receivingEventRepository.findByDomesticPackageIdOrderByOccurredAtDescIdDesc(
            fixture.domesticPackage().getId()
        )).containsExactly(event);
    }

    @Test
    void carrierAndTrackingNumberMustBeUniqueTogether() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> domesticPackageRepository.saveAndFlush(new DomesticPackage(
            "PKG-DUP-" + fixture.suffix(), fixture.purchaseOrder(),
            fixture.domesticPackage().getTrackingNumber(), fixture.domesticPackage().getCarrier(), fixture.operator()
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void receiptItemMustContainAQuantity() {
        Fixture fixture = fixture();
        GoodsReceipt receipt = goodsReceiptRepository.save(new GoodsReceipt(
            "GR-" + fixture.suffix(), fixture.domesticPackage(), fixture.operator(),
            Instant.now(), "IDEMP-" + fixture.suffix(), "hash-" + fixture.suffix(), null
        ));

        assertThatThrownBy(() -> goodsReceiptItemRepository.saveAndFlush(
            new GoodsReceiptItem(receipt, fixture.purchaseOrderItem(), 0, 0, null)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unexpectedItemQuantityMustBePositive() {
        Fixture fixture = fixture();
        GoodsReceipt receipt = goodsReceiptRepository.save(new GoodsReceipt(
            "GR-" + fixture.suffix(), fixture.domesticPackage(), fixture.operator(),
            Instant.now(), "IDEMP-" + fixture.suffix(), "hash-" + fixture.suffix(), null
        ));

        assertThatThrownBy(() -> unexpectedReceiptItemRepository.saveAndFlush(
            new UnexpectedReceiptItem(receipt, "错误数量", null, 0, null)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture fixture() {
        String suffix = suffix();
        User operator = userRepository.save(new User(
            "receiver-" + suffix, "演示仓库", "not-used-in-this-test", false
        ));
        Supplier supplier = supplierRepository.save(new Supplier(
            "SUP-REC-" + suffix, "收货供应商", SupplierPlatform.MANUAL
        ));
        PurchaseOrder purchaseOrder = purchaseOrderRepository.save(new PurchaseOrder(
            "PO-REC-" + suffix, supplier, "CNY", operator
        ));
        PurchaseOrderItem purchaseOrderItem = purchaseOrderItemRepository.save(new PurchaseOrderItem(
            purchaseOrder, "待收货商品", 2, "CNY"
        ));
        DomesticPackage domesticPackage = domesticPackageRepository.save(new DomesticPackage(
            "PKG-" + suffix, purchaseOrder, "TRACK-" + suffix, "SF_EXPRESS", operator
        ));
        return new Fixture(suffix, operator, purchaseOrder, purchaseOrderItem, domesticPackage);
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record Fixture(
        String suffix,
        User operator,
        PurchaseOrder purchaseOrder,
        PurchaseOrderItem purchaseOrderItem,
        DomesticPackage domesticPackage
    ) {
    }
}
