package com.szr.crosserp.order.service;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.order.dto.ChangeResponsibleUserRequest;
import com.szr.crosserp.order.dto.CreateProcurementRequestsRequest;
import com.szr.crosserp.order.dto.OrderCommandResponse;
import com.szr.crosserp.order.dto.ProcurementRequestBatchResponse;
import com.szr.crosserp.order.dto.ProcurementRequestItemCommand;
import com.szr.crosserp.order.dto.ProcurementRequestResponse;
import com.szr.crosserp.order.dto.ReasonVersionRequest;
import com.szr.crosserp.order.dto.ResolveOrderExceptionRequest;
import com.szr.crosserp.order.dto.ResponsibleUserChangeResponse;
import com.szr.crosserp.order.dto.UserSummaryResponse;
import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.model.OrderEventSource;
import com.szr.crosserp.order.model.OrderItemFulfillmentStatus;
import com.szr.crosserp.order.model.OrderProcurementRequest;
import com.szr.crosserp.order.model.OrderStatusEvent;
import com.szr.crosserp.order.model.ProcurementRequestStatus;
import com.szr.crosserp.order.model.SalesOrder;
import com.szr.crosserp.order.model.SalesOrderItem;
import com.szr.crosserp.order.repository.OrderProcurementRequestRepository;
import com.szr.crosserp.order.repository.OrderStatusEventRepository;
import com.szr.crosserp.order.repository.SalesOrderItemRepository;
import com.szr.crosserp.order.repository.SalesOrderRepository;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.UserRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCommandService {
    private static final Set<String> PROCUREMENT_ROLES = Set.of("OWNER", "OPERATIONS", "PROCUREMENT");

    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final OrderProcurementRequestRepository requestRepository;
    private final OrderStatusEventRepository eventRepository;
    private final UserRepository userRepository;

    public OrderCommandService(
        SalesOrderRepository orderRepository,
        SalesOrderItemRepository itemRepository,
        OrderProcurementRequestRepository requestRepository,
        OrderStatusEventRepository eventRepository,
        UserRepository userRepository
    ) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.requestRepository = requestRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ResponsibleUserChangeResponse changeResponsible(
        Long orderId, ChangeResponsibleUserRequest command, String subject, List<String> roles
    ) {
        requireRole(roles, Set.of("OWNER"));
        User actor = requireActor(subject);
        SalesOrder order = requireOrder(orderId);
        assertVersion(order, command.version());
        User replacement = userRepository.findById(command.userId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "负责人不存在"));
        String from = order.getResponsibleUser() == null ? null : order.getResponsibleUser().getLoginName();
        Instant now = Instant.now();
        order.changeResponsibleUser(replacement, now);
        event(order, "RESPONSIBLE_CHANGED", from, replacement.getLoginName(), actor, command.reason(), now);
        orderRepository.saveAndFlush(order);
        return new ResponsibleUserChangeResponse(order.getId(), user(replacement), order.getVersion());
    }

    @Transactional
    public ProcurementRequestBatchResponse createProcurementRequests(
        Long orderId, CreateProcurementRequestsRequest command, String subject, List<String> roles
    ) {
        requireRole(roles, PROCUREMENT_ROLES);
        User actor = requireActor(subject);
        SalesOrder order = requireOrder(orderId);
        assertVersion(order, command.version());
        requireActive(order);

        Set<Long> submittedIds = new HashSet<>();
        if (command.items().stream().anyMatch(item -> !submittedIds.add(item.orderItemId()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROCUREMENT_REQUEST_INVALID", "订单项不能重复");
        }
        List<SalesOrderItem> orderItems = itemRepository.findByOrderIdInOrderByIdAsc(List.of(orderId));
        Map<Long, SalesOrderItem> itemsById = orderItems.stream()
            .collect(Collectors.toMap(SalesOrderItem::getId, Function.identity()));
        if (!itemsById.keySet().containsAll(submittedIds)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROCUREMENT_REQUEST_INVALID", "订单项不属于当前订单");
        }
        List<OrderProcurementRequest> existing = requestRepository.findByOrderItemIdIn(List.copyOf(submittedIds));
        Map<Long, Integer> activeQuantities = existing.stream()
            .filter(request -> request.getStatus() != ProcurementRequestStatus.WITHDRAWN)
            .collect(Collectors.groupingBy(
                request -> request.getOrderItem().getId(),
                Collectors.summingInt(OrderProcurementRequest::getRequestedQuantity)
            ));
        for (ProcurementRequestItemCommand itemCommand : command.items()) {
            SalesOrderItem item = itemsById.get(itemCommand.orderItemId());
            int gap = item.getQuantity() - activeQuantities.getOrDefault(item.getId(), 0);
            if (item.getFulfillmentStatus() != OrderItemFulfillmentStatus.NORMAL || itemCommand.quantity() > gap) {
                throw new ApiException(HttpStatus.CONFLICT, "PROCUREMENT_GAP_CHANGED", "采购缺口已变化，请刷新后重试");
            }
        }

        Instant now = Instant.now();
        List<OrderProcurementRequest> created = command.items().stream()
            .map(itemCommand -> new OrderProcurementRequest(
                itemsById.get(itemCommand.orderItemId()), itemCommand.quantity(), actor, now, command.note()
            ))
            .toList();
        requestRepository.saveAll(created);
        event(order, "PROCUREMENT_REQUESTED", null, String.valueOf(created.size()), actor, command.note(), now);
        order.touch(now);
        orderRepository.saveAndFlush(order);
        requestRepository.flush();
        return new ProcurementRequestBatchResponse(created.stream().map(this::response).toList(), order.getVersion());
    }

    @Transactional
    public ProcurementRequestResponse withdrawProcurementRequest(
        Long orderId, Long requestId, ReasonVersionRequest command, String subject, List<String> roles
    ) {
        requireRole(roles, PROCUREMENT_ROLES);
        User actor = requireActor(subject);
        SalesOrder order = requireOrder(orderId);
        OrderProcurementRequest request = requireRequest(requestId);
        if (!request.getOrderItem().getOrder().getId().equals(orderId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROCUREMENT_REQUEST_NOT_FOUND", "采购申请不存在");
        }
        if (request.getVersion() != command.version()) {
            throw conflict("PROCUREMENT_VERSION_CONFLICT", "采购申请已被修改，请刷新后重试");
        }
        if (request.getStatus() == ProcurementRequestStatus.CONVERTED) {
            throw conflict("PROCUREMENT_ALREADY_CONVERTED", "采购申请已转单，不能撤回");
        }
        if (request.getStatus() != ProcurementRequestStatus.REQUESTED) {
            throw conflict("PROCUREMENT_ALREADY_WITHDRAWN", "采购申请已经撤回");
        }
        Instant now = Instant.now();
        request.withdraw(actor, now, command.reason());
        event(order, "PROCUREMENT_WITHDRAWN", "REQUESTED", "WITHDRAWN", actor, command.reason(), now);
        requestRepository.saveAndFlush(request);
        return response(request);
    }

    @Transactional
    public OrderCommandResponse openException(
        Long orderId, ReasonVersionRequest command, String subject, List<String> roles
    ) {
        User actor = requireActor(subject);
        SalesOrder order = requireOrder(orderId);
        assertVersion(order, command.version());
        requireActive(order);
        if (order.isFulfillmentException()) {
            throw conflict("ORDER_EXCEPTION_ALREADY_OPEN", "订单异常已经开启");
        }
        Instant now = Instant.now();
        order.openException(command.reason(), now);
        event(order, "EXCEPTION_OPENED", "false", "true", actor, command.reason(), now);
        orderRepository.saveAndFlush(order);
        return response(order);
    }

    @Transactional
    public OrderCommandResponse resolveException(
        Long orderId, ResolveOrderExceptionRequest command, String subject, List<String> roles
    ) {
        User actor = requireActor(subject);
        SalesOrder order = requireOrder(orderId);
        assertVersion(order, command.version());
        requireActive(order);
        if (!order.isFulfillmentException()) {
            throw conflict("ORDER_EXCEPTION_NOT_OPEN", "订单当前没有待处理异常");
        }
        Instant now = Instant.now();
        order.resolveException(now);
        event(order, "EXCEPTION_RESOLVED", "true", "false", actor, command.resolution(), now);
        orderRepository.saveAndFlush(order);
        return response(order);
    }

    @Transactional
    public OrderCommandResponse cancel(
        Long orderId, ReasonVersionRequest command, String subject, List<String> roles
    ) {
        requireRole(roles, Set.of("OWNER"));
        User actor = requireActor(subject);
        SalesOrder order = requireOrder(orderId);
        assertVersion(order, command.version());
        requireActive(order);
        List<SalesOrderItem> items = itemRepository.findByOrderIdInOrderByIdAsc(List.of(orderId));
        List<Long> itemIds = items.stream().map(SalesOrderItem::getId).toList();
        List<OrderProcurementRequest> requests = itemIds.isEmpty() ? List.of() : requestRepository.findByOrderItemIdIn(itemIds);
        if (requests.stream().anyMatch(request -> request.getStatus() == ProcurementRequestStatus.CONVERTED)) {
            throw conflict("ORDER_CANCEL_BLOCKED", "订单已有采购转单，不能取消");
        }
        Instant now = Instant.now();
        requests.stream()
            .filter(request -> request.getStatus() == ProcurementRequestStatus.REQUESTED)
            .forEach(request -> request.withdraw(actor, now, command.reason()));
        items.stream()
            .filter(item -> item.getFulfillmentStatus() == OrderItemFulfillmentStatus.NORMAL)
            .forEach(item -> item.cancel(now));
        order.cancel(now);
        event(order, "ORDER_CANCELED", null, "CANCELED", actor, command.reason(), now);
        requestRepository.saveAll(requests);
        itemRepository.saveAll(items);
        orderRepository.saveAndFlush(order);
        return response(order);
    }

    private User requireActor(String subject) {
        try {
            return userRepository.findById(Long.valueOf(subject))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "登录状态无效"));
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "登录状态无效");
        }
    }

    private SalesOrder requireOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在"));
    }

    private OrderProcurementRequest requireRequest(Long requestId) {
        return requestRepository.findById(requestId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROCUREMENT_REQUEST_NOT_FOUND", "采购申请不存在"));
    }

    private void requireRole(List<String> roles, Set<String> allowed) {
        if (roles == null || roles.stream().noneMatch(allowed::contains)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ORDER_COMMAND_FORBIDDEN", "无权执行此订单操作");
        }
    }

    private void assertVersion(SalesOrder order, long version) {
        if (order.getVersion() != version) {
            throw conflict("ORDER_VERSION_CONFLICT", "订单已被修改，请刷新后重试");
        }
    }

    private void requireActive(SalesOrder order) {
        if (order.getExternalStatus() == ExternalOrderStatus.CANCELED) {
            throw conflict("ORDER_ALREADY_CANCELED", "订单已经取消");
        }
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private void event(
        SalesOrder order, String type, String from, String to, User actor, String remark, Instant now
    ) {
        eventRepository.save(new OrderStatusEvent(order, type, from, to, actor, OrderEventSource.USER, remark, now));
    }

    private UserSummaryResponse user(User user) {
        return new UserSummaryResponse(user.getId(), user.getLoginName(), user.getDisplayName());
    }

    private ProcurementRequestResponse response(OrderProcurementRequest request) {
        return new ProcurementRequestResponse(
            request.getId(), request.getOrderItem().getId(), request.getRequestedQuantity(),
            request.getStatus(), request.getVersion()
        );
    }

    private OrderCommandResponse response(SalesOrder order) {
        return new OrderCommandResponse(
            order.getId(), order.getExternalStatus(), order.isFulfillmentException(), order.getVersion()
        );
    }
}
