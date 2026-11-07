package com.szr.crosserp.inventory.controller;

import com.szr.crosserp.inventory.dto.InventoryPostingResponse;
import com.szr.crosserp.inventory.service.InventoryPostingCommandService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/receipts")
public class InventoryPostingController {
    private final InventoryPostingCommandService commandService;

    public InventoryPostingController(InventoryPostingCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping("/{receiptId}/post")
    InventoryPostingResponse post(@PathVariable Long receiptId, @AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return commandService.postHistoricalReceipt(
            receiptId, jwt.getSubject(), roles == null ? List.of() : roles
        );
    }
}
