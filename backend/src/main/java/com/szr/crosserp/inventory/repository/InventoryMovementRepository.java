package com.szr.crosserp.inventory.repository;

import com.szr.crosserp.inventory.model.InventoryMovement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    Optional<InventoryMovement> findByMovementNo(String movementNo);
    Optional<InventoryMovement> findByIdempotencyKey(String idempotencyKey);
    List<InventoryMovement> findByInventoryLotIdOrderByOccurredAtDescIdDesc(Long inventoryLotId);

    @EntityGraph(attributePaths = {"allocation", "operator"})
    Page<InventoryMovement> findByInventoryLotId(Long inventoryLotId, Pageable pageable);
}
