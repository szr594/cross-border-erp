package com.szr.crosserp.procurement.controller;

import com.szr.crosserp.procurement.dto.CreatePurchaseOrderRequest;
import com.szr.crosserp.procurement.dto.PurchaseOrderCommandResponse;
import com.szr.crosserp.procurement.service.PurchaseOrderCommandService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderCommandController {
    private final PurchaseOrderCommandService commandService;

    public PurchaseOrderCommandController(PurchaseOrderCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PurchaseOrderCommandResponse create(
        @Valid @RequestBody CreatePurchaseOrderRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return commandService.create(request, jwt.getSubject(), roles == null ? List.of() : roles);
    }
}
