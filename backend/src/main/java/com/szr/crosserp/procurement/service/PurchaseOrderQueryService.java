package com.szr.crosserp.procurement.service;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.order.dto.UserSummaryResponse;
import com.szr.crosserp.procurement.dto.PurchaseOrderDetailItemResponse;
import com.szr.crosserp.procurement.dto.PurchaseOrderDetailResponse;
import com.szr.crosserp.procurement.dto.PurchaseOrderEventPageResponse;
import com.szr.crosserp.procurement.dto.PurchaseOrderEventResponse;
import com.szr.crosserp.procurement.dto.PurchaseOrderListItemResponse;
import com.szr.crosserp.procurement.dto.PurchaseOrderPageResponse;
import com.szr.crosserp.procurement.dto.PurchaseOrderQuery;
import com.szr.crosserp.procurement.dto.PurchaseSourceResponse;
import com.szr.crosserp.procurement.dto.PurchaseSupplierSummaryResponse;
import com.szr.crosserp.procurement.dto.SupplierResponse;
import com.szr.crosserp.procurement.model.PurchaseOrder;
import com.szr.crosserp.procurement.model.PurchaseOrderAllocation;
import com.szr.crosserp.procurement.model.PurchaseOrderItem;
import com.szr.crosserp.procurement.model.Supplier;
import com.szr.crosserp.procurement.repository.PurchaseOrderAllocationRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderEventRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderSpecifications;
import com.szr.crosserp.user.model.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PurchaseOrderQueryService {
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "purchaseOrderNo", "totalAmountMinor", "status");

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderItemRepository itemRepository;
    private final PurchaseOrderAllocationRepository allocationRepository;
    private final PurchaseOrderEventRepository eventRepository;

    public PurchaseOrderQueryService(
        PurchaseOrderRepository orderRepository,
        PurchaseOrderItemRepository itemRepository,
        PurchaseOrderAllocationRepository allocationRepository,
        PurchaseOrderEventRepository eventRepository
    ) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.allocationRepository = allocationRepository;
        this.eventRepository = eventRepository;
    }

    public PurchaseOrderPageResponse findOrders(PurchaseOrderQuery query) {
        validate(query);
        Page<PurchaseOrder> result = orderRepository.findAll(
            PurchaseOrderSpecifications.from(query),
            PageRequest.of(query.page() - 1, query.size(), parseSort(query.sort()))
        );
        List<Long> orderIds = result.getContent().stream().map(PurchaseOrder::getId).toList();
        Map<Long, List<PurchaseOrderItem>> itemsByOrder = new HashMap<>();
        if (!orderIds.isEmpty()) {
            itemRepository.findByPurchaseOrderIdInOrderByIdAsc(orderIds).forEach(item ->
                itemsByOrder.computeIfAbsent(item.getPurchaseOrder().getId(), ignored -> new ArrayList<>()).add(item)
            );
        }
        return new PurchaseOrderPageResponse(
            result.getContent().stream().map(order -> {
                List<PurchaseOrderItem> items = itemsByOrder.getOrDefault(order.getId(), List.of());
                return new PurchaseOrderListItemResponse(
                    order.getId(), order.getPurchaseOrderNo(), supplierSummary(order.getSupplier()),
                    order.getStatus(), order.getCurrency(), order.getTotalAmountMinor(), items.size(),
                    items.stream().mapToInt(PurchaseOrderItem::getPurchasedQuantity).sum(), order.getCreatedAt()
                );
            }).toList(),
            query.page(), query.size(), result.getTotalElements(), result.getTotalPages()
        );
    }

    public PurchaseOrderDetailResponse findDetail(Long orderId) {
        PurchaseOrder order = requireOrder(orderId);
        List<PurchaseOrderItem> items = itemRepository.findByPurchaseOrderIdOrderByIdAsc(orderId);
        List<Long> itemIds = items.stream().map(PurchaseOrderItem::getId).toList();
        Map<Long, List<PurchaseOrderAllocation>> allocationsByItem = new HashMap<>();
        if (!itemIds.isEmpty()) {
            allocationRepository.findByPurchaseOrderItemIdIn(itemIds).forEach(allocation ->
                allocationsByItem.computeIfAbsent(
                    allocation.getPurchaseOrderItem().getId(), ignored -> new ArrayList<>()
                ).add(allocation)
            );
        }
        return new PurchaseOrderDetailResponse(
            order.getId(), order.getPurchaseOrderNo(), supplier(order.getSupplier()), order.getStatus(),
            order.getExternalOrderNo(), order.getCurrency(), order.getTotalAmountMinor(), order.getOrderedAt(),
            order.getExpectedArrivalAt(), user(order.getCreatedBy()), order.getNote(), order.getVersion(),
            order.getCreatedAt(), order.getUpdatedAt(),
            items.stream().map(item -> detailItem(
                item, allocationsByItem.getOrDefault(item.getId(), List.of())
            )).toList()
        );
    }

    public PurchaseOrderEventPageResponse findEvents(Long orderId, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw invalid("事件分页参数无效");
        }
        requireOrder(orderId);
        var result = eventRepository.findByPurchaseOrderId(
            orderId,
            PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "occurredAt").and(
                Sort.by(Sort.Direction.DESC, "id")
            ))
        );
        return new PurchaseOrderEventPageResponse(
            result.getContent().stream().map(event -> new PurchaseOrderEventResponse(
                event.getId(), event.getEventType(), event.getFromValue(), event.getToValue(),
                user(event.getOperator()), event.getRemark(), event.getOccurredAt()
            )).toList(),
            page, size, result.getTotalElements(), result.getTotalPages()
        );
    }

    private PurchaseOrderDetailItemResponse detailItem(
        PurchaseOrderItem item, List<PurchaseOrderAllocation> allocations
    ) {
        return new PurchaseOrderDetailItemResponse(
            item.getId(), item.getSupplierProductId(), item.getSupplierSku(), item.getProductName(),
            item.getVariantName(), item.getProductUrl(), item.getImageUrl(), item.getPurchasedQuantity(),
            item.getReceivedQuantity(), item.getUnitPriceMinor(), item.getCurrency(), item.getNote(), item.getVersion(),
            allocations.stream().map(allocation -> {
                var request = allocation.getProcurementRequest();
                var salesItem = request.getOrderItem();
                var salesOrder = salesItem.getOrder();
                return new PurchaseSourceResponse(
                    allocation.getId(), request.getId(), allocation.getAllocatedQuantity(), request.getStatus(),
                    salesOrder.getId(), salesOrder.getOrderNo(), salesItem.getId(),
                    salesItem.getProductName(), salesItem.getVariantName()
                );
            }).toList()
        );
    }

    private void validate(PurchaseOrderQuery query) {
        if (query.page() < 1 || query.size() < 1 || query.size() > 100) {
            throw invalid("分页参数无效");
        }
        if (query.keyword() != null && query.keyword().trim().length() > 200) {
            throw invalid("keyword 长度不能超过200个字符");
        }
    }

    private Sort parseSort(String value) {
        String normalized = value == null || value.isBlank() ? "createdAt,desc" : value.trim();
        String[] parts = normalized.split(",", -1);
        if (parts.length != 2 || !SORT_FIELDS.contains(parts[0])) {
            throw invalid("sort 字段无效");
        }
        try {
            return Sort.by(Sort.Direction.fromString(parts[1].toUpperCase(Locale.ROOT)), parts[0])
                .and(Sort.by(Sort.Direction.DESC, "id"));
        } catch (IllegalArgumentException exception) {
            throw invalid("sort 方向必须是 asc 或 desc");
        }
    }

    private PurchaseOrder requireOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PURCHASE_ORDER_NOT_FOUND", "采购单不存在"));
    }

    private PurchaseSupplierSummaryResponse supplierSummary(Supplier supplier) {
        return new PurchaseSupplierSummaryResponse(supplier.getId(), supplier.getCode(), supplier.getName());
    }

    private SupplierResponse supplier(Supplier supplier) {
        return new SupplierResponse(
            supplier.getId(), supplier.getCode(), supplier.getName(), supplier.getPlatform(),
            supplier.getExternalSupplierId(), supplier.getContactName(), supplier.getContactPhone(),
            supplier.getShopUrl(), supplier.getNote(), supplier.getStatus(), supplier.getVersion(),
            supplier.getCreatedAt(), supplier.getUpdatedAt()
        );
    }

    private UserSummaryResponse user(User user) {
        return user == null ? null : new UserSummaryResponse(user.getId(), user.getLoginName(), user.getDisplayName());
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "PURCHASE_ORDER_QUERY_INVALID", message);
    }
}
