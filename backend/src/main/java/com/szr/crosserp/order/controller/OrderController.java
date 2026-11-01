package com.szr.crosserp.order.controller;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.order.dto.OrderDetailResponse;
import com.szr.crosserp.order.dto.OrderEventPageResponse;
import com.szr.crosserp.order.dto.OrderFilterOptionsResponse;
import com.szr.crosserp.order.dto.OrderFulfillmentResponse;
import com.szr.crosserp.order.dto.OrderPageResponse;
import com.szr.crosserp.order.dto.OrderQuery;
import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.service.OrderQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderQueryService queryService;

    public OrderController(OrderQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    OrderPageResponse list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long storeId,
        @RequestParam(required = false) Long responsibleUserId,
        @RequestParam(required = false) String externalStatus,
        @RequestParam(defaultValue = "ACTIONABLE") String stage,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant orderedFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant orderedTo,
        @RequestParam(defaultValue = "false") boolean exceptionOnly,
        @RequestParam(defaultValue = "requestedShipAt,asc") String sort
    ) {
        return queryService.findOrders(new OrderQuery(
            page, size, keyword, storeId, responsibleUserId, parseStatus(externalStatus), stage,
            orderedFrom, orderedTo, exceptionOnly, sort
        ));
    }

    @GetMapping("/{orderId}")
    OrderDetailResponse detail(@PathVariable Long orderId, @AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return queryService.findDetail(orderId, roles == null ? List.of() : roles);
    }

    @GetMapping("/filter-options")
    OrderFilterOptionsResponse filterOptions() {
        return queryService.findFilterOptions();
    }

    @GetMapping("/{orderId}/fulfillment")
    OrderFulfillmentResponse fulfillment(@PathVariable Long orderId) {
        return queryService.findFulfillment(orderId);
    }

    @GetMapping("/{orderId}/events")
    OrderEventPageResponse events(
        @PathVariable Long orderId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal Jwt jwt
    ) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return queryService.findEvents(orderId, page, size, roles == null ? List.of() : roles);
    }

    private ExternalOrderStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ExternalOrderStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ORDER_QUERY_INVALID", "externalStatus 无效");
        }
    }
}
