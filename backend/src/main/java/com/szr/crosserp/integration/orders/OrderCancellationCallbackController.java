package com.szr.crosserp.integration.orders;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/internal/orders/{orderId}/cancel-inventory")
public class OrderCancellationCallbackController {
    private final OrderCancellationCallbackService service;
    public OrderCancellationCallbackController(OrderCancellationCallbackService service){this.service=service;}
    @PostMapping public Map<String,String> cancel(@PathVariable long orderId,@RequestBody OrderCancellationCallbackService.Command input){return service.cancel(orderId,input);}
}
