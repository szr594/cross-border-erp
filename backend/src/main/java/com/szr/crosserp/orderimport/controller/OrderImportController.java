package com.szr.crosserp.orderimport.controller;

import com.szr.crosserp.orderimport.dto.OrderImportErrorPageResponse;
import com.szr.crosserp.orderimport.dto.OrderImportResponse;
import com.szr.crosserp.orderimport.service.OrderImportService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/order-imports")
public class OrderImportController {
    private final OrderImportService service;

    public OrderImportController(OrderImportService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    @ResponseStatus(HttpStatus.CREATED)
    OrderImportResponse preview(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        return service.preview(file, jwt.getSubject(), roles(jwt));
    }

    @PostMapping("/{importId}/confirm")
    OrderImportResponse confirm(@PathVariable String importId, @AuthenticationPrincipal Jwt jwt) {
        return service.confirm(importId, jwt.getSubject(), roles(jwt));
    }

    @GetMapping("/{importId}")
    OrderImportResponse get(@PathVariable String importId, @AuthenticationPrincipal Jwt jwt) {
        return service.get(importId, jwt.getSubject(), roles(jwt));
    }

    @GetMapping("/{importId}/errors")
    OrderImportErrorPageResponse errors(
        @PathVariable String importId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return service.errors(importId, page, size, jwt.getSubject(), roles(jwt));
    }

    private List<String> roles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }
}
