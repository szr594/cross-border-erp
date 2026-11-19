package com.szr.crosserp.integration.procurement;

import com.szr.crosserp.integration.procurement.model.PurchaseOrderProjection;
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

public interface PurchaseOrderProjectionRepository extends JpaRepository<PurchaseOrderProjection, Long>, JpaSpecificationExecutor<PurchaseOrderProjection> {
    Optional<PurchaseOrderProjection> findByPurchaseOrderNo(String purchaseOrderNo);

    @Override
    @EntityGraph(attributePaths = {"supplier"})
    Optional<PurchaseOrderProjection> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"supplier"})
    Page<PurchaseOrderProjection> findAll(Specification<PurchaseOrderProjection> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchaseOrder from PurchaseOrderProjection purchaseOrder where purchaseOrder.id = :id")
    Optional<PurchaseOrderProjection> findByIdForUpdate(Long id);
}
