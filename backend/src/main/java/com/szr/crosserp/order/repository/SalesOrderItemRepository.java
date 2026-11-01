package com.szr.crosserp.order.repository;

import com.szr.crosserp.order.model.SalesOrderItem;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, Long> {
    Optional<SalesOrderItem> findByOrderIdAndExternalItemId(Long orderId, String externalItemId);
    List<SalesOrderItem> findByOrderIdInOrderByIdAsc(List<Long> orderIds);
}
