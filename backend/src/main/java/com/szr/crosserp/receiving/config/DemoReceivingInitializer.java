package com.szr.crosserp.receiving.config;

import com.szr.crosserp.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderRepository;
import com.szr.crosserp.receiving.dto.ConfirmGoodsReceiptRequest;
import com.szr.crosserp.receiving.dto.ConfirmReceiptItemCommand;
import com.szr.crosserp.receiving.dto.CreateDomesticPackageRequest;
import com.szr.crosserp.receiving.dto.UnexpectedReceiptItemCommand;
import com.szr.crosserp.receiving.repository.DomesticPackageRepository;
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
@Order(40)
public class DemoReceivingInitializer implements ApplicationRunner {
    private static final String TRACKING_NUMBER = "DEMO-SF-20260001";

    private final UserRepository userRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseItemRepository;
    private final DomesticPackageRepository packageRepository;
    private final ReceivingCommandService commandService;

    public DemoReceivingInitializer(
        UserRepository userRepository,
        PurchaseOrderRepository purchaseOrderRepository,
        PurchaseOrderItemRepository purchaseItemRepository,
        DomesticPackageRepository packageRepository,
        ReceivingCommandService commandService
    ) {
        this.userRepository = userRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseItemRepository = purchaseItemRepository;
        this.packageRepository = packageRepository;
        this.commandService = commandService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (packageRepository.findByCarrierAndTrackingNumber("SF_EXPRESS", TRACKING_NUMBER).isPresent()) {
            return;
        }
        var owner = userRepository.findByLoginNameIgnoreCase("owner").orElse(null);
        var purchaseOrder = purchaseOrderRepository.findByPurchaseOrderNo("DEMO-PO-240102").orElse(null);
        if (owner == null || purchaseOrder == null) {
            return;
        }
        var items = purchaseItemRepository.findByPurchaseOrderIdOrderByIdAsc(purchaseOrder.getId());
        if (items.isEmpty()) {
            return;
        }
        var registered = commandService.register(
            new CreateDomesticPackageRequest(
                purchaseOrder.getId(), TRACKING_NUMBER, "SF_EXPRESS", null, "演示：供应商首个国内包裹"
            ),
            owner.getId().toString(), List.of("OWNER")
        );
        commandService.confirm(
            registered.id(),
            new ConfirmGoodsReceiptRequest(
                "demo-receipt-20260001", registered.version(),
                List.of(new ConfirmReceiptItemCommand(
                    items.get(0).getId(), 2, 1, "演示：1 件外包装破损"
                )),
                List.of(new UnexpectedReceiptItemCommand(
                    "演示错发的红色配件", "DEMO-WRONG-RED", 1, "不计入库存"
                )),
                "演示：首批扫码收货"
            ),
            owner.getId().toString(), List.of("OWNER")
        );
    }
}
