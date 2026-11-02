package com.szr.crosserp.procurement.repository;

import com.szr.crosserp.procurement.model.PurchaseOrderEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderEventRepository extends JpaRepository<PurchaseOrderEvent, Long> {
    List<PurchaseOrderEvent> findByPurchaseOrderIdOrderByOccurredAtDescIdDesc(Long purchaseOrderId);
}
