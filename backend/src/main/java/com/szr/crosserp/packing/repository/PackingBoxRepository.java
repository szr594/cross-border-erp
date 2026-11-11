package com.szr.crosserp.packing.repository;

import com.szr.crosserp.packing.model.PackingBox;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface PackingBoxRepository extends JpaRepository<PackingBox,Long> {
    List<PackingBox> findByTaskIdOrderByIdAsc(Long taskId);
    @EntityGraph(attributePaths="task") Optional<PackingBox> findById(Long id);
}
