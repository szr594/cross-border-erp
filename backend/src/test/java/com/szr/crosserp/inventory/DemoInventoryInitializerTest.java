package com.szr.crosserp.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.szr.crosserp.inventory.config.DemoInventoryInitializer;
import com.szr.crosserp.inventory.repository.InventoryAllocationRepository;
import com.szr.crosserp.inventory.repository.InventoryLotRepository;
import com.szr.crosserp.inventory.repository.InventoryMovementRepository;
import com.szr.crosserp.inventory.service.InventoryCommandService;
import com.szr.crosserp.order.config.DemoOrderInitializer;
import com.szr.crosserp.procurement.config.DemoPurchaseOrderInitializer;
import com.szr.crosserp.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderRepository;
import com.szr.crosserp.receiving.config.DemoReceivingInitializer;
import com.szr.crosserp.receiving.repository.DomesticPackageRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptItemRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptRepository;
import com.szr.crosserp.receiving.service.ReceivingCommandService;
import com.szr.crosserp.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoInventoryInitializerTest {
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderItemRepository purchaseItemRepository;
    @Autowired DomesticPackageRepository packageRepository;
    @Autowired GoodsReceiptRepository receiptRepository;
    @Autowired GoodsReceiptItemRepository receiptItemRepository;
    @Autowired InventoryLotRepository lotRepository;
    @Autowired InventoryAllocationRepository allocationRepository;
    @Autowired InventoryMovementRepository movementRepository;
    @Autowired ReceivingCommandService receivingCommandService;
    @Autowired InventoryCommandService inventoryCommandService;

    @Test
    void createsCompleteTraceableInventoryDemoIdempotently() {
        jdbcTemplate.update("""
            INSERT INTO app_users(login_name, display_name, password_hash, status)
            SELECT 'owner', '演示老板', 'test-only-password-hash', 'ACTIVE'
            WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE login_name = 'owner')
            """);
        new DemoOrderInitializer(jdbcTemplate).run(null);
        new DemoPurchaseOrderInitializer(jdbcTemplate).run(null);
        new DemoReceivingInitializer(
            userRepository, purchaseOrderRepository, purchaseItemRepository,
            packageRepository, receivingCommandService
        ).run(null);
        DemoInventoryInitializer initializer = new DemoInventoryInitializer(
            userRepository, purchaseOrderRepository, purchaseItemRepository,
            packageRepository, receiptRepository, receiptItemRepository,
            lotRepository, allocationRepository, movementRepository,
            receivingCommandService, inventoryCommandService
        );

        initializer.run(null);
        initializer.run(null);

        assertThat(count("""
            SELECT COUNT(*) FROM domestic_packages
            WHERE tracking_number = 'DEMO-SF-20260002'
            """)).isEqualTo(1);
        assertThat(count("""
            SELECT COUNT(*) FROM goods_receipts
            WHERE idempotency_key = 'demo-receipt-20260002'
            """)).isEqualTo(1);
        assertThat(count("""
            SELECT COUNT(*) FROM inventory_lots lot
            JOIN goods_receipt_items item ON item.id = lot.goods_receipt_item_id
            JOIN goods_receipts receipt ON receipt.id = item.goods_receipt_id
            WHERE receipt.idempotency_key = 'demo-receipt-20260002'
              AND lot.quality_status = 'ACCEPTED'
              AND lot.initial_quantity = 3
              AND lot.available_quantity = 3
              AND lot.reserved_quantity = 0
            """)).isEqualTo(1);
        assertThat(count("""
            SELECT COUNT(*) FROM inventory_allocations allocation
            JOIN inventory_lots lot ON lot.id = allocation.inventory_lot_id
            JOIN goods_receipt_items item ON item.id = lot.goods_receipt_item_id
            JOIN goods_receipts receipt ON receipt.id = item.goods_receipt_id
            WHERE receipt.idempotency_key = 'demo-receipt-20260002'
              AND allocation.status = 'RELEASED'
            """)).isEqualTo(1);
        assertThat(count("""
            SELECT COUNT(DISTINCT movement.movement_type)
            FROM inventory_movements movement
            JOIN inventory_lots lot ON lot.id = movement.inventory_lot_id
            JOIN goods_receipt_items item ON item.id = lot.goods_receipt_item_id
            JOIN goods_receipts receipt ON receipt.id = item.goods_receipt_id
            WHERE receipt.idempotency_key = 'demo-receipt-20260002'
              AND movement.movement_type IN (
                'RECEIPT_ACCEPTED', 'ORDER_ALLOCATED', 'ALLOCATION_RELEASED', 'ADJUSTMENT_IN'
              )
            """)).isEqualTo(4);
        assertThat(count("""
            SELECT COUNT(*) FROM inventory_lots lot
            JOIN goods_receipt_items item ON item.id = lot.goods_receipt_item_id
            JOIN goods_receipts receipt ON receipt.id = item.goods_receipt_id
            WHERE receipt.idempotency_key = 'demo-receipt-20260001'
              AND lot.quality_status = 'DAMAGED'
              AND lot.quarantined_quantity = 1
            """)).isEqualTo(1);
    }

    private Integer count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
