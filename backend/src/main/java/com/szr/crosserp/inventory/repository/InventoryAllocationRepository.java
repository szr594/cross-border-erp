package com.szr.crosserp.inventory.repository;

import com.szr.crosserp.inventory.model.InventoryAllocation;
import com.szr.crosserp.inventory.model.InventoryAllocationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryAllocationRepository extends JpaRepository<InventoryAllocation, Long> {
    Optional<InventoryAllocation> findByAllocationNo(String allocationNo);
    List<InventoryAllocation> findByInventoryLotIdOrderByIdAsc(Long inventoryLotId);
    List<InventoryAllocation> findBySalesOrderItemIdOrderByIdAsc(Long salesOrderItemId);
    Optional<InventoryAllocation> findByInventoryLotIdAndPurchaseOrderAllocationId(
        Long inventoryLotId, Long purchaseOrderAllocationId
    );

    @Query("""
        select coalesce(sum(allocation.quantity), 0) from InventoryAllocation allocation
        where allocation.purchaseOrderAllocation.id = :purchaseAllocationId
          and allocation.status in (com.szr.crosserp.inventory.model.InventoryAllocationStatus.RESERVED,
                                    com.szr.crosserp.inventory.model.InventoryAllocationStatus.CONSUMED)
        """)
    long sumActiveQuantityByPurchaseAllocationId(@Param("purchaseAllocationId") Long purchaseAllocationId);

    @Query("""
        select coalesce(sum(allocation.quantity), 0) from InventoryAllocation allocation
        where allocation.salesOrderItem.id = :salesOrderItemId
          and allocation.status in (com.szr.crosserp.inventory.model.InventoryAllocationStatus.RESERVED,
                                    com.szr.crosserp.inventory.model.InventoryAllocationStatus.CONSUMED)
        """)
    long sumActiveQuantityBySalesOrderItemId(@Param("salesOrderItemId") Long salesOrderItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select allocation from InventoryAllocation allocation
        where allocation.salesOrderItem.id = :salesOrderItemId
          and allocation.status in :statuses
        order by allocation.id
        """)
    List<InventoryAllocation> findActiveBySalesOrderItemIdForUpdate(
        @Param("salesOrderItemId") Long salesOrderItemId,
        @Param("statuses") List<InventoryAllocationStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select allocation from InventoryAllocation allocation where allocation.id = :id")
    Optional<InventoryAllocation> findByIdForUpdate(@Param("id") Long id);
}
