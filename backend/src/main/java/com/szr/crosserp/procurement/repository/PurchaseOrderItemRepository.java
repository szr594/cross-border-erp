package com.szr.crosserp.procurement.repository;

import com.szr.crosserp.procurement.model.PurchaseOrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {
    List<PurchaseOrderItem> findByPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);
}
