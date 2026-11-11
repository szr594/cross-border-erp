package com.szr.crosserp.packing.repository;

import com.szr.crosserp.packing.model.PackingBoxItem;
import java.util.List;
import org.springframework.data.jpa.repository.*;

public interface PackingBoxItemRepository extends JpaRepository<PackingBoxItem,Long> {
    @EntityGraph(attributePaths={"box","orderItem","allocation","allocation.inventoryLot"})
    List<PackingBoxItem> findByBoxTaskIdOrderByBoxIdAscIdAsc(Long taskId);
    void deleteByBoxId(Long boxId);
}
