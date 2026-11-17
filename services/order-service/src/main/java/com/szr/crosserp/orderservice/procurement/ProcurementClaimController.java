package com.szr.crosserp.orderservice.procurement;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/internal/procurement-requests/claims")
public class ProcurementClaimController {
    public record Confirmation(String purchaseReference) {}
    private final ProcurementClaimService service;
    public ProcurementClaimController(ProcurementClaimService service){this.service=service;}
    @PostMapping public ProcurementClaimService.Result claim(@RequestBody ProcurementClaimService.Command command){return service.claim(command);}
    @GetMapping("/{id}") public ProcurementClaimService.Result get(@PathVariable String id){return service.get(id);}
    @PostMapping("/{id}/confirm") public ProcurementClaimService.Result confirm(@PathVariable String id,@RequestBody Confirmation body){return service.confirm(id,body.purchaseReference());}
    @PostMapping("/{id}/release") public ProcurementClaimService.Result release(@PathVariable String id){return service.release(id);}
}
