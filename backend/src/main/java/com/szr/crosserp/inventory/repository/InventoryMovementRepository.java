package com.szr.crosserp.inventory.repository;

import com.szr.crosserp.inventory.model.InventoryMovement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    Optional<InventoryMovement> findByMovementNo(String movementNo);
    Optional<InventoryMovement> findByIdempotencyKey(String idempotencyKey);
    List<InventoryMovement> findByInventoryLotIdOrderByOccurredAtDescIdDesc(Long inventoryLotId);
}
