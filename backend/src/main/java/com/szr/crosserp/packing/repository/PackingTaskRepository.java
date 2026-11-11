package com.szr.crosserp.packing.repository;

import com.szr.crosserp.packing.model.PackingTask;
import com.szr.crosserp.packing.model.PackingTaskStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PackingTaskRepository extends JpaRepository<PackingTask,Long> {
    @EntityGraph(attributePaths={"order","order.store","startedBy","completedBy"})
    Optional<PackingTask> findByOrderId(Long orderId);
    @EntityGraph(attributePaths={"order","order.store","startedBy","completedBy"})
    List<PackingTask> findAllByOrderByStartedAtDesc();
    long countByStatus(PackingTaskStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select task from PackingTask task where task.id=:id")
    Optional<PackingTask> findByIdForUpdate(@Param("id") Long id);
}
