package com.szr.crosserp.orderservice.fulfillment;
import com.szr.crosserp.orderservice.order.dto.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.Valid;
import java.util.List;
@RestController @RequestMapping("/api/orders/{orderId}/cancel")
public class OrderCancellationController {
    private final OrderCancellationService service;
    public OrderCancellationController(OrderCancellationService service){this.service=service;}
    @PostMapping public OrderCommandResponse cancel(@PathVariable long orderId,@Valid @RequestBody ReasonVersionRequest input,@RequestHeader(value="Idempotency-Key",required=false)String key,@AuthenticationPrincipal Jwt jwt){var roles=jwt.getClaimAsStringList("roles");return service.cancel(orderId,input,key,jwt.getSubject(),roles==null?List.of():roles);}
}
