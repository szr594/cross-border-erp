package com.szr.crosserp.receiving.controller;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.receiving.dto.DomesticPackageDetailResponse;
import com.szr.crosserp.receiving.dto.DomesticPackagePageResponse;
import com.szr.crosserp.receiving.dto.DomesticPackageQuery;
import com.szr.crosserp.receiving.dto.GoodsReceiptDetailResponse;
import com.szr.crosserp.receiving.dto.ReceivingEventPageResponse;
import com.szr.crosserp.receiving.model.DomesticPackageStatus;
import com.szr.crosserp.receiving.service.ReceivingQueryService;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReceivingQueryController {
    private final ReceivingQueryService queryService;

    public ReceivingQueryController(ReceivingQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/domestic-packages")
    DomesticPackagePageResponse list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long supplierId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return queryService.findPackages(new DomesticPackageQuery(
            page, size, keyword, supplierId, parseStatus(status), sort
        ));
    }

    @GetMapping("/domestic-packages/lookup")
    DomesticPackageDetailResponse lookup(
        @RequestParam String trackingNumber,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return queryService.findByTrackingNumber(trackingNumber, roles(jwt));
    }

    @GetMapping("/domestic-packages/{packageId}")
    DomesticPackageDetailResponse detail(@PathVariable Long packageId, @AuthenticationPrincipal Jwt jwt) {
        return queryService.findDetail(packageId, roles(jwt));
    }

    @GetMapping("/domestic-packages/{packageId}/events")
    ReceivingEventPageResponse events(
        @PathVariable Long packageId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return queryService.findEvents(packageId, page, size);
    }

    @GetMapping("/goods-receipts/{receiptId}")
    GoodsReceiptDetailResponse receipt(@PathVariable Long receiptId) {
        return queryService.findReceipt(receiptId);
    }

    private DomesticPackageStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DomesticPackageStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RECEIVING_QUERY_INVALID", "status 无效");
        }
    }

    private List<String> roles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }
}
