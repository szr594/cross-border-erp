package com.szr.crosserp.order.repository;

import com.szr.crosserp.order.model.SalesOrderItem;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, Long> {
    Optional<SalesOrderItem> findByOrderIdAndExternalItemId(Long orderId, String externalItemId);
    List<SalesOrderItem> findByOrderIdInOrderByIdAsc(List<Long> orderIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from SalesOrderItem item join fetch item.order where item.id = :id")
    Optional<SalesOrderItem> findByIdForUpdate(@Param("id") Long id);
}
