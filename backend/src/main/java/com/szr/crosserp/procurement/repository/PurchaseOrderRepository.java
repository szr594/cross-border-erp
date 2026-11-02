package com.szr.crosserp.procurement.repository;

import com.szr.crosserp.procurement.model.PurchaseOrder;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    Optional<PurchaseOrder> findByPurchaseOrderNo(String purchaseOrderNo);
}
