package com.szr.crosserp.inventory.repository;

import com.szr.crosserp.inventory.model.InventoryMovement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    Optional<InventoryMovement> findByMovementNo(String movementNo);
    Optional<InventoryMovement> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select movement from InventoryMovement movement where movement.idempotencyKey = :key")
    Optional<InventoryMovement> findByIdempotencyKeyForUpdate(@Param("key") String key);
    List<InventoryMovement> findByInventoryLotIdOrderByOccurredAtDescIdDesc(Long inventoryLotId);

    @EntityGraph(attributePaths = {"allocation", "operator"})
    Page<InventoryMovement> findByInventoryLotId(Long inventoryLotId, Pageable pageable);
}
