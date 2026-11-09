package com.szr.crosserp.inventory.controller;

import com.szr.crosserp.inventory.dto.AdjustInventoryRequest;
import com.szr.crosserp.inventory.dto.InventoryCommandResponse;
import com.szr.crosserp.inventory.dto.ManualInventoryAllocationRequest;
import com.szr.crosserp.inventory.dto.ReleaseInventoryAllocationRequest;
import com.szr.crosserp.inventory.dto.ReleaseQuarantineRequest;
import com.szr.crosserp.inventory.service.InventoryCommandService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryCommandController {
    private final InventoryCommandService commandService;

    public InventoryCommandController(InventoryCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping("/lots/{lotId}/allocate")
    InventoryCommandResponse allocate(
        @PathVariable Long lotId, @RequestBody ManualInventoryAllocationRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return commandService.allocate(lotId, request, jwt.getSubject(), roles(jwt));
    }

    @PostMapping("/allocations/{allocationId}/release")
    InventoryCommandResponse release(
        @PathVariable Long allocationId, @RequestBody ReleaseInventoryAllocationRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return commandService.release(allocationId, request, jwt.getSubject(), roles(jwt));
    }

    @PostMapping("/lots/{lotId}/adjustments")
    InventoryCommandResponse adjust(
        @PathVariable Long lotId, @RequestBody AdjustInventoryRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return commandService.adjust(lotId, request, jwt.getSubject(), roles(jwt));
    }

    @PostMapping("/lots/{lotId}/release-quarantine")
    InventoryCommandResponse releaseQuarantine(
        @PathVariable Long lotId, @RequestBody ReleaseQuarantineRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return commandService.releaseQuarantine(lotId, request, jwt.getSubject(), roles(jwt));
    }

    private List<String> roles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }
}
