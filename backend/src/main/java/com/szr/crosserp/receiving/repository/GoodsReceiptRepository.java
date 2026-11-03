package com.szr.crosserp.receiving.repository;

import com.szr.crosserp.receiving.model.GoodsReceipt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {
    Optional<GoodsReceipt> findByReceiptNo(String receiptNo);
    Optional<GoodsReceipt> findByIdempotencyKey(String idempotencyKey);
    List<GoodsReceipt> findByDomesticPackageIdOrderByConfirmedAtDescIdDesc(Long domesticPackageId);
    List<GoodsReceipt> findByDomesticPackageIdInOrderByConfirmedAtDescIdDesc(List<Long> domesticPackageIds);
    boolean existsByDomesticPackageId(Long domesticPackageId);
}
