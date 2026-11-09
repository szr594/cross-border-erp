package com.szr.crosserp.inventory.service;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.inventory.dto.AdjustInventoryRequest;
import com.szr.crosserp.inventory.dto.InventoryAdjustmentType;
import com.szr.crosserp.inventory.dto.InventoryCommandResponse;
import com.szr.crosserp.inventory.dto.ManualInventoryAllocationRequest;
import com.szr.crosserp.inventory.dto.ReleaseInventoryAllocationRequest;
import com.szr.crosserp.inventory.dto.ReleaseQuarantineRequest;
import com.szr.crosserp.inventory.model.InventoryAllocation;
import com.szr.crosserp.inventory.model.InventoryAllocationStatus;
import com.szr.crosserp.inventory.model.InventoryLot;
import com.szr.crosserp.inventory.model.InventoryMovement;
import com.szr.crosserp.inventory.model.InventoryMovementType;
import com.szr.crosserp.inventory.repository.InventoryAllocationRepository;
import com.szr.crosserp.inventory.repository.InventoryLotRepository;
import com.szr.crosserp.inventory.repository.InventoryMovementRepository;
import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.model.OrderItemFulfillmentStatus;
import com.szr.crosserp.order.model.SalesOrderItem;
import com.szr.crosserp.order.repository.SalesOrderItemRepository;
import com.szr.crosserp.procurement.model.PurchaseOrderAllocation;
import com.szr.crosserp.procurement.repository.PurchaseOrderAllocationRepository;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryCommandService {
    private static final List<String> ALLOWED_ROLES = List.of("OWNER", "WAREHOUSE");

    private final InventoryLotRepository lotRepository;
    private final InventoryAllocationRepository allocationRepository;
    private final InventoryMovementRepository movementRepository;
    private final PurchaseOrderAllocationRepository purchaseAllocationRepository;
    private final SalesOrderItemRepository orderItemRepository;
    private final UserRepository userRepository;

    public InventoryCommandService(
        InventoryLotRepository lotRepository,
        InventoryAllocationRepository allocationRepository,
        InventoryMovementRepository movementRepository,
        PurchaseOrderAllocationRepository purchaseAllocationRepository,
        SalesOrderItemRepository orderItemRepository,
        UserRepository userRepository
    ) {
        this.lotRepository = lotRepository;
        this.allocationRepository = allocationRepository;
        this.movementRepository = movementRepository;
        this.purchaseAllocationRepository = purchaseAllocationRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public InventoryCommandResponse allocate(
        Long lotId, ManualInventoryAllocationRequest request, String subject, List<String> roles
    ) {
        User actor = authorize(subject, roles);
        validateCommon(request.quantity(), request.reason(), request.idempotencyKey(), request.version());
        if (request.orderItemId() == null) {
            throw badRequest("orderItemId不能为空");
        }
        InventoryLot lot = lockLot(lotId);
        InventoryMovement duplicate = duplicate(request.idempotencyKey());
        if (duplicate != null) {
            assertDuplicate(duplicate, lotId, InventoryMovementType.ORDER_ALLOCATED,
                request.quantity(), request.reason(), request.orderItemId());
            return response(duplicate.getInventoryLot(), duplicate.getAllocation(), duplicate);
        }
        assertVersion(lot.getVersion(), request.version());
        SalesOrderItem orderItem = orderItemRepository.findByIdForUpdate(request.orderItemId())
            .orElseThrow(() -> notFound("订单明细不存在"));
        if (orderItem.getFulfillmentStatus() != OrderItemFulfillmentStatus.NORMAL
            || orderItem.getOrder().getExternalStatus() == ExternalOrderStatus.CANCELED) {
            throw conflict("INVENTORY_ORDER_ITEM_UNAVAILABLE", "订单明细不可分配库存");
        }
        PurchaseOrderAllocation source = requireSource(lot, orderItem, request.quantity());
        long orderUsed = allocationRepository.sumActiveQuantityBySalesOrderItemId(orderItem.getId());
        if (request.quantity() > lot.getAvailableQuantity()
            || request.quantity() > orderItem.getQuantity() - orderUsed) {
            throw conflict("INVENTORY_ALLOCATION_EXCEEDS_LIMIT", "分配数量超过可用库存或订单缺口");
        }
        lot.reserve(request.quantity());
        Instant now = Instant.now();
        InventoryAllocation allocation = allocationRepository.saveAndFlush(new InventoryAllocation(
            number("IAL"), lot, orderItem, source, request.quantity(), actor, now
        ));
        InventoryMovement movement = saveMovement(
            lot, allocation, InventoryMovementType.ORDER_ALLOCATED, request.quantity(), actor,
            request.reason(), request.idempotencyKey(), now
        );
        lotRepository.flush();
        return response(lot, allocation, movement);
    }

    @Transactional
    public InventoryCommandResponse release(
        Long allocationId, ReleaseInventoryAllocationRequest request, String subject, List<String> roles
    ) {
        User actor = authorize(subject, roles);
        validateCommon(1, request.reason(), request.idempotencyKey(), request.version());
        InventoryAllocation reference = allocationRepository.findById(allocationId)
            .orElseThrow(() -> notFound("库存分配不存在"));
        InventoryLot lot = lockLot(reference.getInventoryLot().getId());
        InventoryMovement duplicate = duplicate(request.idempotencyKey());
        if (duplicate != null) {
            assertDuplicate(duplicate, lot.getId(), InventoryMovementType.ALLOCATION_RELEASED,
                reference.getQuantity(), request.reason(), reference.getSalesOrderItem().getId());
            if (duplicate.getAllocation() == null || !duplicate.getAllocation().getId().equals(allocationId)) {
                throw idempotencyConflict();
            }
            return response(duplicate.getInventoryLot(), duplicate.getAllocation(), duplicate);
        }
        InventoryAllocation allocation = allocationRepository.findByIdForUpdate(allocationId)
            .orElseThrow(() -> notFound("库存分配不存在"));
        assertVersion(allocation.getVersion(), request.version());
        if (allocation.getStatus() != InventoryAllocationStatus.RESERVED) {
            throw conflict("INVENTORY_ALLOCATION_NOT_RELEASABLE", "仅预留中的库存分配可以释放");
        }
        Instant now = Instant.now();
        lot.releaseReservation(allocation.getQuantity());
        allocation.release(actor, now, normalizeReason(request.reason()));
        InventoryMovement movement = saveMovement(
            lot, allocation, InventoryMovementType.ALLOCATION_RELEASED, allocation.getQuantity(), actor,
            request.reason(), request.idempotencyKey(), now
        );
        allocationRepository.flush();
        lotRepository.flush();
        return response(lot, allocation, movement);
    }

    @Transactional
    public InventoryCommandResponse adjust(
        Long lotId, AdjustInventoryRequest request, String subject, List<String> roles
    ) {
        User actor = authorize(subject, roles);
        validateCommon(request.quantity(), request.reason(), request.idempotencyKey(), request.version());
        if (request.type() == null) {
            throw badRequest("type不能为空");
        }
        InventoryLot lot = lockLot(lotId);
        InventoryMovementType movementType = request.type() == InventoryAdjustmentType.IN
            ? InventoryMovementType.ADJUSTMENT_IN : InventoryMovementType.ADJUSTMENT_OUT;
        InventoryMovement duplicate = duplicate(request.idempotencyKey());
        if (duplicate != null) {
            assertDuplicate(duplicate, lotId, movementType, request.quantity(), request.reason(), null);
            return response(duplicate.getInventoryLot(), null, duplicate);
        }
        assertVersion(lot.getVersion(), request.version());
        try {
            if (request.type() == InventoryAdjustmentType.IN) {
                lot.adjustIn(request.quantity());
            } else {
                lot.adjustOut(request.quantity());
            }
        } catch (IllegalArgumentException exception) {
            throw conflict("INVENTORY_ADJUSTMENT_EXCEEDS_AVAILABLE", "调减数量不能占用预留库存");
        }
        InventoryMovement movement = saveMovement(
            lot, null, movementType, request.quantity(), actor, request.reason(),
            request.idempotencyKey(), Instant.now()
        );
        lotRepository.flush();
        return response(lot, null, movement);
    }

    @Transactional
    public InventoryCommandResponse releaseQuarantine(
        Long lotId, ReleaseQuarantineRequest request, String subject, List<String> roles
    ) {
        User actor = authorize(subject, roles);
        validateCommon(request.quantity(), request.reason(), request.idempotencyKey(), request.version());
        InventoryLot lot = lockLot(lotId);
        InventoryMovement duplicate = duplicate(request.idempotencyKey());
        if (duplicate != null) {
            assertDuplicate(duplicate, lotId, InventoryMovementType.QUARANTINE_RELEASED,
                request.quantity(), request.reason(), null);
            return response(duplicate.getInventoryLot(), null, duplicate);
        }
        assertVersion(lot.getVersion(), request.version());
        try {
            lot.releaseQuarantine(request.quantity());
        } catch (IllegalArgumentException exception) {
            throw conflict("INVENTORY_QUARANTINE_EXCEEDS_BALANCE", "解除数量超过隔离库存");
        }
        InventoryMovement movement = saveMovement(
            lot, null, InventoryMovementType.QUARANTINE_RELEASED, request.quantity(), actor,
            request.reason(), request.idempotencyKey(), Instant.now()
        );
        lotRepository.flush();
        return response(lot, null, movement);
    }

    private PurchaseOrderAllocation requireSource(InventoryLot lot, SalesOrderItem orderItem, int quantity) {
        List<PurchaseOrderAllocation> sources = purchaseAllocationRepository
            .findByPurchaseOrderItemIdIn(List.of(lot.getPurchaseOrderItem().getId())).stream()
            .filter(source -> source.getProcurementRequest().getOrderItem().getId().equals(orderItem.getId()))
            .filter(source -> source.getAllocatedQuantity()
                - allocationRepository.sumActiveQuantityByPurchaseAllocationId(source.getId()) >= quantity)
            .toList();
        if (sources.isEmpty()) {
            throw conflict("INVENTORY_PURCHASE_SOURCE_MISMATCH", "库存批次与订单明细没有可用的采购来源");
        }
        return sources.get(0);
    }

    private InventoryMovement saveMovement(
        InventoryLot lot, InventoryAllocation allocation, InventoryMovementType type, int quantity,
        User actor, String reason, String key, Instant now
    ) {
        return movementRepository.saveAndFlush(new InventoryMovement(
            number("IMV"), lot, allocation, type, quantity,
            lot.getAvailableQuantity(), lot.getReservedQuantity(), lot.getQuarantinedQuantity(),
            actor, normalizeReason(reason), key.trim(), now
        ));
    }

    private InventoryCommandResponse response(
        InventoryLot lot, InventoryAllocation allocation, InventoryMovement movement
    ) {
        return new InventoryCommandResponse(
            lot.getId(), lot.getLotNo(), allocation == null ? null : allocation.getId(),
            allocation == null ? null : allocation.getAllocationNo(),
            allocation == null ? null : allocation.getStatus().name(),
            lot.getAvailableQuantity(), lot.getReservedQuantity(), lot.getQuarantinedQuantity(),
            lot.getVersion(), allocation == null ? null : allocation.getVersion(),
            movement.getId(), movement.getMovementType().name()
        );
    }

    private InventoryMovement duplicate(String key) {
        return movementRepository.findByIdempotencyKeyForUpdate(key.trim()).orElse(null);
    }

    private void assertDuplicate(
        InventoryMovement movement, Long lotId, InventoryMovementType type, int quantity,
        String reason, Long orderItemId
    ) {
        boolean same = movement.getInventoryLot().getId().equals(lotId)
            && movement.getMovementType() == type
            && movement.getQuantity() == quantity
            && Objects.equals(movement.getReason(), normalizeReason(reason));
        if (orderItemId != null) {
            same = same && movement.getAllocation() != null
                && movement.getAllocation().getSalesOrderItem().getId().equals(orderItemId);
        }
        if (!same) {
            throw idempotencyConflict();
        }
    }

    private User authorize(String subject, List<String> roles) {
        if (roles == null || roles.stream().noneMatch(ALLOWED_ROLES::contains)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVENTORY_FORBIDDEN", "无权执行库存操作");
        }
        try {
            return userRepository.findById(Long.valueOf(subject))
                .orElseThrow(() -> new ApiException(
                    HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "登录状态无效"
                ));
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "登录状态无效");
        }
    }

    private InventoryLot lockLot(Long lotId) {
        return lotRepository.findByIdForUpdate(lotId).orElseThrow(() -> notFound("库存批次不存在"));
    }

    private void validateCommon(Integer quantity, String reason, String key, Long version) {
        if (quantity == null || quantity <= 0) {
            throw badRequest("quantity必须大于0");
        }
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw badRequest("reason不能为空且不能超过500个字符");
        }
        if (key == null || key.isBlank() || key.trim().length() > 64) {
            throw badRequest("idempotencyKey不能为空且不能超过64个字符");
        }
        if (version == null || version < 0) {
            throw badRequest("version不能为空");
        }
    }

    private void assertVersion(long actual, Long requested) {
        if (actual != requested) {
            throw conflict("INVENTORY_VERSION_CONFLICT", "库存数据已更新，请刷新后重试");
        }
    }

    private String normalizeReason(String reason) {
        return reason == null ? null : reason.trim();
    }

    private String number(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVENTORY_REQUEST_INVALID", message);
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "INVENTORY_NOT_FOUND", message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private ApiException idempotencyConflict() {
        return conflict("INVENTORY_IDEMPOTENCY_CONFLICT", "幂等键已用于其他库存操作");
    }
}
