package com.szr.crosserp.order.repository;

import com.szr.crosserp.order.model.OrderStatusEvent;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface OrderStatusEventRepository extends JpaRepository<OrderStatusEvent, Long> {
    List<OrderStatusEvent> findByOrderIdOrderByOccurredAtDescIdDesc(Long orderId);

    @EntityGraph(attributePaths = "operator")
    Page<OrderStatusEvent> findByOrderId(Long orderId, Pageable pageable);
}
