package com.szr.crosserp.inventory.controller;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.inventory.dto.InventoryAllocationPageResponse;
import com.szr.crosserp.inventory.dto.InventoryAllocationQuery;
import com.szr.crosserp.inventory.dto.InventoryLotDetailResponse;
import com.szr.crosserp.inventory.dto.InventoryLotPageResponse;
import com.szr.crosserp.inventory.dto.InventoryLotQuery;
import com.szr.crosserp.inventory.dto.InventoryMovementPageResponse;
import com.szr.crosserp.inventory.model.InventoryAllocationStatus;
import com.szr.crosserp.inventory.model.InventoryLotStatus;
import com.szr.crosserp.inventory.model.InventoryQualityStatus;
import com.szr.crosserp.inventory.service.InventoryQueryService;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryQueryController {
    private final InventoryQueryService queryService;

    public InventoryQueryController(InventoryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/lots")
    InventoryLotPageResponse lots(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long purchaseOrderId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String qualityStatus
    ) {
        return queryService.findLots(new InventoryLotQuery(
            page, size, keyword, purchaseOrderId,
            parseEnum(status, InventoryLotStatus.class, "status"),
            parseEnum(qualityStatus, InventoryQualityStatus.class, "qualityStatus")
        ));
    }

    @GetMapping("/lots/{lotId}")
    InventoryLotDetailResponse lotDetail(@PathVariable Long lotId) {
        return queryService.findLotDetail(lotId);
    }

    @GetMapping("/lots/{lotId}/movements")
    InventoryMovementPageResponse movements(
        @PathVariable Long lotId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return queryService.findMovements(lotId, page, size);
    }

    @GetMapping("/allocations")
    InventoryAllocationPageResponse allocations(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String orderNo,
        @RequestParam(required = false) String lotNo,
        @RequestParam(required = false) String status
    ) {
        return queryService.findAllocations(new InventoryAllocationQuery(
            page, size, orderNo, lotNo,
            parseEnum(status, InventoryAllocationStatus.class, "status")
        ));
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> type, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVENTORY_QUERY_INVALID", field + " 无效");
        }
    }
}
