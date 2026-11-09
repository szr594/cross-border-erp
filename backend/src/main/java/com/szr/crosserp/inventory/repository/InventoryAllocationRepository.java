package com.szr.crosserp.inventory.repository;

import com.szr.crosserp.inventory.model.InventoryAllocation;
import com.szr.crosserp.inventory.model.InventoryAllocationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryAllocationRepository extends JpaRepository<InventoryAllocation, Long>, JpaSpecificationExecutor<InventoryAllocation> {
    Optional<InventoryAllocation> findByAllocationNo(String allocationNo);
    @EntityGraph(attributePaths = {"inventoryLot", "salesOrderItem.order"})
    List<InventoryAllocation> findByInventoryLotIdOrderByIdAsc(Long inventoryLotId);

    @EntityGraph(attributePaths = {"inventoryLot", "salesOrderItem.order"})
    List<InventoryAllocation> findBySalesOrderItemIdOrderByIdAsc(Long salesOrderItemId);

    @EntityGraph(attributePaths = {"inventoryLot", "salesOrderItem.order"})
    List<InventoryAllocation> findBySalesOrderItemIdInOrderByIdAsc(List<Long> salesOrderItemIds);

    @Override
    @EntityGraph(attributePaths = {"inventoryLot", "salesOrderItem.order"})
    Page<InventoryAllocation> findAll(
        Specification<InventoryAllocation> specification, Pageable pageable
    );
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
    @Query("""
        select allocation from InventoryAllocation allocation
        where allocation.salesOrderItem.id in :salesOrderItemIds
          and allocation.status in :statuses
        order by allocation.inventoryLot.id, allocation.id
        """)
    List<InventoryAllocation> findBySalesOrderItemIdsAndStatusesForUpdate(
        @Param("salesOrderItemIds") List<Long> salesOrderItemIds,
        @Param("statuses") List<InventoryAllocationStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select allocation from InventoryAllocation allocation where allocation.id = :id")
    Optional<InventoryAllocation> findByIdForUpdate(@Param("id") Long id);
}
