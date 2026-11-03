package com.szr.crosserp.receiving.repository;

import com.szr.crosserp.receiving.model.UnexpectedReceiptItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnexpectedReceiptItemRepository extends JpaRepository<UnexpectedReceiptItem, Long> {
    List<UnexpectedReceiptItem> findByGoodsReceiptIdOrderByIdAsc(Long goodsReceiptId);
    List<UnexpectedReceiptItem> findByGoodsReceiptIdInOrderByIdAsc(List<Long> goodsReceiptIds);
}
