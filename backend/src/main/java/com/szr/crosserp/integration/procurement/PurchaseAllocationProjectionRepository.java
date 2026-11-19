package com.szr.crosserp.integration.procurement;

import com.szr.crosserp.integration.procurement.model.PurchaseAllocationProjection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface PurchaseAllocationProjectionRepository extends JpaRepository<PurchaseAllocationProjection, Long> {
    List<PurchaseAllocationProjection> findByProcurementRequestId(Long procurementRequestId);

    @EntityGraph(attributePaths = {"purchaseOrderItem", "procurementRequest", "procurementRequest.orderItem", "procurementRequest.orderItem.order"})
    List<PurchaseAllocationProjection> findByPurchaseOrderItemIdIn(List<Long> purchaseOrderItemIds);
}
