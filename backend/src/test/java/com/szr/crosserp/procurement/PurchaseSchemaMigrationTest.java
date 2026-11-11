package com.szr.crosserp.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class PurchaseSchemaMigrationTest {

    private static final Set<String> PURCHASE_TABLES = Set.of(
        "SUPPLIERS",
        "PURCHASE_ORDERS",
        "PURCHASE_ORDER_ITEMS",
        "PURCHASE_ORDER_ALLOCATIONS",
        "PURCHASE_ORDER_EVENTS"
    );

    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;
    @Autowired UserRepository userRepository;
    @Autowired SalesChannelRepository channelRepository;
    @Autowired SalesStoreRepository storeRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderItemRepository salesOrderItemRepository;
    @Autowired OrderProcurementRequestRepository requestRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Autowired PurchaseOrderAllocationRepository allocationRepository;
    @Autowired PurchaseOrderEventRepository eventRepository;

    @Test
    void laterMigrationsKeepAllPurchaseTables() throws Exception {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("7");

        Set<String> tables = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet result = connection.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME").toUpperCase());
            }
        }
        assertThat(tables).containsAll(PURCHASE_TABLES);
    }

    @Test
    void repositoriesPersistAndFindTheMinimumPurchaseChain() {
        String suffix = suffix();
        User buyer = userRepository.save(new User("buyer-" + suffix, "演示采购", "not-used-in-this-test", false));
        OrderProcurementRequest request = createProcurementRequest(suffix, buyer);
        Supplier supplier = supplierRepository.save(new Supplier("SUP-" + suffix, "演示供应商", SupplierPlatform.MANUAL));
        PurchaseOrder purchaseOrder = purchaseOrderRepository.save(
            new PurchaseOrder("PO-" + suffix, supplier, "CNY", buyer)
        );
        PurchaseOrderItem purchaseItem = purchaseOrderItemRepository.save(
            new PurchaseOrderItem(purchaseOrder, "演示采购商品", 2, "CNY")
        );
        PurchaseOrderAllocation allocation = allocationRepository.save(
            new PurchaseOrderAllocation(purchaseItem, request, 1)
        );
        PurchaseOrderEvent event = eventRepository.save(
            new PurchaseOrderEvent(purchaseOrder, "CREATED", buyer, Instant.now())
        );

        assertThat(supplierRepository.findByCode(supplier.getCode())).contains(supplier);
        assertThat(purchaseOrderRepository.findByPurchaseOrderNo(purchaseOrder.getPurchaseOrderNo()))
            .contains(purchaseOrder);
        assertThat(purchaseOrderItemRepository.findByPurchaseOrderIdOrderByIdAsc(purchaseOrder.getId()))
            .containsExactly(purchaseItem);
        assertThat(allocationRepository.findByProcurementRequestId(request.getId())).containsExactly(allocation);
        assertThat(eventRepository.findByPurchaseOrderIdOrderByOccurredAtDescIdDesc(purchaseOrder.getId()))
            .containsExactly(event);
    }

    @Test
    void supplierCodeMustBeUnique() {
        String code = "SUP-" + suffix();
        supplierRepository.saveAndFlush(new Supplier(code, "供应商一", SupplierPlatform.MANUAL));

        assertThatThrownBy(() -> supplierRepository.saveAndFlush(
            new Supplier(code, "供应商二", SupplierPlatform.MANUAL)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void purchasedQuantityMustBePositive() {
        String suffix = suffix();
        User buyer = userRepository.save(new User("buyer-" + suffix, "演示采购", "not-used-in-this-test", false));
        Supplier supplier = supplierRepository.save(new Supplier("SUP-" + suffix, "供应商", SupplierPlatform.MANUAL));
        PurchaseOrder purchaseOrder = purchaseOrderRepository.save(
            new PurchaseOrder("PO-" + suffix, supplier, "CNY", buyer)
        );

        assertThatThrownBy(() -> purchaseOrderItemRepository.saveAndFlush(
            new PurchaseOrderItem(purchaseOrder, "错误商品", 0, "CNY")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allocationQuantityMustBePositive() {
        String suffix = suffix();
        User buyer = userRepository.save(new User("buyer-" + suffix, "演示采购", "not-used-in-this-test", false));
        OrderProcurementRequest request = createProcurementRequest(suffix, buyer);
        Supplier supplier = supplierRepository.save(new Supplier("SUP-" + suffix, "供应商", SupplierPlatform.MANUAL));
        PurchaseOrder purchaseOrder = purchaseOrderRepository.save(
            new PurchaseOrder("PO-" + suffix, supplier, "CNY", buyer)
        );
        PurchaseOrderItem purchaseItem = purchaseOrderItemRepository.save(
            new PurchaseOrderItem(purchaseOrder, "采购商品", 1, "CNY")
        );

        assertThatThrownBy(() -> allocationRepository.saveAndFlush(
            new PurchaseOrderAllocation(purchaseItem, request, 0)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private OrderProcurementRequest createProcurementRequest(String suffix, User buyer) {
        SalesChannel channel = channelRepository.save(new SalesChannel("CH-" + suffix, "渠道"));
        SalesStore store = storeRepository.save(
            new SalesStore(channel, "STORE-" + suffix, "store-" + suffix, "店铺")
        );
        SalesOrder order = salesOrderRepository.save(
            new SalesOrder("ERP-" + suffix, store, "EXT-" + suffix, ExternalOrderStatus.NEW)
        );
        SalesOrderItem item = salesOrderItemRepository.save(
            new SalesOrderItem(order, "ITEM-" + suffix, "销售商品", 1)
        );
        return requestRepository.save(new OrderProcurementRequest(item, 1, buyer, Instant.now()));
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
