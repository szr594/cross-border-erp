package com.szr.crosserp.procurement.repository;

import com.szr.crosserp.procurement.model.PurchaseOrderEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

public interface PurchaseOrderEventRepository extends JpaRepository<PurchaseOrderEvent, Long> {
    List<PurchaseOrderEvent> findByPurchaseOrderIdOrderByOccurredAtDescIdDesc(Long purchaseOrderId);

    @EntityGraph(attributePaths = {"operator"})
    Page<PurchaseOrderEvent> findByPurchaseOrderId(Long purchaseOrderId, Pageable pageable);
}
