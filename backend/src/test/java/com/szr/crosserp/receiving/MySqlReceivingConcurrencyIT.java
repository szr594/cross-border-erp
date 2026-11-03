package com.szr.crosserp.receiving;

import static org.assertj.core.api.Assertions.assertThat;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.procurement.model.PurchaseOrder;
import com.szr.crosserp.procurement.model.PurchaseOrderItem;
import com.szr.crosserp.procurement.model.Supplier;
import com.szr.crosserp.procurement.model.SupplierPlatform;
import com.szr.crosserp.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderRepository;
import com.szr.crosserp.procurement.repository.SupplierRepository;
import com.szr.crosserp.receiving.dto.ConfirmGoodsReceiptRequest;
import com.szr.crosserp.receiving.dto.ConfirmReceiptItemCommand;
import com.szr.crosserp.receiving.model.DomesticPackage;
import com.szr.crosserp.receiving.repository.DomesticPackageRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptRepository;
import com.szr.crosserp.receiving.service.ReceivingCommandService;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("mysql-integration")
class MySqlReceivingConcurrencyIT {
    @Autowired ReceivingCommandService commandService;
    @Autowired UserRepository userRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Autowired DomesticPackageRepository packageRepository;
    @Autowired GoodsReceiptRepository receiptRepository;

    @Test
    void concurrentReceiptsCannotReceiveMoreThanPurchasedQuantity() throws Exception {
        String suffix = suffix();
        User warehouse = userRepository.save(new User(
            "mysql-receiver-" + suffix, "并发仓库", "not-used-in-this-test", false
        ));
        Supplier supplier = supplierRepository.save(new Supplier(
            "MYSQL-RCV-" + suffix, "并发供应商", SupplierPlatform.MANUAL
        ));
        PurchaseOrder order = purchaseOrderRepository.save(new PurchaseOrder(
            "MYSQL-PO-" + suffix, supplier, "CNY", warehouse
        ));
        order.updateReceivingStatus(false, false);
        purchaseOrderRepository.save(order);
        PurchaseOrderItem item = purchaseOrderItemRepository.save(new PurchaseOrderItem(
            order, "并发商品", 1, "CNY"
        ));
        DomesticPackage domesticPackage = packageRepository.save(new DomesticPackage(
            "MYSQL-PKG-" + suffix, order, "MYSQL-TRACK-" + suffix, "SF_EXPRESS", warehouse
        ));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Result> first = executor.submit(() -> receive(
                domesticPackage.getId(), item.getId(), warehouse.getId(), "mysql-idem-a-" + suffix, ready, start
            ));
            Future<Result> second = executor.submit(() -> receive(
                domesticPackage.getId(), item.getId(), warehouse.getId(), "mysql-idem-b-" + suffix, ready, start
            ));
            ready.await();
            start.countDown();

            List<Result> results = List.of(first.get(), second.get());
            assertThat(results.stream().filter(Result::success).count()).isEqualTo(1);
            assertThat(results.stream().filter(result -> !result.success()).map(Result::code).toList())
                .allMatch(code -> code.equals("RECEIPT_VERSION_CONFLICT") || code.equals("RECEIPT_QUANTITY_CONFLICT"));
        } finally {
            executor.shutdownNow();
        }

        assertThat(purchaseOrderItemRepository.findById(item.getId()).orElseThrow().getReceivedQuantity()).isEqualTo(1);
        assertThat(receiptRepository.findAll().stream()
            .filter(receipt -> receipt.getDomesticPackage().getId().equals(domesticPackage.getId())).count()).isEqualTo(1);
    }

    private Result receive(
        Long packageId, Long itemId, Long userId, String idempotencyKey,
        CountDownLatch ready, CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            commandService.confirm(
                packageId,
                new ConfirmGoodsReceiptRequest(
                    idempotencyKey, 0,
                    List.of(new ConfirmReceiptItemCommand(itemId, 1, 0, null)),
                    List.of(), null
                ),
                userId.toString(), List.of("WAREHOUSE")
            );
            return new Result(true, null);
        } catch (ApiException exception) {
            return new Result(false, exception.getCode());
        }
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record Result(boolean success, String code) {
    }
}
