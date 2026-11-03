package com.szr.crosserp.receiving.controller;

import com.szr.crosserp.receiving.dto.CancelDomesticPackageRequest;
import com.szr.crosserp.receiving.dto.ConfirmGoodsReceiptRequest;
import com.szr.crosserp.receiving.dto.ConfirmGoodsReceiptResponse;
import com.szr.crosserp.receiving.dto.CreateDomesticPackageRequest;
import com.szr.crosserp.receiving.dto.DomesticPackageCommandResponse;
import com.szr.crosserp.receiving.service.ReceivingCommandService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/domestic-packages")
public class ReceivingCommandController {
    private final ReceivingCommandService commandService;

    public ReceivingCommandController(ReceivingCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DomesticPackageCommandResponse register(
        @Valid @RequestBody CreateDomesticPackageRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return commandService.register(request, jwt.getSubject(), roles(jwt));
    }

    @PostMapping("/{packageId}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    ConfirmGoodsReceiptResponse confirm(
        @PathVariable Long packageId,
        @Valid @RequestBody ConfirmGoodsReceiptRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return commandService.confirm(packageId, request, jwt.getSubject(), roles(jwt));
    }

    @PostMapping("/{packageId}/cancel")
    DomesticPackageCommandResponse cancel(
        @PathVariable Long packageId,
        @Valid @RequestBody CancelDomesticPackageRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return commandService.cancel(packageId, request, jwt.getSubject(), roles(jwt));
    }

    private List<String> roles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }
}
