package com.szr.crosserp.order.service;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.order.dto.CustomerResponse;
import com.szr.crosserp.order.dto.OrderDetailResponse;
import com.szr.crosserp.order.dto.NamedOptionResponse;
import com.szr.crosserp.order.dto.OrderEventPageResponse;
import com.szr.crosserp.order.dto.OrderEventResponse;
import com.szr.crosserp.order.dto.OrderFilterOptionsResponse;
import com.szr.crosserp.order.dto.OrderFulfillmentResponse;
import com.szr.crosserp.order.dto.OrderItemResponse;
import com.szr.crosserp.order.dto.OrderListItemResponse;
import com.szr.crosserp.order.dto.OrderPageResponse;
import com.szr.crosserp.order.dto.OrderProcurementRequestDetailResponse;
import com.szr.crosserp.order.dto.OrderProcurementRequestPageResponse;
import com.szr.crosserp.order.dto.ProcurementOrderItemResponse;
import com.szr.crosserp.order.dto.OrderQuery;
import com.szr.crosserp.order.dto.OrderSummaryResponse;
import com.szr.crosserp.order.dto.StoreSummaryResponse;
import com.szr.crosserp.order.dto.UserSummaryResponse;
import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.model.FulfillmentStage;
import com.szr.crosserp.order.model.OrderProcurementRequest;
import com.szr.crosserp.order.model.OrderItemFulfillmentStatus;
import com.szr.crosserp.order.model.ProcurementRequestStatus;
import com.szr.crosserp.order.model.SalesOrder;
import com.szr.crosserp.order.model.SalesOrderItem;
import com.szr.crosserp.order.repository.OrderProcurementRequestRepository;
import com.szr.crosserp.order.repository.OrderStatusEventRepository;
import com.szr.crosserp.order.repository.OrderSpecifications;
import com.szr.crosserp.order.repository.SalesOrderItemRepository;
import com.szr.crosserp.order.repository.SalesOrderRepository;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.UserRepository;
import com.szr.crosserp.store.repository.SalesStoreRepository;
import java.util.ArrayList;
import java.util.Collection;
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
public class OrderQueryService {

    private static final Set<String> SORT_FIELDS = Set.of(
        "requestedShipAt", "orderedAt", "createdAt", "updatedAt", "orderNo"
    );

    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final OrderProcurementRequestRepository requestRepository;
    private final OrderStatusEventRepository eventRepository;
    private final SalesStoreRepository storeRepository;
    private final UserRepository userRepository;
    private final FulfillmentStageCalculator stageCalculator;

    public OrderQueryService(
        SalesOrderRepository orderRepository,
        SalesOrderItemRepository itemRepository,
        OrderProcurementRequestRepository requestRepository,
        OrderStatusEventRepository eventRepository,
        SalesStoreRepository storeRepository,
        UserRepository userRepository,
        FulfillmentStageCalculator stageCalculator
    ) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.requestRepository = requestRepository;
        this.eventRepository = eventRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.stageCalculator = stageCalculator;
    }

    public OrderPageResponse findOrders(OrderQuery request) {
        validate(request);
        Page<SalesOrder> page = orderRepository.findAll(
            OrderSpecifications.from(request),
            PageRequest.of(request.page() - 1, request.size(), parseSort(request.sort()))
        );
        List<Long> orderIds = page.getContent().stream().map(SalesOrder::getId).toList();
        Map<Long, List<SalesOrderItem>> itemsByOrder = itemsByOrder(orderIds);
        Map<Long, List<OrderProcurementRequest>> requestsByItem = requestsByItem(itemsByOrder.values());

        List<OrderListItemResponse> items = page.getContent().stream()
            .map(order -> toListItem(
                order,
                itemsByOrder.getOrDefault(order.getId(), List.of()),
                requestsByItem
            ))
            .toList();

        return new OrderPageResponse(
            items,
            request.page(),
            request.size(),
            page.getTotalElements(),
            page.getTotalPages(),
            summary(request)
        );
    }

    public OrderDetailResponse findDetail(Long orderId, Collection<String> roles) {
        SalesOrder order = requireOrder(orderId);
        List<SalesOrderItem> items = itemRepository.findByOrderIdInOrderByIdAsc(List.of(orderId));
        Map<Long, List<OrderProcurementRequest>> requestsByItem = requestsByItem(List.of(items));
        FulfillmentStage stage = calculateStage(order, items, requestsByItem);
        boolean procurementView = roles != null && roles.contains("PROCUREMENT");

        return new OrderDetailResponse(
            order.getId(),
            order.getOrderNo(),
            order.getExternalOrderId(),
            order.getDisplayOrderNo(),
            store(order),
            user(order.getResponsibleUser()),
            order.getExternalStatus(),
            order.getExternalStatusRaw(),
            stage,
            stage.getDisplayName(),
            order.isFulfillmentException(),
            order.getExceptionReason(),
            order.getCurrency(),
            order.getOrderedAt(),
            order.getShipAfter(),
            order.getRequestedShipAt(),
            customer(order, procurementView),
            order.getNote(),
            order.getVersion(),
            items.stream().map(item -> toItem(item, requestsByItem.getOrDefault(item.getId(), List.of()))).toList(),
            availableActions(order, roles == null ? List.of() : roles)
        );
    }

    public OrderFilterOptionsResponse findFilterOptions() {
        List<StoreSummaryResponse> stores = storeRepository.findAllByOrderByNameAsc().stream()
            .map(store -> new StoreSummaryResponse(store.getId(), store.getCode(), store.getName()))
            .toList();
        List<UserSummaryResponse> users = userRepository.findAll().stream()
            .sorted((left, right) -> left.getDisplayName().compareToIgnoreCase(right.getDisplayName()))
            .map(this::user)
            .toList();
        List<NamedOptionResponse> statuses = List.of(ExternalOrderStatus.values()).stream()
            .map(status -> new NamedOptionResponse(status.name(), externalStatusName(status)))
            .toList();
        List<NamedOptionResponse> stages = new ArrayList<>();
        stages.add(new NamedOptionResponse("ACTIONABLE", "可处理订单"));
        stages.addAll(List.of(FulfillmentStage.values()).stream()
            .map(stage -> new NamedOptionResponse(stage.name(), stage.getDisplayName()))
            .toList());
        return new OrderFilterOptionsResponse(stores, users, statuses, List.copyOf(stages));
    }

    public OrderFulfillmentResponse findFulfillment(Long orderId) {
        SalesOrder order = requireOrder(orderId);
        List<SalesOrderItem> items = itemRepository.findByOrderIdInOrderByIdAsc(List.of(orderId));
        Map<Long, List<OrderProcurementRequest>> requestsByItem = requestsByItem(List.of(items));
        FulfillmentStage stage = calculateStage(order, items, requestsByItem);
        return new OrderFulfillmentResponse(
            orderId,
            stage,
            stage.getDisplayName(),
            items.stream().map(item -> toItem(item, requestsByItem.getOrDefault(item.getId(), List.of()))).toList()
        );
    }

    public OrderEventPageResponse findEvents(Long orderId, int page, int size, Collection<String> roles) {
        if (page < 1 || size < 1 || size > 100) {
            throw invalid("事件分页参数无效");
        }
        requireOrder(orderId);
        boolean procurementView = roles != null && roles.contains("PROCUREMENT");
        Page<com.szr.crosserp.order.model.OrderStatusEvent> result = eventRepository.findByOrderId(
            orderId,
            PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "occurredAt").and(
                Sort.by(Sort.Direction.DESC, "id")
            ))
        );
        return new OrderEventPageResponse(
            result.getContent().stream().map(event -> new OrderEventResponse(
                event.getId(),
                event.getEventType(),
                procurementView ? null : event.getFromValue(),
                procurementView ? null : event.getToValue(),
                user(event.getOperator()),
                event.getSource(),
                procurementView ? null : event.getRemark(),
                event.getOccurredAt()
            )).toList(),
            page,
            size,
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    public OrderProcurementRequestPageResponse findProcurementRequests(
        Long orderId, int page, int size, Collection<String> roles
    ) {
        if (page < 1 || size < 1 || size > 100) {
            throw invalid("采购申请分页参数无效");
        }
        boolean allowed = roles != null && roles.stream()
            .anyMatch(Set.of("OWNER", "OPERATIONS", "PROCUREMENT")::contains);
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ORDER_COMMAND_FORBIDDEN", "无权查看采购申请");
        }
        requireOrder(orderId);
        Page<OrderProcurementRequest> result = requestRepository.findByOrderItemOrderId(
            orderId,
            PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "requestedAt").and(
                Sort.by(Sort.Direction.DESC, "id")
            ))
        );
        return new OrderProcurementRequestPageResponse(
            result.getContent().stream().map(request -> new OrderProcurementRequestDetailResponse(
                request.getId(),
                new ProcurementOrderItemResponse(
                    request.getOrderItem().getId(), request.getOrderItem().getExternalSku(),
                    request.getOrderItem().getProductName(), request.getOrderItem().getVariantName()
                ),
                request.getRequestedQuantity(), request.getStatus(), user(request.getRequestedBy()),
                request.getRequestedAt(), user(request.getWithdrawnBy()), request.getWithdrawnAt(),
                request.getNote(), request.getVersion(), request.getStatus() == ProcurementRequestStatus.REQUESTED
            )).toList(),
            page, size, result.getTotalElements(), result.getTotalPages()
        );
    }

    private OrderListItemResponse toListItem(
        SalesOrder order,
        List<SalesOrderItem> items,
        Map<Long, List<OrderProcurementRequest>> requestsByItem
    ) {
        FulfillmentStage stage = calculateStage(order, items, requestsByItem);
        return new OrderListItemResponse(
            order.getId(),
            order.getOrderNo(),
            order.getDisplayOrderNo(),
            store(order),
            user(order.getResponsibleUser()),
            order.getExternalStatus(),
            stage,
            stage.getDisplayName(),
            order.isFulfillmentException(),
            order.getOrderedAt(),
            order.getRequestedShipAt(),
            items.size(),
            items.stream().mapToInt(SalesOrderItem::getQuantity).sum(),
            items.stream().map(SalesOrderItem::getProductName).distinct().limit(3).toList()
        );
    }

    private OrderItemResponse toItem(SalesOrderItem item, List<OrderProcurementRequest> requests) {
        int requested = quantity(requests, ProcurementRequestStatus.REQUESTED);
        int inProgress = quantity(requests, ProcurementRequestStatus.CONVERTED);
        return new OrderItemResponse(
            item.getId(),
            item.getExternalItemId(),
            item.getExternalSku(),
            item.getErpSkuId(),
            item.getProductName(),
            item.getVariantName(),
            item.getImageUrl(),
            item.getQuantity(),
            item.getUnitPriceMinor(),
            item.getCurrency(),
            item.getFulfillmentStatus(),
            0,
            requested,
            inProgress,
            0,
            item.getQuantity(),
            false
        );
    }

    private int quantity(List<OrderProcurementRequest> requests, ProcurementRequestStatus status) {
        return requests.stream()
            .filter(request -> request.getStatus() == status)
            .mapToInt(OrderProcurementRequest::getRequestedQuantity)
            .sum();
    }

    private FulfillmentStage calculateStage(
        SalesOrder order,
        List<SalesOrderItem> items,
        Map<Long, List<OrderProcurementRequest>> requestsByItem
    ) {
        List<ProcurementRequestStatus> statuses = items.stream()
            .filter(item -> item.getFulfillmentStatus() == OrderItemFulfillmentStatus.NORMAL)
            .flatMap(item -> requestsByItem.getOrDefault(item.getId(), List.of()).stream())
            .map(OrderProcurementRequest::getStatus)
            .toList();
        return stageCalculator.calculate(order, statuses);
    }

    private Map<Long, List<SalesOrderItem>> itemsByOrder(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<SalesOrderItem>> result = new HashMap<>();
        for (SalesOrderItem item : itemRepository.findByOrderIdInOrderByIdAsc(orderIds)) {
            result.computeIfAbsent(item.getOrder().getId(), ignored -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private Map<Long, List<OrderProcurementRequest>> requestsByItem(
        Collection<List<SalesOrderItem>> itemGroups
    ) {
        List<Long> itemIds = itemGroups.stream().flatMap(Collection::stream).map(SalesOrderItem::getId).toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<OrderProcurementRequest>> result = new HashMap<>();
        for (OrderProcurementRequest request : requestRepository.findByOrderItemIdIn(itemIds)) {
            result.computeIfAbsent(request.getOrderItem().getId(), ignored -> new ArrayList<>()).add(request);
        }
        return result;
    }

    private OrderSummaryResponse summary(OrderQuery request) {
        return new OrderSummaryResponse(
            countWithStage(request, FulfillmentStage.PENDING.name()),
            countWithStage(request, FulfillmentStage.WAITING_PROCUREMENT.name()),
            0,
            countWithStage(request, FulfillmentStage.EXCEPTION.name())
        );
    }

    private long countWithStage(OrderQuery request, String stage) {
        OrderQuery scoped = new OrderQuery(
            request.page(), request.size(), request.keyword(), request.storeId(), request.responsibleUserId(),
            request.externalStatus(), stage, request.orderedFrom(), request.orderedTo(), request.exceptionOnly(), request.sort()
        );
        return orderRepository.count(OrderSpecifications.from(scoped));
    }

    private CustomerResponse customer(SalesOrder order, boolean procurementView) {
        return new CustomerResponse(
            order.getCustomerId(),
            order.getCustomerName(),
            order.getRecipientName(),
            procurementView ? null : order.getRecipientPhone(),
            order.getCountryCode(),
            procurementView ? null : order.getProvince(),
            procurementView ? null : order.getCity(),
            procurementView ? null : order.getDistrict(),
            procurementView ? null : order.getAddressLine1(),
            procurementView ? null : order.getAddressLine2(),
            procurementView ? null : order.getPostalCode()
        );
    }

    private SalesOrder requireOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在"));
    }

    private String externalStatusName(ExternalOrderStatus status) {
        return switch (status) {
            case NEW -> "新订单";
            case PROCESSING -> "处理中";
            case PRE_TRANSIT -> "待承运";
            case IN_TRANSIT -> "运输中";
            case DELIVERED -> "已送达";
            case CANCELED -> "已取消";
            case UNKNOWN -> "未知";
        };
    }

    private StoreSummaryResponse store(SalesOrder order) {
        return new StoreSummaryResponse(order.getStore().getId(), order.getStore().getCode(), order.getStore().getName());
    }

    private UserSummaryResponse user(User user) {
        return user == null ? null : new UserSummaryResponse(user.getId(), user.getLoginName(), user.getDisplayName());
    }

    private List<String> availableActions(SalesOrder order, Collection<String> roles) {
        boolean owner = roles.contains("OWNER");
        boolean canRequest = owner || roles.contains("OPERATIONS") || roles.contains("PROCUREMENT");
        boolean canceled = order.getExternalStatus() == ExternalOrderStatus.CANCELED;
        List<String> actions = new ArrayList<>();
        if (owner && !canceled) {
            actions.add("CHANGE_RESPONSIBLE");
            actions.add("CANCEL");
        }
        if (canRequest && !canceled) {
            actions.add("REQUEST_PROCUREMENT");
        }
        if (!canceled) {
            actions.add(order.isFulfillmentException() ? "RESOLVE_EXCEPTION" : "OPEN_EXCEPTION");
        }
        return List.copyOf(actions);
    }

    private void validate(OrderQuery request) {
        if (request.page() < 1) {
            throw invalid("page 必须从 1 开始");
        }
        if (request.size() < 1 || request.size() > 100) {
            throw invalid("size 必须在 1 到 100 之间");
        }
        if (request.orderedFrom() != null && request.orderedTo() != null
            && request.orderedFrom().isAfter(request.orderedTo())) {
            throw invalid("orderedFrom 不能晚于 orderedTo");
        }
        if (request.keyword() != null && request.keyword().trim().length() > 200) {
            throw invalid("keyword 长度不能超过 200 个字符");
        }
    }

    private Sort parseSort(String value) {
        String normalized = value == null || value.isBlank() ? "requestedShipAt,asc" : value.trim();
        String[] parts = normalized.split(",", -1);
        if (parts.length != 2 || !SORT_FIELDS.contains(parts[0])) {
            throw invalid("sort 字段不在允许范围内");
        }
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(parts[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("sort 方向必须是 asc 或 desc");
        }
        return Sort.by(direction, parts[0]).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "ORDER_QUERY_INVALID", message);
    }
}
