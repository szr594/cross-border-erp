package com.szr.crosserp.procurementservice.procurement.service;

import com.szr.crosserp.procurementservice.common.exception.ApiException;
import com.szr.crosserp.procurementservice.procurement.dto.CreatePurchaseOrderItemCommand;
import com.szr.crosserp.procurementservice.procurement.dto.CreatePurchaseOrderRequest;
import com.szr.crosserp.procurementservice.procurement.dto.PurchaseAllocationCommand;
import com.szr.crosserp.procurementservice.procurement.dto.PurchaseAllocationResponse;
import com.szr.crosserp.procurementservice.procurement.dto.PurchaseOrderCommandResponse;
import com.szr.crosserp.procurementservice.procurement.dto.PurchaseOrderItemResponse;
import com.szr.crosserp.procurementservice.procurement.model.PurchaseOrder;
import com.szr.crosserp.procurementservice.procurement.model.PurchaseOrderAllocation;
import com.szr.crosserp.procurementservice.procurement.model.PurchaseOrderEvent;
import com.szr.crosserp.procurementservice.procurement.model.PurchaseOrderItem;
import com.szr.crosserp.procurementservice.procurement.model.Supplier;
import com.szr.crosserp.procurementservice.procurement.model.SupplierStatus;
import com.szr.crosserp.procurementservice.procurement.repository.PurchaseOrderAllocationRepository;
import com.szr.crosserp.procurementservice.procurement.repository.PurchaseOrderEventRepository;
import com.szr.crosserp.procurementservice.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.procurementservice.procurement.repository.PurchaseOrderRepository;
import com.szr.crosserp.procurementservice.procurement.repository.SupplierRepository;
import com.szr.crosserp.procurementservice.user.model.UserProjection;
import com.szr.crosserp.procurementservice.user.UserProjectionRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseOrderCommandService {
    private static final Set<String> WRITE_ROLES = Set.of("OWNER", "PROCUREMENT");
    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository itemRepository;
    private final PurchaseOrderAllocationRepository allocationRepository;
    private final PurchaseOrderEventRepository eventRepository;

    private final UserProjectionRepository userRepository;
    private final com.szr.crosserp.procurementservice.projection.ProcurementRequestProjectionRepository projections;

    public PurchaseOrderCommandService(
        SupplierRepository supplierRepository,
        PurchaseOrderRepository purchaseOrderRepository,
        PurchaseOrderItemRepository itemRepository,
        PurchaseOrderAllocationRepository allocationRepository,
        PurchaseOrderEventRepository eventRepository,

        UserProjectionRepository userRepository,
        com.szr.crosserp.procurementservice.projection.ProcurementRequestProjectionRepository projections
    ) {
        this.supplierRepository = supplierRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.itemRepository = itemRepository;
        this.allocationRepository = allocationRepository;
        this.eventRepository = eventRepository;

        this.userRepository = userRepository;
        this.projections = projections;
    }

    @Transactional
    public PurchaseOrderCommandResponse createClaimed(CreatePurchaseOrderRequest command,String subject,List<String> roles) {
        return createInternal(command,subject,roles);
    }

    private PurchaseOrderCommandResponse createInternal(CreatePurchaseOrderRequest command,String subject,List<String> roles) {
        requireWriteRole(roles);
        UserProjection actor = requireActor(subject);
        Supplier supplier = supplierRepository.findById(command.supplierId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", "供应商不存在"));
        if (supplier.getStatus() != SupplierStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "SUPPLIER_INACTIVE", "停用供应商不能创建采购单");
        }

        List<PurchaseAllocationCommand> allocationCommands = command.items().stream()
            .flatMap(item -> item.allocations().stream())
            .toList();
        Set<Long> requestIds = new HashSet<>();
        if (allocationCommands.stream().anyMatch(allocation -> !requestIds.add(allocation.procurementRequestId()))) {
            throw invalidAllocation("采购需求不能重复分配");
        }
        Map<Long,com.szr.crosserp.procurementservice.projection.ProcurementRequestProjection> sources=new HashMap<>();
        projections.findAllForUpdateByIdIn(requestIds.stream().sorted().toList()).forEach(p->sources.put(p.getId(),p));
        for (var allocation:allocationCommands) {
            var projection=sources.get(allocation.procurementRequestId());
            if(projection==null || !"REQUESTED".equals(projection.getStatus())
                || projection.getSourceVersion()!=allocation.version() || projection.getRequestedQuantity()!=allocation.quantity())
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PROCUREMENT_PROJECTION_NOT_READY","采购需求投影尚未就绪，请使用原幂等键重试");
        }

        String currency = command.currency().toUpperCase(Locale.ROOT);
        long totalAmountMinor = calculateTotal(command.items());
        Instant now = Instant.now();
        PurchaseOrder purchaseOrder = new PurchaseOrder(
            purchaseOrderNo(now), supplier, currency, totalAmountMinor, actor, normalize(command.note())
        );
        purchaseOrder.awaitBridgeConfirmation();
        purchaseOrder = purchaseOrderRepository.save(purchaseOrder);
        List<PurchaseOrderItemResponse> itemResponses = new ArrayList<>();
        for (CreatePurchaseOrderItemCommand itemCommand : command.items()) {
            int purchasedQuantity = itemCommand.allocations().stream()
                .mapToInt(PurchaseAllocationCommand::quantity)
                .sum();
            PurchaseOrderItem item = itemRepository.save(new PurchaseOrderItem(
                purchaseOrder,
                normalize(itemCommand.supplierProductId()),
                normalize(itemCommand.supplierSku()),
                itemCommand.productName().trim(),
                normalize(itemCommand.variantName()),
                normalize(itemCommand.productUrl()),
                normalize(itemCommand.imageUrl()),
                purchasedQuantity,
                itemCommand.unitPriceMinor(),
                currency
            ));
            List<PurchaseOrderAllocation> allocations = itemCommand.allocations().stream()
                .map(allocation -> new PurchaseOrderAllocation(
                    item, sources.get(allocation.procurementRequestId()), allocation.quantity()
                ))
                .toList();
            allocationRepository.saveAll(allocations);
            itemResponses.add(itemResponse(item, allocations));
        }
        eventRepository.save(new PurchaseOrderEvent(purchaseOrder, "CREATED", actor, normalize(command.note()), now));
        purchaseOrderRepository.flush();
        return new PurchaseOrderCommandResponse(
            purchaseOrder.getId(), purchaseOrder.getPurchaseOrderNo(), supplier.getId(), purchaseOrder.getStatus(),
            currency, totalAmountMinor, purchaseOrder.getVersion(), purchaseOrder.getCreatedAt(), itemResponses
        );
    }

    private long calculateTotal(List<CreatePurchaseOrderItemCommand> items) {
        if (items.stream().anyMatch(item -> item.unitPriceMinor() == null)) {
            return 0;
        }
        try {
            long total = 0;
            for (CreatePurchaseOrderItemCommand item : items) {
                long quantity = item.allocations().stream().mapToLong(PurchaseAllocationCommand::quantity).sum();
                total = Math.addExact(total, Math.multiplyExact(quantity, item.unitPriceMinor()));
            }
            return total;
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PURCHASE_AMOUNT_INVALID", "采购金额超出允许范围");
        }
    }

    private PurchaseOrderItemResponse itemResponse(
        PurchaseOrderItem item, List<PurchaseOrderAllocation> allocations
    ) {
        return new PurchaseOrderItemResponse(
            item.getId(), item.getSupplierProductId(), item.getSupplierSku(), item.getProductName(),
            item.getVariantName(), item.getPurchasedQuantity(), item.getUnitPriceMinor(), item.getCurrency(),
            allocations.stream().map(allocation -> new PurchaseAllocationResponse(
                allocation.getId(), allocation.getProcurementRequest().getId(), allocation.getAllocatedQuantity()
            )).toList()
        );
    }

    private UserProjection requireActor(String subject) {
        try {
            return userRepository.findById(Long.valueOf(subject)).filter(u -> "ACTIVE".equals(u.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "登录状态无效"));
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "登录状态无效");
        }
    }

    private void requireWriteRole(List<String> roles) {
        if (roles == null || roles.stream().noneMatch(WRITE_ROLES::contains)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PURCHASE_ORDER_COMMAND_FORBIDDEN", "无权创建采购单");
        }
    }

    private ApiException invalidAllocation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "PURCHASE_ALLOCATION_INVALID", message);
    }

    private String purchaseOrderNo(Instant now) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "PO-" + NUMBER_DATE.format(now) + "-" + random;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
