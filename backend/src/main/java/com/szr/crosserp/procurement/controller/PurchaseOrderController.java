package com.szr.crosserp.procurement.controller;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.procurement.dto.PurchaseOrderDetailResponse;
import com.szr.crosserp.procurement.dto.PurchaseOrderEventPageResponse;
import com.szr.crosserp.procurement.dto.PurchaseOrderPageResponse;
import com.szr.crosserp.procurement.dto.PurchaseOrderQuery;
import com.szr.crosserp.procurement.model.PurchaseOrderStatus;
import com.szr.crosserp.procurement.service.PurchaseOrderQueryService;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {
    private final PurchaseOrderQueryService queryService;

    public PurchaseOrderController(PurchaseOrderQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    PurchaseOrderPageResponse list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long supplierId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return queryService.findOrders(new PurchaseOrderQuery(
            page, size, keyword, supplierId, parseStatus(status), sort
        ));
    }

    @GetMapping("/{orderId}")
    PurchaseOrderDetailResponse detail(@PathVariable Long orderId) {
        return queryService.findDetail(orderId);
    }

    @GetMapping("/{orderId}/events")
    PurchaseOrderEventPageResponse events(
        @PathVariable Long orderId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return queryService.findEvents(orderId, page, size);
    }

    private PurchaseOrderStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PurchaseOrderStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PURCHASE_ORDER_QUERY_INVALID", "status 无效");
        }
    }
}
