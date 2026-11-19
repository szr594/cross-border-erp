package com.szr.crosserp.procurementservice.procurement.controller;
import com.szr.crosserp.procurementservice.procurement.service.PurchaseBridgeWorkflow;
import com.szr.crosserp.procurementservice.procurement.dto.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.procurement.remote-enabled",havingValue="true")
public class PurchaseCreationController {
 private final PurchaseBridgeWorkflow workflow;
 public PurchaseCreationController(PurchaseBridgeWorkflow workflow){this.workflow=workflow;}
 @PostMapping("/api/purchase-orders") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public PurchaseOrderCommandResponse create(@RequestHeader(value="Idempotency-Key",required=false) String key,
  @Valid @RequestBody CreatePurchaseOrderRequest command,@AuthenticationPrincipal Jwt jwt){
  return workflow.create(key,command,jwt.getSubject(),jwt.getClaimAsStringList("roles"));
 }
}
