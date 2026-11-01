package com.szr.crosserp.order.repository;

import com.szr.crosserp.order.model.SalesOrder;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {
    Optional<SalesOrder> findByOrderNo(String orderNo);
    Optional<SalesOrder> findByStoreIdAndExternalOrderId(Long storeId, String externalOrderId);

    @Override
    @EntityGraph(attributePaths = {"store", "responsibleUser"})
    Optional<SalesOrder> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"store", "responsibleUser"})
    Page<SalesOrder> findAll(Specification<SalesOrder> specification, Pageable pageable);
}
