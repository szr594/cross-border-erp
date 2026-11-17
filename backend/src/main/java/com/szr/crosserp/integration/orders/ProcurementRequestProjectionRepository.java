package com.szr.crosserp.integration.orders;
import com.szr.crosserp.integration.orders.model.ProcurementRequestProjection;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ProcurementRequestProjectionRepository extends JpaRepository<ProcurementRequestProjection,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ProcurementRequestProjection r join fetch r.orderItem i join fetch i.order where r.id in :ids order by r.id")
    List<ProcurementRequestProjection> findAllForUpdateByIdIn(List<Long> ids);
}
