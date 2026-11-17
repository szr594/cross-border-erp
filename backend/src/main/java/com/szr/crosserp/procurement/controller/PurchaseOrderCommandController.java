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
    private final com.szr.crosserp.procurement.service.IdempotentPurchaseCreation idempotent;
    private final org.springframework.beans.factory.ObjectProvider<com.szr.crosserp.procurement.service.PurchaseBridgeWorkflow> bridge;

    public PurchaseOrderCommandController(PurchaseOrderCommandService commandService,com.szr.crosserp.procurement.service.IdempotentPurchaseCreation idempotent,
        org.springframework.beans.factory.ObjectProvider<com.szr.crosserp.procurement.service.PurchaseBridgeWorkflow> bridge) {
        this.commandService = commandService;
        this.idempotent = idempotent;
        this.bridge = bridge;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PurchaseOrderCommandResponse create(
        @Valid @RequestBody CreatePurchaseOrderRequest request,
        @org.springframework.web.bind.annotation.RequestHeader(value="Idempotency-Key",required=false) String key,
        @AuthenticationPrincipal Jwt jwt
    ) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        var workflow=bridge.getIfAvailable();
        if(workflow!=null)return workflow.create(key,request,jwt.getSubject(),roles==null?List.of():roles);
        if(key!=null)return idempotent.create(key,request,jwt.getSubject(),roles==null?List.of():roles);
        return commandService.create(request, jwt.getSubject(), roles == null ? List.of() : roles);
    }
}
