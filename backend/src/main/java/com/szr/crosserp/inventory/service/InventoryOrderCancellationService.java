package com.szr.crosserp.inventory.service;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.inventory.model.InventoryAllocation;
import com.szr.crosserp.inventory.model.InventoryAllocationStatus;
import com.szr.crosserp.inventory.model.InventoryLot;
import com.szr.crosserp.inventory.model.InventoryMovement;
import com.szr.crosserp.inventory.model.InventoryMovementType;
import com.szr.crosserp.inventory.repository.InventoryAllocationRepository;
import com.szr.crosserp.inventory.repository.InventoryLotRepository;
import com.szr.crosserp.inventory.repository.InventoryMovementRepository;
import com.szr.crosserp.order.repository.SalesOrderItemRepository;
import com.szr.crosserp.user.model.User;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InventoryOrderCancellationService {
    private static final List<InventoryAllocationStatus> ACTIVE_STATUSES = List.of(
        InventoryAllocationStatus.RESERVED, InventoryAllocationStatus.CONSUMED
    );

    private final InventoryLotRepository lotRepository;
    private final InventoryAllocationRepository allocationRepository;
    private final InventoryMovementRepository movementRepository;
    private final SalesOrderItemRepository orderItemRepository;

    public InventoryOrderCancellationService(
        InventoryLotRepository lotRepository,
        InventoryAllocationRepository allocationRepository,
        InventoryMovementRepository movementRepository,
        SalesOrderItemRepository orderItemRepository
    ) {
        this.lotRepository = lotRepository;
        this.allocationRepository = allocationRepository;
        this.movementRepository = movementRepository;
        this.orderItemRepository = orderItemRepository;
    }

    public void releaseReservedForOrder(
        Long orderId, List<Long> orderItemIds, User actor, String reason, Instant occurredAt
    ) {
        if (orderItemIds.isEmpty()) {
            return;
        }
        List<InventoryAllocation> snapshot = allocationRepository
            .findBySalesOrderItemIdInOrderByIdAsc(orderItemIds).stream()
            .filter(allocation -> ACTIVE_STATUSES.contains(allocation.getStatus()))
            .toList();
        Set<Long> lockedLotIds = new HashSet<>();
        snapshot.stream()
            .map(allocation -> allocation.getInventoryLot().getId())
            .distinct()
            .sorted()
            .forEach(lotId -> {
                lockLot(lotId);
                lockedLotIds.add(lotId);
            });
        orderItemIds.stream().sorted().forEach(this::lockOrderItem);

        allocationRepository.findBySalesOrderItemIdInOrderByIdAsc(orderItemIds).stream()
            .filter(allocation -> ACTIVE_STATUSES.contains(allocation.getStatus()))
            .map(allocation -> allocation.getInventoryLot().getId())
            .filter(lotId -> !lockedLotIds.contains(lotId))
            .distinct()
            .sorted()
            .forEach(lotId -> {
                lockLot(lotId);
                lockedLotIds.add(lotId);
            });

        List<InventoryAllocation> allocations = allocationRepository
            .findBySalesOrderItemIdsAndStatusesForUpdate(orderItemIds, ACTIVE_STATUSES);
        if (allocations.stream().anyMatch(
            allocation -> allocation.getStatus() == InventoryAllocationStatus.CONSUMED
        )) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "ORDER_CANCEL_BLOCKED_BY_CONSUMED_INVENTORY",
                "订单存在已消耗库存，不能取消"
            );
        }
        allocations.stream()
            .sorted(Comparator
                .comparing((InventoryAllocation allocation) -> allocation.getInventoryLot().getId())
                .thenComparing(InventoryAllocation::getId))
            .forEach(allocation -> release(orderId, allocation, actor, reason, occurredAt));
        allocationRepository.flush();
        lotRepository.flush();
    }

    private void release(
        Long orderId, InventoryAllocation allocation, User actor, String reason, Instant occurredAt
    ) {
        InventoryLot lot = allocation.getInventoryLot();
        lot.releaseReservation(allocation.getQuantity());
        allocation.release(actor, occurredAt, reason);
        movementRepository.save(new InventoryMovement(
            "IMV-CANCEL-" + allocation.getId(), lot, allocation,
            InventoryMovementType.ALLOCATION_RELEASED, allocation.getQuantity(),
            lot.getAvailableQuantity(), lot.getReservedQuantity(), lot.getQuarantinedQuantity(),
            actor, reason, "ORDER-CANCEL-" + orderId + "-ALLOC-" + allocation.getId(), occurredAt
        ));
    }

    private void lockLot(Long lotId) {
        lotRepository.findByIdForUpdate(lotId).orElseThrow(() -> new ApiException(
            HttpStatus.CONFLICT, "INVENTORY_LOT_CHANGED", "库存批次已发生变化"
        ));
    }

    private void lockOrderItem(Long orderItemId) {
        orderItemRepository.findByIdForUpdate(orderItemId).orElseThrow(() -> new ApiException(
            HttpStatus.CONFLICT, "ORDER_ITEM_CHANGED", "订单明细已发生变化"
        ));
    }
}
