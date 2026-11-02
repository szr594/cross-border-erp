package com.szr.crosserp.procurement.service;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.order.model.OrderProcurementRequest;
import com.szr.crosserp.order.model.ProcurementRequestStatus;
import com.szr.crosserp.order.repository.OrderProcurementRequestRepository;
import com.szr.crosserp.procurement.dto.CreatePurchaseOrderItemCommand;
import com.szr.crosserp.procurement.dto.CreatePurchaseOrderRequest;
import com.szr.crosserp.procurement.dto.PurchaseAllocationCommand;
import com.szr.crosserp.procurement.dto.PurchaseAllocationResponse;
import com.szr.crosserp.procurement.dto.PurchaseOrderCommandResponse;
import com.szr.crosserp.procurement.dto.PurchaseOrderItemResponse;
import com.szr.crosserp.procurement.model.PurchaseOrder;
import com.szr.crosserp.procurement.model.PurchaseOrderAllocation;
import com.szr.crosserp.procurement.model.PurchaseOrderEvent;
import com.szr.crosserp.procurement.model.PurchaseOrderItem;
import com.szr.crosserp.procurement.model.Supplier;
import com.szr.crosserp.procurement.model.SupplierStatus;
import com.szr.crosserp.procurement.repository.PurchaseOrderAllocationRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderEventRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderRepository;
import com.szr.crosserp.procurement.repository.SupplierRepository;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.UserRepository;
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
    private final OrderProcurementRequestRepository requestRepository;
    private final UserRepository userRepository;

    public PurchaseOrderCommandService(
        SupplierRepository supplierRepository,
        PurchaseOrderRepository purchaseOrderRepository,
        PurchaseOrderItemRepository itemRepository,
        PurchaseOrderAllocationRepository allocationRepository,
        PurchaseOrderEventRepository eventRepository,
        OrderProcurementRequestRepository requestRepository,
        UserRepository userRepository
    ) {
        this.supplierRepository = supplierRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.itemRepository = itemRepository;
        this.allocationRepository = allocationRepository;
        this.eventRepository = eventRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public PurchaseOrderCommandResponse create(
        CreatePurchaseOrderRequest command, String subject, List<String> roles
    ) {
        requireWriteRole(roles);
        User actor = requireActor(subject);
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
        List<OrderProcurementRequest> requests = requestRepository.findAllForUpdateByIdIn(List.copyOf(requestIds));
        if (requests.size() != requestIds.size()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROCUREMENT_REQUEST_NOT_FOUND", "采购需求不存在");
        }
        Map<Long, OrderProcurementRequest> requestsById = new HashMap<>();
        requests.forEach(request -> requestsById.put(request.getId(), request));
        validateAllocations(allocationCommands, requestsById);

        String currency = command.currency().toUpperCase(Locale.ROOT);
        long totalAmountMinor = calculateTotal(command.items());
        Instant now = Instant.now();
        PurchaseOrder purchaseOrder = purchaseOrderRepository.save(new PurchaseOrder(
            purchaseOrderNo(now), supplier, currency, totalAmountMinor, actor, normalize(command.note())
        ));
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
                    item, requestsById.get(allocation.procurementRequestId()), allocation.quantity()
                ))
                .toList();
            allocationRepository.saveAll(allocations);
            itemResponses.add(itemResponse(item, allocations));
        }
        requests.forEach(OrderProcurementRequest::convert);
        requestRepository.saveAllAndFlush(requests);
        eventRepository.save(new PurchaseOrderEvent(purchaseOrder, "CREATED", actor, normalize(command.note()), now));
        purchaseOrderRepository.flush();
        return new PurchaseOrderCommandResponse(
            purchaseOrder.getId(), purchaseOrder.getPurchaseOrderNo(), supplier.getId(), purchaseOrder.getStatus(),
            currency, totalAmountMinor, purchaseOrder.getVersion(), purchaseOrder.getCreatedAt(), itemResponses
        );
    }

    private void validateAllocations(
        List<PurchaseAllocationCommand> commands, Map<Long, OrderProcurementRequest> requestsById
    ) {
        for (PurchaseAllocationCommand command : commands) {
            OrderProcurementRequest request = requestsById.get(command.procurementRequestId());
            if (request.getStatus() != ProcurementRequestStatus.REQUESTED) {
                throw new ApiException(HttpStatus.CONFLICT, "PROCUREMENT_REQUEST_UNAVAILABLE", "采购需求已撤回或已转换");
            }
            if (request.getVersion() != command.version()) {
                throw new ApiException(HttpStatus.CONFLICT, "PROCUREMENT_REQUEST_VERSION_CONFLICT", "采购需求已被修改，请刷新后重试");
            }
            if (request.getRequestedQuantity() != command.quantity()) {
                throw invalidAllocation("采购需求必须一次性完整分配");
            }
        }
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

    private User requireActor(String subject) {
        try {
            return userRepository.findById(Long.valueOf(subject))
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
