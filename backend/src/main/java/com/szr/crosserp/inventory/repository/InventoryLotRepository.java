package com.szr.crosserp.inventory.repository;

import com.szr.crosserp.inventory.model.InventoryLot;
import com.szr.crosserp.inventory.model.InventoryQualityStatus;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long> {
    Optional<InventoryLot> findByLotNo(String lotNo);
    Optional<InventoryLot> findByGoodsReceiptItemIdAndQualityStatus(
        Long goodsReceiptItemId, InventoryQualityStatus qualityStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lot from InventoryLot lot where lot.id = :id")
    Optional<InventoryLot> findByIdForUpdate(@Param("id") Long id);
}
