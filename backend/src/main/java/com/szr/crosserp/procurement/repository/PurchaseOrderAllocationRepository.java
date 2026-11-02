package com.szr.crosserp.procurement.repository;

import com.szr.crosserp.procurement.model.PurchaseOrderAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderAllocationRepository extends JpaRepository<PurchaseOrderAllocation, Long> {
    List<PurchaseOrderAllocation> findByProcurementRequestId(Long procurementRequestId);
}
