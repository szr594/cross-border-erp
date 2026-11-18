package com.szr.crosserp.procurement.controller;

import com.szr.crosserp.procurement.service.PurchaseOrderingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
public class PurchaseOrderingController {
    public record Command(@Min(0) long version) {}
    private final PurchaseOrderingService service;
    public PurchaseOrderingController(PurchaseOrderingService service){this.service=service;}
    @PostMapping("/api/purchase-orders/{id}/confirm-order")
    public PurchaseOrderingService.Result confirm(@PathVariable long id,@Valid @RequestBody Command command,@AuthenticationPrincipal Jwt jwt){
        return service.confirm(id,command.version(),jwt.getSubject(),jwt.getClaimAsStringList("roles"));
    }
}
