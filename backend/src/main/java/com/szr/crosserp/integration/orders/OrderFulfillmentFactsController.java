package com.szr.crosserp.integration.orders;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderFulfillmentFactsController {
    private final OrderFulfillmentFactsService service;
    public OrderFulfillmentFactsController(OrderFulfillmentFactsService service) { this.service = service; }
    @GetMapping("/internal/orders/fulfillment-facts")
    public OrderFulfillmentFactsService.Snapshot facts() { return service.snapshot(); }
}
