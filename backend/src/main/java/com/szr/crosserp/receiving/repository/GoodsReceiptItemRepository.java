package com.szr.crosserp.receiving.repository;

import com.szr.crosserp.receiving.model.GoodsReceiptItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItem, Long> {
    List<GoodsReceiptItem> findByGoodsReceiptIdOrderByIdAsc(Long goodsReceiptId);
    List<GoodsReceiptItem> findByPurchaseOrderItemIdOrderByIdAsc(Long purchaseOrderItemId);
    List<GoodsReceiptItem> findByGoodsReceiptIdInOrderByIdAsc(List<Long> goodsReceiptIds);
    List<GoodsReceiptItem> findByPurchaseOrderItemIdInOrderByIdAsc(List<Long> purchaseOrderItemIds);
}
