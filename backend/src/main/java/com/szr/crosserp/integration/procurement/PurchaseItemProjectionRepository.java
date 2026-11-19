package com.szr.crosserp.integration.procurement;

import com.szr.crosserp.integration.procurement.model.PurchaseItemProjection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface PurchaseItemProjectionRepository extends JpaRepository<PurchaseItemProjection, Long> {
    List<PurchaseItemProjection> findByPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);
    List<PurchaseItemProjection> findByPurchaseOrderIdInOrderByIdAsc(List<Long> purchaseOrderIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from PurchaseItemProjection item where item.id in :ids order by item.id")
    List<PurchaseItemProjection> findAllForUpdateByIdIn(List<Long> ids);
}
