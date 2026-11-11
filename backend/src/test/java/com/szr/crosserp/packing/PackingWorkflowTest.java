package com.szr.crosserp.packing;

import static org.assertj.core.api.Assertions.assertThat;

import com.szr.crosserp.inventory.config.DemoInventoryInitializer;
import com.szr.crosserp.inventory.model.InventoryAllocationStatus;
import com.szr.crosserp.inventory.repository.InventoryAllocationRepository;
import com.szr.crosserp.inventory.repository.InventoryLotRepository;
import com.szr.crosserp.inventory.repository.InventoryMovementRepository;
import com.szr.crosserp.inventory.service.InventoryCommandService;
import com.szr.crosserp.order.config.DemoOrderInitializer;
import com.szr.crosserp.order.repository.SalesOrderRepository;
import com.szr.crosserp.packing.dto.PackingDtos.BoxItemCommand;
import com.szr.crosserp.packing.dto.PackingDtos.CompleteRequest;
import com.szr.crosserp.packing.dto.PackingDtos.SaveBoxRequest;
import com.szr.crosserp.packing.dto.PackingDtos.StartRequest;
import com.szr.crosserp.packing.service.PackingCommandService;
import com.szr.crosserp.packing.service.PackingQueryService;
import com.szr.crosserp.procurement.config.DemoPurchaseOrderInitializer;
import com.szr.crosserp.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderRepository;
import com.szr.crosserp.receiving.config.DemoReceivingInitializer;
import com.szr.crosserp.receiving.repository.DomesticPackageRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptItemRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptRepository;
import com.szr.crosserp.receiving.service.ReceivingCommandService;
import com.szr.crosserp.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PackingWorkflowTest {
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
    @Autowired SalesOrderRepository orderRepository;
    @Autowired PackingCommandService packingCommands;
    @Autowired PackingQueryService packingQueries;

    @Test
    void completesPackingAndConsumesEveryReservedAllocationIdempotently() {
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
        new DemoInventoryInitializer(
            userRepository, purchaseOrderRepository, purchaseItemRepository,
            packageRepository, receiptRepository, receiptItemRepository,
            lotRepository, allocationRepository,
            movementRepository, receivingCommandService, inventoryCommandService
        ).run(null);

        var owner = userRepository.findByLoginNameIgnoreCase("owner").orElseThrow();
        var order = orderRepository.findByOrderNo("DEMO-240101").orElseThrow();
        var ready = packingQueries.findDetail(order.getId());
        assertThat(ready.status()).isEqualTo("READY");
        assertThat(ready.availableActions()).containsExactly("START");
        assertThat(ready.allocations()).hasSize(1);

        var started = packingCommands.start(
            order.getId(), new StartRequest("测试开始打包"),
            owner.getId().toString(), List.of("OWNER")
        );
        var boxItems = ready.allocations().stream()
            .map(item -> new BoxItemCommand(item.allocationId(), item.quantity()))
            .toList();
        var boxed = packingCommands.addBox(
            started.taskId(),
            new SaveBoxRequest(1200, 300, 220, 180, "完整装入一箱", started.version(), boxItems),
            owner.getId().toString(), List.of("OWNER")
        );
        var completed = packingCommands.complete(
            started.taskId(), new CompleteRequest("packing-workflow-test", boxed.version()),
            owner.getId().toString(), List.of("OWNER")
        );
        var duplicate = packingCommands.complete(
            started.taskId(), new CompleteRequest("packing-workflow-test", boxed.version()),
            owner.getId().toString(), List.of("OWNER")
        );

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(duplicate.status()).isEqualTo("COMPLETED");
        assertThat(packingQueries.findDetail(order.getId()).status()).isEqualTo("COMPLETED");
        assertThat(ready.allocations()).allSatisfy(item ->
            assertThat(allocationRepository.findById(item.allocationId()).orElseThrow().getStatus())
                .isEqualTo(InventoryAllocationStatus.CONSUMED)
        );
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM inventory_movements WHERE movement_type = 'PACKING_CONSUMED'",
            Integer.class
        )).isEqualTo(1);
    }
}
