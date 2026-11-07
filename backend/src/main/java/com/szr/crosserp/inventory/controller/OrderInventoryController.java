package com.szr.crosserp.inventory.controller;

import com.szr.crosserp.inventory.dto.OrderInventoryCoverageResponse;
import com.szr.crosserp.inventory.service.InventoryQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderInventoryController {
    private final InventoryQueryService queryService;

    public OrderInventoryController(InventoryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{orderId}/inventory-allocations")
    OrderInventoryCoverageResponse coverage(@PathVariable Long orderId) {
        return queryService.findOrderCoverage(orderId);
    }
}
