package com.szr.crosserp.integration.orders;
import com.szr.crosserp.integration.orders.model.OrderItemProjection;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface OrderItemProjectionRepository extends JpaRepository<OrderItemProjection,Long> {
    List<OrderItemProjection> findByOrderIdOrderByIdAsc(Long id);
    List<OrderItemProjection> findByOrderIdInOrderByIdAsc(List<Long> ids);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from OrderItemProjection i where i.id=:id") Optional<OrderItemProjection> findByIdForUpdate(Long id);
}
