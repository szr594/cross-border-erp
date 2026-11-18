package com.szr.crosserp.store.controller;

import com.szr.crosserp.store.service.DemoStoreService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.procurement.remote-enabled",havingValue="false",matchIfMissing=true)
public class DemoStoreController {
    private final DemoStoreService service;
    public DemoStoreController(DemoStoreService service) {this.service=service;}
    @PostMapping("/api/stores/demo/initialize")
    public DemoStoreService.Result initialize(@AuthenticationPrincipal Jwt jwt) {
        return service.initialize(jwt.getClaimAsStringList("roles"));
    }
}
