package com.szr.crosserp.inventory.service;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.inventory.dto.InventoryPostingResponse;
import com.szr.crosserp.inventory.model.InventoryLot;
import com.szr.crosserp.inventory.model.InventoryMovement;
import com.szr.crosserp.inventory.model.InventoryMovementType;
import com.szr.crosserp.inventory.model.InventoryQualityStatus;
import com.szr.crosserp.inventory.repository.InventoryLotRepository;
import com.szr.crosserp.inventory.repository.InventoryMovementRepository;
import com.szr.crosserp.receiving.model.GoodsReceipt;
import com.szr.crosserp.receiving.model.GoodsReceiptItem;
import com.szr.crosserp.receiving.repository.GoodsReceiptItemRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptRepository;
import com.szr.crosserp.user.model.User;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryPostingService {
    private final GoodsReceiptRepository receiptRepository;
    private final GoodsReceiptItemRepository receiptItemRepository;
    private final InventoryLotRepository lotRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryAllocationService allocationService;

    public InventoryPostingService(
        GoodsReceiptRepository receiptRepository,
        GoodsReceiptItemRepository receiptItemRepository,
        InventoryLotRepository lotRepository,
        InventoryMovementRepository movementRepository,
        InventoryAllocationService allocationService
    ) {
        this.receiptRepository = receiptRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.lotRepository = lotRepository;
        this.movementRepository = movementRepository;
        this.allocationService = allocationService;
    }

    @Transactional
    public InventoryPostingResponse postReceipt(Long receiptId, User operator) {
        GoodsReceipt receipt = receiptRepository.findByIdForUpdate(receiptId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "GOODS_RECEIPT_NOT_FOUND", "收货批次不存在"
            ));
        List<GoodsReceiptItem> items = receiptItemRepository.findByGoodsReceiptIdOrderByIdAsc(receiptId);
        return postReceipt(receipt, items, operator);
    }

    @Transactional
    public InventoryPostingResponse postReceipt(
        GoodsReceipt receipt, List<GoodsReceiptItem> items, User operator
    ) {
        List<Long> lotIds = new ArrayList<>();
        List<Long> allocationIds = new ArrayList<>();
        items.stream().sorted(Comparator.comparing(GoodsReceiptItem::getId)).forEach(item -> {
            if (item.getAcceptedQuantity() > 0) {
                InventoryLot acceptedLot = postQuality(
                    receipt, item, InventoryQualityStatus.ACCEPTED,
                    item.getAcceptedQuantity(), operator
                );
                lotIds.add(acceptedLot.getId());
                allocationIds.addAll(allocationService.allocateByPurchaseSource(
                    acceptedLot.getId(), operator, receipt.getConfirmedAt()
                ));
            }
            if (item.getDamagedQuantity() > 0) {
                lotIds.add(postQuality(
                    receipt, item, InventoryQualityStatus.DAMAGED,
                    item.getDamagedQuantity(), operator
                ).getId());
            }
        });
        return new InventoryPostingResponse(
            receipt.getId(), true, List.copyOf(lotIds), List.copyOf(allocationIds)
        );
    }

    @Transactional(readOnly = true)
    public boolean isPosted(GoodsReceipt receipt) {
        return receiptItemRepository.findByGoodsReceiptIdOrderByIdAsc(receipt.getId()).stream().allMatch(item ->
            (item.getAcceptedQuantity() == 0 || lotRepository.findByGoodsReceiptItemIdAndQualityStatus(
                item.getId(), InventoryQualityStatus.ACCEPTED
            ).isPresent())
            &&
            (item.getDamagedQuantity() == 0 || lotRepository.findByGoodsReceiptItemIdAndQualityStatus(
                item.getId(), InventoryQualityStatus.DAMAGED
            ).isPresent())
        );
    }

    private InventoryLot postQuality(
        GoodsReceipt receipt, GoodsReceiptItem item, InventoryQualityStatus quality,
        int quantity, User operator
    ) {
        InventoryLot existing = lotRepository.findByGoodsReceiptItemIdAndQualityStatus(item.getId(), quality)
            .orElse(null);
        if (existing != null) {
            return existing;
        }
        String suffix = quality == InventoryQualityStatus.ACCEPTED ? "A" : "D";
        InventoryLot lot = lotRepository.saveAndFlush(new InventoryLot(
            "LOT-RCV-" + item.getId() + "-" + suffix,
            item, item.getPurchaseOrderItem(), quality, quantity, receipt.getConfirmedAt()
        ));
        InventoryMovementType movementType = quality == InventoryQualityStatus.ACCEPTED
            ? InventoryMovementType.RECEIPT_ACCEPTED
            : InventoryMovementType.RECEIPT_DAMAGED;
        movementRepository.save(new InventoryMovement(
            "IMV-RCV-" + item.getId() + "-" + suffix,
            lot, null, movementType, quantity,
            lot.getAvailableQuantity(), lot.getReservedQuantity(), lot.getQuarantinedQuantity(),
            operator, null, "INV-RCV-" + item.getId() + "-" + suffix, receipt.getConfirmedAt()
        ));
        return lot;
    }
}
