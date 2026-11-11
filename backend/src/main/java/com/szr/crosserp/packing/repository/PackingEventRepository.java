package com.szr.crosserp.packing.repository;

import com.szr.crosserp.packing.model.PackingEvent;
import java.util.List;
import org.springframework.data.jpa.repository.*;

public interface PackingEventRepository extends JpaRepository<PackingEvent,Long> {
    @EntityGraph(attributePaths="operator") List<PackingEvent> findByTaskIdOrderByOccurredAtDescIdDesc(Long taskId);
}
