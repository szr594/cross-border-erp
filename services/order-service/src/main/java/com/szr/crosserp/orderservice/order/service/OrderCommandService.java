package com.szr.crosserp.orderservice.order.service;

import com.szr.crosserp.orderservice.common.exception.ApiException;
import com.szr.crosserp.orderservice.events.OrderOutbox;
import com.szr.crosserp.orderservice.order.dto.ChangeResponsibleUserRequest;
import com.szr.crosserp.orderservice.order.dto.CreateProcurementRequestsRequest;
import com.szr.crosserp.orderservice.order.dto.OrderCommandResponse;
import com.szr.crosserp.orderservice.order.dto.ProcurementRequestBatchResponse;
import com.szr.crosserp.orderservice.order.dto.ProcurementRequestItemCommand;
import com.szr.crosserp.orderservice.order.dto.ProcurementRequestResponse;
import com.szr.crosserp.orderservice.order.dto.ReasonVersionRequest;
import com.szr.crosserp.orderservice.order.dto.ResolveOrderExceptionRequest;
import com.szr.crosserp.orderservice.order.dto.ResponsibleUserChangeResponse;
import com.szr.crosserp.orderservice.order.dto.UserSummaryResponse;
import com.szr.crosserp.orderservice.order.model.ExternalOrderStatus;
import com.szr.crosserp.orderservice.order.model.OrderEventSource;
import com.szr.crosserp.orderservice.order.model.OrderItemFulfillmentStatus;
import com.szr.crosserp.orderservice.order.model.OrderProcurementRequest;
import com.szr.crosserp.orderservice.order.model.OrderStatusEvent;
import com.szr.crosserp.orderservice.order.model.ProcurementRequestStatus;
import com.szr.crosserp.orderservice.order.model.SalesOrder;
import com.szr.crosserp.orderservice.order.model.SalesOrderItem;
import com.szr.crosserp.orderservice.order.repository.OrderProcurementRequestRepository;
import com.szr.crosserp.orderservice.order.repository.OrderStatusEventRepository;
import com.szr.crosserp.orderservice.order.repository.SalesOrderItemRepository;
import com.szr.crosserp.orderservice.order.repository.SalesOrderRepository;
import com.szr.crosserp.orderservice.user.model.UserProjection;
import com.szr.crosserp.orderservice.user.UserProjectionRepository;
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
    private final UserProjectionRepository userRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final OrderOutbox outbox;
    public OrderCommandService(SalesOrderRepository orderRepository,
        SalesOrderItemRepository itemRepository, OrderProcurementRequestRepository requestRepository,
        OrderStatusEventRepository eventRepository, UserProjectionRepository userRepository, OrderOutbox outbox, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.orderRepository=orderRepository; this.itemRepository=itemRepository;
        this.requestRepository=requestRepository; this.eventRepository=eventRepository;
        this.userRepository=userRepository; this.outbox=outbox; this.jdbc=jdbc;
    }

    @Transactional
    public ResponsibleUserChangeResponse changeResponsible(
        Long orderId, ChangeResponsibleUserRequest command, String subject, List<String> roles
    ) {
        requireRole(roles, Set.of("OWNER"));
        UserProjection actor = requireActor(subject);
        SalesOrder order = requireOrder(orderId);
        assertVersion(order, command.version());
        requireActive(order);
        UserProjection replacement = userRepository.findById(command.userId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "负责人不存在"));
        if (!"ACTIVE".equals(replacement.getStatus())) throw conflict("USER_INACTIVE", "负责人已停用");
        String from = order.getResponsibleUser() == null ? null : order.getResponsibleUser().getLoginName();
        Instant now = Instant.now();
        order.changeResponsibleUser(replacement, now);
        event(order, "RESPONSIBLE_CHANGED", from, replacement.getLoginName(), actor, command.reason(), now);
        orderRepository.saveAndFlush(order);
        outbox.changed(orderId);
        return new ResponsibleUserChangeResponse(order.getId(), user(replacement), order.getVersion());
    }

    @Transactional
    public OrderCommandResponse openException(
        Long orderId, ReasonVersionRequest command, String subject, List<String> roles
    ) {
        UserProjection actor = requireActor(subject);
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
        outbox.changed(orderId);
        return response(order);
    }

    @Transactional
    public OrderCommandResponse resolveException(
        Long orderId, ResolveOrderExceptionRequest command, String subject, List<String> roles
    ) {
        UserProjection actor = requireActor(subject);
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
        outbox.changed(orderId);
        return response(order);
    }

    private boolean pendingCancellation(long id) {
        return jdbc.queryForObject("select count(*) from order_cancel_operations where order_id=? and state='PENDING'", Long.class,id)>0
            || jdbc.queryForObject("select count(*) from fulfillment_execution_holds where order_id=? and status='ACTIVE'",Long.class,id)>0;
    }
    private UserProjection requireActor(String subject) {
        try {
            return userRepository.findById(Long.valueOf(subject)).filter(user -> "ACTIVE".equals(user.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "登录状态无效"));
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "登录状态无效");
        }
    }

    private SalesOrder requireOrder(Long orderId) {
        SalesOrder order=orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在"));
        if (pendingCancellation(orderId)) throw conflict("ORDER_CANCEL_IN_PROGRESS", "订单取消处理中");
        return order;
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
        SalesOrder order, String type, String from, String to, UserProjection actor, String remark, Instant now
    ) {
        eventRepository.save(new OrderStatusEvent(order, type, from, to, actor, OrderEventSource.USER, remark, now));
    }

    private UserSummaryResponse user(UserProjection user) {
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
