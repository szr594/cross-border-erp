package com.szr.crosserp.inventory.config;

import com.szr.crosserp.inventory.dto.AdjustInventoryRequest;
import com.szr.crosserp.inventory.dto.InventoryAdjustmentType;
import com.szr.crosserp.inventory.dto.ReleaseInventoryAllocationRequest;
import com.szr.crosserp.inventory.model.InventoryAllocationStatus;
import com.szr.crosserp.inventory.model.InventoryQualityStatus;
import com.szr.crosserp.inventory.repository.InventoryAllocationRepository;
import com.szr.crosserp.inventory.repository.InventoryLotRepository;
import com.szr.crosserp.inventory.repository.InventoryMovementRepository;
import com.szr.crosserp.inventory.service.InventoryCommandService;
import com.szr.crosserp.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderRepository;
import com.szr.crosserp.receiving.dto.ConfirmGoodsReceiptRequest;
import com.szr.crosserp.receiving.dto.ConfirmReceiptItemCommand;
import com.szr.crosserp.receiving.dto.CreateDomesticPackageRequest;
import com.szr.crosserp.receiving.repository.DomesticPackageRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptItemRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptRepository;
import com.szr.crosserp.receiving.service.ReceivingCommandService;
import com.szr.crosserp.user.repository.UserRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@Order(50)
public class DemoInventoryInitializer implements ApplicationRunner {
    private static final String TRACKING_NUMBER = "DEMO-SF-20260002";
    private static final String RECEIPT_KEY = "demo-receipt-20260002";
    private static final String RELEASE_KEY = "demo-inventory-release-20260002";
    private static final String ADJUSTMENT_KEY = "demo-inventory-adjustment-20260002";
    private static final String PACKING_TRACKING_NUMBER = "DEMO-SF-PACKING-20260001";
    private static final String PACKING_RECEIPT_KEY = "demo-receipt-packing-20260001";

    private final UserRepository userRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseItemRepository;
    private final DomesticPackageRepository packageRepository;
    private final GoodsReceiptRepository receiptRepository;
    private final GoodsReceiptItemRepository receiptItemRepository;
    private final InventoryLotRepository lotRepository;
    private final InventoryAllocationRepository allocationRepository;
    private final InventoryMovementRepository movementRepository;
    private final ReceivingCommandService receivingCommandService;
    private final InventoryCommandService inventoryCommandService;

    public DemoInventoryInitializer(
        UserRepository userRepository,
        PurchaseOrderRepository purchaseOrderRepository,
        PurchaseOrderItemRepository purchaseItemRepository,
        DomesticPackageRepository packageRepository,
        GoodsReceiptRepository receiptRepository,
        GoodsReceiptItemRepository receiptItemRepository,
        InventoryLotRepository lotRepository,
        InventoryAllocationRepository allocationRepository,
        InventoryMovementRepository movementRepository,
        ReceivingCommandService receivingCommandService,
        InventoryCommandService inventoryCommandService
    ) {
        this.userRepository = userRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseItemRepository = purchaseItemRepository;
        this.packageRepository = packageRepository;
        this.receiptRepository = receiptRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.lotRepository = lotRepository;
        this.allocationRepository = allocationRepository;
        this.movementRepository = movementRepository;
        this.receivingCommandService = receivingCommandService;
        this.inventoryCommandService = inventoryCommandService;
    }

    @Override
    public void run(ApplicationArguments args) {
        var owner = userRepository.findByLoginNameIgnoreCase("owner").orElse(null);
        var purchaseOrder = purchaseOrderRepository.findByPurchaseOrderNo("DEMO-PO-240102").orElse(null);
        if (owner == null || purchaseOrder == null) {
            return;
        }
        var purchaseItems = purchaseItemRepository.findByPurchaseOrderIdOrderByIdAsc(purchaseOrder.getId());
        if (purchaseItems.isEmpty()) {
            return;
        }
        var purchaseItem = purchaseItems.get(0);
        var domesticPackage = packageRepository
            .findByCarrierAndTrackingNumber("SF_EXPRESS", TRACKING_NUMBER)
            .orElseGet(() -> {
                var response = receivingCommandService.register(
                    new CreateDomesticPackageRequest(
                        purchaseOrder.getId(), TRACKING_NUMBER, "SF_EXPRESS", null,
                        "演示：第二批国内包裹"
                    ),
                    owner.getId().toString(), List.of("OWNER")
                );
                return packageRepository.findById(response.id()).orElseThrow();
            });

        if (receiptRepository.findByIdempotencyKey(RECEIPT_KEY).isEmpty()) {
            receivingCommandService.confirm(
                domesticPackage.getId(),
                new ConfirmGoodsReceiptRequest(
                    RECEIPT_KEY, domesticPackage.getVersion(),
                    List.of(new ConfirmReceiptItemCommand(
                        purchaseItem.getId(), 2, 0, "演示：第二批两件合格品"
                    )),
                    List.of(), "演示：第二批扫码收货"
                ),
                owner.getId().toString(), List.of("OWNER")
            );
        }

        var receipt = receiptRepository.findByIdempotencyKey(RECEIPT_KEY).orElseThrow();
        var receiptItems = receiptItemRepository.findByGoodsReceiptIdOrderByIdAsc(receipt.getId());
        if (receiptItems.isEmpty()) {
            return;
        }
        var lot = lotRepository.findByGoodsReceiptItemIdAndQualityStatus(
            receiptItems.get(0).getId(), InventoryQualityStatus.ACCEPTED
        ).orElseThrow();
        var allocations = allocationRepository.findByInventoryLotIdOrderByIdAsc(lot.getId());
        if (!allocations.isEmpty() && allocations.get(0).getStatus() == InventoryAllocationStatus.RESERVED) {
            var allocation = allocations.get(0);
            inventoryCommandService.release(
                allocation.getId(),
                new ReleaseInventoryAllocationRequest(
                    "演示：释放第二批库存供工作台查看", RELEASE_KEY, allocation.getVersion()
                ),
                owner.getId().toString(), List.of("OWNER")
            );
        }

        if (movementRepository.findByIdempotencyKey(ADJUSTMENT_KEY).isEmpty()) {
            var currentLot = lotRepository.findById(lot.getId()).orElseThrow();
            inventoryCommandService.adjust(
                currentLot.getId(),
                new AdjustInventoryRequest(
                    InventoryAdjustmentType.IN, 1, "演示：盘点调增一件",
                    ADJUSTMENT_KEY, currentLot.getVersion()
                ),
                owner.getId().toString(), List.of("OWNER")
            );
        }

        createPackingReadyOrder(owner.getId().toString());
    }

    private void createPackingReadyOrder(String ownerId) {
        var packingPurchaseOrder = purchaseOrderRepository.findByPurchaseOrderNo("DEMO-PO-240101").orElse(null);
        if (packingPurchaseOrder == null) {
            return;
        }
        var packingItems = purchaseItemRepository.findByPurchaseOrderIdOrderByIdAsc(packingPurchaseOrder.getId());
        if (packingItems.isEmpty()) {
            return;
        }
        var domesticPackage = packageRepository
            .findByCarrierAndTrackingNumber("SF_EXPRESS", PACKING_TRACKING_NUMBER)
            .orElseGet(() -> {
                packingPurchaseOrder.updateReceivingStatus(false, false);
                purchaseOrderRepository.saveAndFlush(packingPurchaseOrder);
                var response = receivingCommandService.register(
                    new CreateDomesticPackageRequest(
                        packingPurchaseOrder.getId(), PACKING_TRACKING_NUMBER, "SF_EXPRESS", null,
                        "演示：待打包订单完整到货"
                    ),
                    ownerId, List.of("OWNER")
                );
                return packageRepository.findById(response.id()).orElseThrow();
            });
        if (receiptRepository.findByIdempotencyKey(PACKING_RECEIPT_KEY).isEmpty()) {
            var item = packingItems.get(0);
            receivingCommandService.confirm(
                domesticPackage.getId(),
                new ConfirmGoodsReceiptRequest(
                    PACKING_RECEIPT_KEY, domesticPackage.getVersion(),
                    List.of(new ConfirmReceiptItemCommand(
                        item.getId(), item.getPurchasedQuantity(), 0, "演示：完整收货并自动预留"
                    )),
                    List.of(), "演示：生成待打包订单"
                ),
                ownerId, List.of("OWNER")
            );
        }
    }
}
