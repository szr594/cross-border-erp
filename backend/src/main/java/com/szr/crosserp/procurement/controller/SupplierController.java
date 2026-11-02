package com.szr.crosserp.procurement.controller;

import com.szr.crosserp.procurement.dto.CreateSupplierRequest;
import com.szr.crosserp.procurement.dto.SupplierPageResponse;
import com.szr.crosserp.procurement.dto.SupplierResponse;
import com.szr.crosserp.procurement.dto.SupplierStatusRequest;
import com.szr.crosserp.procurement.dto.UpdateSupplierRequest;
import com.szr.crosserp.procurement.model.SupplierStatus;
import com.szr.crosserp.procurement.service.SupplierService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {
    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    SupplierPageResponse list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) SupplierStatus status
    ) {
        return supplierService.findSuppliers(page, size, keyword, status);
    }

    @GetMapping("/{supplierId}")
    SupplierResponse detail(@PathVariable Long supplierId) {
        return supplierService.findSupplier(supplierId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SupplierResponse create(
        @Valid @RequestBody CreateSupplierRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return supplierService.create(request, roles(jwt));
    }

    @PutMapping("/{supplierId}")
    SupplierResponse update(
        @PathVariable Long supplierId,
        @Valid @RequestBody UpdateSupplierRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return supplierService.update(supplierId, request, roles(jwt));
    }

    @PostMapping("/{supplierId}/enable")
    SupplierResponse enable(
        @PathVariable Long supplierId,
        @Valid @RequestBody SupplierStatusRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return supplierService.changeStatus(supplierId, request.version(), SupplierStatus.ACTIVE, roles(jwt));
    }

    @PostMapping("/{supplierId}/disable")
    SupplierResponse disable(
        @PathVariable Long supplierId,
        @Valid @RequestBody SupplierStatusRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return supplierService.changeStatus(supplierId, request.version(), SupplierStatus.INACTIVE, roles(jwt));
    }

    private List<String> roles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }
}
