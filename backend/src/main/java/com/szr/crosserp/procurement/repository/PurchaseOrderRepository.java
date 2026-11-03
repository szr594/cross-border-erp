package com.szr.crosserp.procurement.repository;

import com.szr.crosserp.procurement.model.PurchaseOrder;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {
    Optional<PurchaseOrder> findByPurchaseOrderNo(String purchaseOrderNo);

    @Override
    @EntityGraph(attributePaths = {"supplier", "createdBy"})
    Optional<PurchaseOrder> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"supplier", "createdBy"})
    Page<PurchaseOrder> findAll(Specification<PurchaseOrder> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchaseOrder from PurchaseOrder purchaseOrder where purchaseOrder.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(Long id);
}
