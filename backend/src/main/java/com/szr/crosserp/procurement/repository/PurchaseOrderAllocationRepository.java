package com.szr.crosserp.procurement.repository;

import com.szr.crosserp.procurement.model.PurchaseOrderAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface PurchaseOrderAllocationRepository extends JpaRepository<PurchaseOrderAllocation, Long> {
    List<PurchaseOrderAllocation> findByProcurementRequestId(Long procurementRequestId);

    @EntityGraph(attributePaths = {"purchaseOrderItem", "procurementRequest", "procurementRequest.orderItem", "procurementRequest.orderItem.order"})
    List<PurchaseOrderAllocation> findByPurchaseOrderItemIdIn(List<Long> purchaseOrderItemIds);
}
