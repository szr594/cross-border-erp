package com.szr.crosserp.orderservice.procurement;
import com.szr.crosserp.orderservice.order.dto.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.Valid;
import java.util.List;
@RestController @RequestMapping("/api/orders/{orderId}/procurement-requests")
public class ProcurementRequestController {
    private final ProcurementRequestService service;
    public ProcurementRequestController(ProcurementRequestService service){this.service=service;}
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ProcurementRequestBatchResponse create(@PathVariable long orderId,@Valid @RequestBody CreateProcurementRequestsRequest input,@AuthenticationPrincipal Jwt jwt){return service.create(orderId,input,jwt.getSubject(),roles(jwt));}
    @PostMapping("/{requestId}/withdraw")
    public ProcurementRequestResponse withdraw(@PathVariable long orderId,@PathVariable long requestId,@Valid @RequestBody ReasonVersionRequest input,@AuthenticationPrincipal Jwt jwt){return service.withdraw(orderId,requestId,input,jwt.getSubject(),roles(jwt));}
    private List<String> roles(Jwt jwt){var roles=jwt.getClaimAsStringList("roles");return roles==null?List.of():roles;}
}
