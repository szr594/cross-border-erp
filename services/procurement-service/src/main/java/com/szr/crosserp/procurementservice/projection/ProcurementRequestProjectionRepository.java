package com.szr.crosserp.procurementservice.projection;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface ProcurementRequestProjectionRepository extends JpaRepository<ProcurementRequestProjection,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select r from ProcurementRequestProjection r where r.id in :ids order by r.id")
 List<ProcurementRequestProjection> findAllForUpdateByIdIn(@Param("ids") List<Long> ids);
}
