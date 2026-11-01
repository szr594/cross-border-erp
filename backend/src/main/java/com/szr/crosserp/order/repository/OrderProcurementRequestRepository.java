package com.szr.crosserp.order.repository;

import com.szr.crosserp.order.model.OrderProcurementRequest;
import com.szr.crosserp.order.model.ProcurementRequestStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderProcurementRequestRepository extends JpaRepository<OrderProcurementRequest, Long> {
    List<OrderProcurementRequest> findByOrderItemIdAndStatus(Long orderItemId, ProcurementRequestStatus status);
    List<OrderProcurementRequest> findByOrderItemIdIn(List<Long> orderItemIds);
}
