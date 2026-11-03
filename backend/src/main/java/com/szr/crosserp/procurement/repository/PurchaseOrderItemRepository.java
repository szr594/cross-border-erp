package com.szr.crosserp.procurement.repository;

import com.szr.crosserp.procurement.model.PurchaseOrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {
    List<PurchaseOrderItem> findByPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);
    List<PurchaseOrderItem> findByPurchaseOrderIdInOrderByIdAsc(List<Long> purchaseOrderIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from PurchaseOrderItem item where item.id in :ids order by item.id")
    List<PurchaseOrderItem> findAllForUpdateByIdIn(List<Long> ids);
}
