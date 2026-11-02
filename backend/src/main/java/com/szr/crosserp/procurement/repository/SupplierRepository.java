package com.szr.crosserp.procurement.repository;

import com.szr.crosserp.procurement.model.Supplier;
import com.szr.crosserp.procurement.model.SupplierPlatform;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findByCode(String code);
    Optional<Supplier> findByPlatformAndExternalSupplierId(SupplierPlatform platform, String externalSupplierId);

    @Query("""
        SELECT supplier FROM Supplier supplier
        WHERE (:status IS NULL OR supplier.status = :status)
          AND (:keyword IS NULL OR LOWER(supplier.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(supplier.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    Page<Supplier> search(
        @Param("keyword") String keyword,
        @Param("status") com.szr.crosserp.procurement.model.SupplierStatus status,
        Pageable pageable
    );
}
