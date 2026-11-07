package com.szr.crosserp.receiving.repository;

import com.szr.crosserp.receiving.model.GoodsReceipt;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {
    Optional<GoodsReceipt> findByReceiptNo(String receiptNo);
    Optional<GoodsReceipt> findByIdempotencyKey(String idempotencyKey);
    List<GoodsReceipt> findByDomesticPackageIdOrderByConfirmedAtDescIdDesc(Long domesticPackageId);
    List<GoodsReceipt> findByDomesticPackageIdInOrderByConfirmedAtDescIdDesc(List<Long> domesticPackageIds);
    boolean existsByDomesticPackageId(Long domesticPackageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select receipt from GoodsReceipt receipt where receipt.id = :id")
    Optional<GoodsReceipt> findByIdForUpdate(@Param("id") Long id);
}
