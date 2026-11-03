package com.szr.crosserp.receiving.repository;

import com.szr.crosserp.receiving.model.DomesticPackage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface DomesticPackageRepository extends JpaRepository<DomesticPackage, Long>, JpaSpecificationExecutor<DomesticPackage> {
    Optional<DomesticPackage> findByPackageNo(String packageNo);
    Optional<DomesticPackage> findByCarrierAndTrackingNumber(String carrier, String trackingNumber);
    List<DomesticPackage> findByTrackingNumberIgnoreCase(String trackingNumber);
    List<DomesticPackage> findByPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);

    @Override
    @EntityGraph(attributePaths = {"purchaseOrder", "purchaseOrder.supplier", "createdBy"})
    Optional<DomesticPackage> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select domesticPackage from DomesticPackage domesticPackage where domesticPackage.id = :id")
    Optional<DomesticPackage> findByIdForUpdate(Long id);
}
