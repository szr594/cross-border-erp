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
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from SalesOrder o where o.id=:id")
    Optional<SalesOrder> findByIdForUpdate(Long id);
    Optional<SalesOrder> findByOrderNo(String orderNo);
    Optional<SalesOrder> findByStoreIdAndExternalOrderId(Long storeId, String externalOrderId);

    @Override
    @EntityGraph(attributePaths = {"store", "responsibleUser"})
    Optional<SalesOrder> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"store", "responsibleUser"})
    Page<SalesOrder> findAll(Specification<SalesOrder> specification, Pageable pageable);
}
