package com.szr.crosserp.order.controller;

import com.szr.crosserp.order.dto.ChangeResponsibleUserRequest;
import com.szr.crosserp.order.dto.CreateProcurementRequestsRequest;
import com.szr.crosserp.order.dto.OrderCommandResponse;
import com.szr.crosserp.order.dto.ProcurementRequestBatchResponse;
import com.szr.crosserp.order.dto.ProcurementRequestResponse;
import com.szr.crosserp.order.dto.ReasonVersionRequest;
import com.szr.crosserp.order.dto.ResolveOrderExceptionRequest;
import com.szr.crosserp.order.dto.ResponsibleUserChangeResponse;
import com.szr.crosserp.order.service.OrderCommandService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderCommandController {
    private final OrderCommandService commandService;

    public OrderCommandController(OrderCommandService commandService) {
        this.commandService = commandService;
    }

    @PutMapping("/{orderId}/responsible-user")
    ResponsibleUserChangeResponse changeResponsible(
        @PathVariable Long orderId, @Valid @RequestBody ChangeResponsibleUserRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return commandService.changeResponsible(orderId, request, jwt.getSubject(), roles(jwt));
    }

    @PostMapping("/{orderId}/procurement-requests")
    @ResponseStatus(HttpStatus.CREATED)
    ProcurementRequestBatchResponse createProcurementRequests(
        @PathVariable Long orderId, @Valid @RequestBody CreateProcurementRequestsRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return commandService.createProcurementRequests(orderId, request, jwt.getSubject(), roles(jwt));
    }

    @PostMapping("/{orderId}/procurement-requests/{requestId}/withdraw")
    ProcurementRequestResponse withdrawProcurementRequest(
        @PathVariable Long orderId, @PathVariable Long requestId,
        @Valid @RequestBody ReasonVersionRequest request, @AuthenticationPrincipal Jwt jwt
    ) {
        return commandService.withdrawProcurementRequest(orderId, requestId, request, jwt.getSubject(), roles(jwt));
    }

    @PostMapping("/{orderId}/exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    OrderCommandResponse openException(
        @PathVariable Long orderId, @Valid @RequestBody ReasonVersionRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return commandService.openException(orderId, request, jwt.getSubject(), roles(jwt));
    }

    @PostMapping("/{orderId}/exceptions/resolve")
    OrderCommandResponse resolveException(
        @PathVariable Long orderId, @Valid @RequestBody ResolveOrderExceptionRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return commandService.resolveException(orderId, request, jwt.getSubject(), roles(jwt));
    }

    @PostMapping("/{orderId}/cancel")
    OrderCommandResponse cancel(
        @PathVariable Long orderId, @Valid @RequestBody ReasonVersionRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return commandService.cancel(orderId, request, jwt.getSubject(), roles(jwt));
    }

    private List<String> roles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }
}
