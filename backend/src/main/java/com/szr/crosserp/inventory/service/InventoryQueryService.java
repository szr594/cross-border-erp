package com.szr.crosserp.inventory.service;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.inventory.dto.InventoryAllocationPageResponse;
import com.szr.crosserp.inventory.dto.InventoryAllocationQuery;
import com.szr.crosserp.inventory.dto.InventoryAllocationResponse;
import com.szr.crosserp.inventory.dto.InventoryLotDetailResponse;
import com.szr.crosserp.inventory.dto.InventoryLotListItemResponse;
import com.szr.crosserp.inventory.dto.InventoryLotPageResponse;
import com.szr.crosserp.inventory.dto.InventoryLotQuery;
import com.szr.crosserp.inventory.dto.InventoryMovementPageResponse;
import com.szr.crosserp.inventory.dto.InventoryMovementResponse;
import com.szr.crosserp.inventory.dto.InventoryProductResponse;
import com.szr.crosserp.inventory.dto.InventoryPurchaseSourceResponse;
import com.szr.crosserp.inventory.dto.InventoryReceiptSourceResponse;
import com.szr.crosserp.inventory.dto.OrderInventoryCoverageResponse;
import com.szr.crosserp.inventory.dto.OrderInventoryItemResponse;
import com.szr.crosserp.inventory.model.InventoryAllocation;
import com.szr.crosserp.inventory.model.InventoryAllocationStatus;
import com.szr.crosserp.inventory.model.InventoryLot;
import com.szr.crosserp.inventory.repository.InventoryAllocationRepository;
import com.szr.crosserp.inventory.repository.InventoryAllocationSpecifications;
import com.szr.crosserp.inventory.repository.InventoryLotRepository;
import com.szr.crosserp.inventory.repository.InventoryLotSpecifications;
import com.szr.crosserp.inventory.repository.InventoryMovementRepository;
import com.szr.crosserp.order.dto.UserSummaryResponse;
import com.szr.crosserp.order.model.SalesOrder;
import com.szr.crosserp.order.model.SalesOrderItem;
import com.szr.crosserp.order.repository.SalesOrderItemRepository;
import com.szr.crosserp.order.repository.SalesOrderRepository;
import com.szr.crosserp.procurement.model.PurchaseOrder;
import com.szr.crosserp.procurement.model.PurchaseOrderItem;
import com.szr.crosserp.receiving.model.DomesticPackage;
import com.szr.crosserp.receiving.model.GoodsReceipt;
import com.szr.crosserp.user.model.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryQueryService {
    private final InventoryLotRepository lotRepository;
    private final InventoryAllocationRepository allocationRepository;
    private final InventoryMovementRepository movementRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;

    public InventoryQueryService(
        InventoryLotRepository lotRepository,
        InventoryAllocationRepository allocationRepository,
        InventoryMovementRepository movementRepository,
        SalesOrderRepository salesOrderRepository,
        SalesOrderItemRepository salesOrderItemRepository
    ) {
        this.lotRepository = lotRepository;
        this.allocationRepository = allocationRepository;
        this.movementRepository = movementRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
    }

    public InventoryLotPageResponse findLots(InventoryLotQuery query) {
        validatePage(query.page(), query.size());
        validateText(query.keyword(), "keyword");
        var result = lotRepository.findAll(
            InventoryLotSpecifications.from(query),
            PageRequest.of(query.page() - 1, query.size(),
                Sort.by(Sort.Direction.DESC, "receivedAt").and(Sort.by(Sort.Direction.DESC, "id")))
        );
        return new InventoryLotPageResponse(
            result.getContent().stream().map(this::lotListItem).toList(),
            query.page(), query.size(), result.getTotalElements(), result.getTotalPages()
        );
    }

    public InventoryLotDetailResponse findLotDetail(Long lotId) {
        InventoryLot lot = requireLot(lotId);
        List<InventoryAllocationResponse> allocations = allocationRepository
            .findByInventoryLotIdOrderByIdAsc(lotId).stream().map(this::allocation).toList();
        GoodsReceipt receipt = lot.getGoodsReceiptItem().getGoodsReceipt();
        DomesticPackage domesticPackage = receipt.getDomesticPackage();
        return new InventoryLotDetailResponse(
            lot.getId(), lot.getLotNo(), product(lot.getPurchaseOrderItem()),
            purchaseSource(lot.getPurchaseOrderItem().getPurchaseOrder()),
            new InventoryReceiptSourceResponse(
                receipt.getId(), receipt.getReceiptNo(), domesticPackage.getId(), domesticPackage.getPackageNo(),
                domesticPackage.getCarrier(), domesticPackage.getTrackingNumber(), receipt.getConfirmedAt()
            ),
            lot.getQualityStatus(), lot.getStatus(), lot.getInitialQuantity(), lot.getAvailableQuantity(),
            lot.getReservedQuantity(), lot.getQuarantinedQuantity(), lot.getConsumedQuantity(),
            lot.getAdjustedOutQuantity(), lot.getVersion(), lot.getReceivedAt(), lot.getCreatedAt(),
            lot.getUpdatedAt(), allocations
        );
    }

    public InventoryMovementPageResponse findMovements(Long lotId, int page, int size) {
        validatePage(page, size);
        requireLot(lotId);
        var result = movementRepository.findByInventoryLotId(
            lotId,
            PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "occurredAt")
                .and(Sort.by(Sort.Direction.DESC, "id")))
        );
        return new InventoryMovementPageResponse(
            result.getContent().stream().map(movement -> new InventoryMovementResponse(
                movement.getId(), movement.getMovementNo(), movement.getMovementType(), movement.getQuantity(),
                movement.getAvailableAfter(), movement.getReservedAfter(), movement.getQuarantinedAfter(),
                movement.getAllocation() == null ? null : movement.getAllocation().getId(),
                movement.getAllocation() == null ? null : movement.getAllocation().getAllocationNo(),
                user(movement.getOperator()), movement.getReason(), movement.getIdempotencyKey(),
                movement.getOccurredAt()
            )).toList(),
            page, size, result.getTotalElements(), result.getTotalPages()
        );
    }

    public InventoryAllocationPageResponse findAllocations(InventoryAllocationQuery query) {
        validatePage(query.page(), query.size());
        validateText(query.orderNo(), "orderNo");
        validateText(query.lotNo(), "lotNo");
        var result = allocationRepository.findAll(
            InventoryAllocationSpecifications.from(query),
            PageRequest.of(query.page() - 1, query.size(), Sort.by(Sort.Direction.DESC, "allocatedAt")
                .and(Sort.by(Sort.Direction.DESC, "id")))
        );
        return new InventoryAllocationPageResponse(
            result.getContent().stream().map(this::allocation).toList(),
            query.page(), query.size(), result.getTotalElements(), result.getTotalPages()
        );
    }

    public OrderInventoryCoverageResponse findOrderCoverage(Long orderId) {
        SalesOrder order = salesOrderRepository.findById(orderId).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在")
        );
        List<SalesOrderItem> items = salesOrderItemRepository.findByOrderIdOrderByIdAsc(orderId);
        List<Long> itemIds = items.stream().map(SalesOrderItem::getId).toList();
        Map<Long, List<InventoryAllocation>> allocationsByItem = new HashMap<>();
        if (!itemIds.isEmpty()) {
            allocationRepository.findBySalesOrderItemIdInOrderByIdAsc(itemIds).forEach(allocation ->
                allocationsByItem.computeIfAbsent(
                    allocation.getSalesOrderItem().getId(), ignored -> new ArrayList<>()
                ).add(allocation)
            );
        }
        return new OrderInventoryCoverageResponse(
            order.getId(), order.getOrderNo(), items.stream().map(item -> {
                List<InventoryAllocation> itemAllocations = allocationsByItem.getOrDefault(item.getId(), List.of());
                int reserved = quantity(itemAllocations, InventoryAllocationStatus.RESERVED);
                int consumed = quantity(itemAllocations, InventoryAllocationStatus.CONSUMED);
                return new OrderInventoryItemResponse(
                    item.getId(), item.getExternalSku(), item.getProductName(), item.getVariantName(),
                    item.getQuantity(), reserved, consumed,
                    Math.max(item.getQuantity() - reserved - consumed, 0),
                    itemAllocations.stream().map(this::allocation).toList()
                );
            }).toList()
        );
    }

    private int quantity(List<InventoryAllocation> allocations, InventoryAllocationStatus status) {
        return allocations.stream().filter(allocation -> allocation.getStatus() == status)
            .mapToInt(InventoryAllocation::getQuantity).sum();
    }

    private InventoryLotListItemResponse lotListItem(InventoryLot lot) {
        return new InventoryLotListItemResponse(
            lot.getId(), lot.getLotNo(), product(lot.getPurchaseOrderItem()),
            purchaseSource(lot.getPurchaseOrderItem().getPurchaseOrder()), lot.getQualityStatus(), lot.getStatus(),
            lot.getInitialQuantity(), lot.getAvailableQuantity(), lot.getReservedQuantity(),
            lot.getQuarantinedQuantity(), lot.getConsumedQuantity(), lot.getAdjustedOutQuantity(), lot.getReceivedAt()
        );
    }

    private InventoryProductResponse product(PurchaseOrderItem item) {
        return new InventoryProductResponse(
            item.getId(), item.getSupplierSku(), item.getProductName(), item.getVariantName(), item.getImageUrl()
        );
    }

    private InventoryPurchaseSourceResponse purchaseSource(PurchaseOrder order) {
        return new InventoryPurchaseSourceResponse(
            order.getId(), order.getPurchaseOrderNo(), order.getStatus(), order.getSupplier().getId(),
            order.getSupplier().getCode(), order.getSupplier().getName()
        );
    }

    private InventoryAllocationResponse allocation(InventoryAllocation allocation) {
        SalesOrderItem item = allocation.getSalesOrderItem();
        return new InventoryAllocationResponse(
            allocation.getId(), allocation.getAllocationNo(), allocation.getInventoryLot().getId(),
            allocation.getInventoryLot().getLotNo(), item.getOrder().getId(), item.getOrder().getOrderNo(),
            item.getId(), item.getProductName(), item.getVariantName(), allocation.getQuantity(),
            allocation.getStatus(), allocation.getAllocatedAt()
        );
    }

    private UserSummaryResponse user(User user) {
        return user == null ? null : new UserSummaryResponse(user.getId(), user.getLoginName(), user.getDisplayName());
    }

    private InventoryLot requireLot(Long lotId) {
        return lotRepository.findById(lotId).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, "INVENTORY_LOT_NOT_FOUND", "库存批次不存在")
        );
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw invalid("分页参数无效");
        }
    }

    private void validateText(String value, String field) {
        if (value != null && value.trim().length() > 200) {
            throw invalid(field + " 长度不能超过200个字符");
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVENTORY_QUERY_INVALID", message);
    }
}
