package com.szr.crosserp.inventory.repository;

import com.szr.crosserp.inventory.model.InventoryLot;
import com.szr.crosserp.inventory.model.InventoryQualityStatus;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long>, JpaSpecificationExecutor<InventoryLot> {
    Optional<InventoryLot> findByLotNo(String lotNo);
    Optional<InventoryLot> findByGoodsReceiptItemIdAndQualityStatus(
        Long goodsReceiptItemId, InventoryQualityStatus qualityStatus
    );

    @Override
    @EntityGraph(attributePaths = {
        "purchaseOrderItem.purchaseOrder.supplier",
        "goodsReceiptItem.goodsReceipt.domesticPackage"
    })
    Optional<InventoryLot> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"purchaseOrderItem.purchaseOrder.supplier"})
    Page<InventoryLot> findAll(Specification<InventoryLot> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lot from InventoryLot lot where lot.id = :id")
    Optional<InventoryLot> findByIdForUpdate(@Param("id") Long id);
}
