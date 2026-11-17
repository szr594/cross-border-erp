package com.szr.crosserp.orderservice.fulfillment;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/orders/{orderId}/executions/{operationId}")
public class ExecutionAuthorityController {
    private final ExecutionAuthorityService service;
    public ExecutionAuthorityController(ExecutionAuthorityService service){this.service=service;}
    @PostMapping public ExecutionAuthorityService.Authorization acquire(@PathVariable long orderId,@PathVariable String operationId){return service.acquire(orderId,operationId);}
    @PostMapping("/release") public ExecutionAuthorityService.Authorization release(@PathVariable long orderId,@PathVariable String operationId){return service.release(orderId,operationId);}
}
