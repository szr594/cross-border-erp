package com.szr.crosserp.integration.orders;
import com.szr.crosserp.integration.orders.model.OrderProjection;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface OrderProjectionRepository extends JpaRepository<OrderProjection,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from OrderProjection o where o.id=:id") Optional<OrderProjection> findByIdForUpdate(Long id);
}
