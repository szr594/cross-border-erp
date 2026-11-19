package com.szr.crosserp.inventory.service;

import com.szr.crosserp.inventory.model.InventoryAllocation;
import com.szr.crosserp.inventory.model.InventoryAllocationStatus;
import com.szr.crosserp.inventory.model.InventoryLot;
import com.szr.crosserp.inventory.model.InventoryMovement;
import com.szr.crosserp.inventory.model.InventoryMovementType;
import com.szr.crosserp.inventory.model.InventoryQualityStatus;
import com.szr.crosserp.inventory.repository.InventoryAllocationRepository;
import com.szr.crosserp.inventory.repository.InventoryLotRepository;
import com.szr.crosserp.inventory.repository.InventoryMovementRepository;
import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.model.OrderItemFulfillmentStatus;
import com.szr.crosserp.integration.orders.model.OrderItemProjection;
import com.szr.crosserp.integration.orders.OrderItemProjectionRepository;
import com.szr.crosserp.integration.procurement.model.PurchaseAllocationProjection;
import com.szr.crosserp.integration.procurement.PurchaseAllocationProjectionRepository;
import com.szr.crosserp.user.model.User;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryAllocationService {
    @org.springframework.beans.factory.annotation.Autowired private com.szr.crosserp.integration.orders.OrderExecutionGate execution;
    private final InventoryLotRepository lotRepository;
    private final InventoryAllocationRepository allocationRepository;
    private final InventoryMovementRepository movementRepository;
    private final PurchaseAllocationProjectionRepository purchaseAllocationRepository;
    private final OrderItemProjectionRepository salesOrderItemRepository;

    public InventoryAllocationService(
        InventoryLotRepository lotRepository,
        InventoryAllocationRepository allocationRepository,
        InventoryMovementRepository movementRepository,
        PurchaseAllocationProjectionRepository purchaseAllocationRepository,
        OrderItemProjectionRepository salesOrderItemRepository
    ) {
        this.lotRepository = lotRepository;
        this.allocationRepository = allocationRepository;
        this.movementRepository = movementRepository;
        this.purchaseAllocationRepository = purchaseAllocationRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
    }

    @Transactional
    public List<Long> allocateByPurchaseSource(Long lotId, User operator, Instant occurredAt) {
        InventoryLot lot = lotRepository.findByIdForUpdate(lotId).orElseThrow();
        if (lot.getQualityStatus() != InventoryQualityStatus.ACCEPTED || lot.getAvailableQuantity() == 0) {
            return List.of();
        }
        List<PurchaseAllocationProjection> sources = purchaseAllocationRepository
            .findByPurchaseOrderItemIdIn(List.of(lot.getPurchaseOrderItem().getId()))
            .stream()
            .sorted(sourceOrder())
            .toList();
        java.util.ArrayList<Long> allocationIds = new java.util.ArrayList<>();
        for (PurchaseAllocationProjection source : sources) {
            if (lot.getAvailableQuantity() == 0) {
                break;
            }
            if (allocationRepository.findByInventoryLotIdAndPurchaseOrderAllocationId(
                lot.getId(), source.getId()
            ).isPresent()) {
                continue;
            }
            OrderItemProjection orderItem = salesOrderItemRepository.findByIdForUpdate(
                source.getProcurementRequest().getOrderItem().getId()
            ).orElseThrow();
            execution.require(orderItem.getOrder().getId());
            if (orderItem.getFulfillmentStatus() != OrderItemFulfillmentStatus.NORMAL
                || orderItem.getOrder().getExternalStatus() == ExternalOrderStatus.CANCELED) {
                continue;
            }
            List<InventoryAllocation> activeAllocations = allocationRepository
                .findActiveBySalesOrderItemIdForUpdate(
                    orderItem.getId(),
                    List.of(InventoryAllocationStatus.RESERVED, InventoryAllocationStatus.CONSUMED)
                );
            long sourceUsed = activeAllocations.stream()
                .filter(allocation -> allocation.getPurchaseOrderAllocation() != null
                    && allocation.getPurchaseOrderAllocation().getId().equals(source.getId()))
                .mapToLong(InventoryAllocation::getQuantity)
                .sum();
            long orderUsed = activeAllocations.stream().mapToLong(InventoryAllocation::getQuantity).sum();
            int quantity = (int) Math.min(
                lot.getAvailableQuantity(),
                Math.min(source.getAllocatedQuantity() - sourceUsed, orderItem.getQuantity() - orderUsed)
            );
            if (quantity <= 0) {
                continue;
            }
            lot.reserve(quantity);
            InventoryAllocation allocation = allocationRepository.saveAndFlush(new InventoryAllocation(
                "IAL-" + lot.getId() + "-" + source.getId(), lot, orderItem, source,
                quantity, null, occurredAt
            ));
            movementRepository.save(new InventoryMovement(
                "IMV-ALLOC-" + allocation.getId(), lot, allocation,
                InventoryMovementType.ORDER_ALLOCATED, quantity,
                lot.getAvailableQuantity(), lot.getReservedQuantity(), lot.getQuarantinedQuantity(),
                operator, null, "INV-ALLOC-" + allocation.getId(), occurredAt
            ));
            allocationIds.add(allocation.getId());
        }
        return List.copyOf(allocationIds);
    }

    private Comparator<PurchaseAllocationProjection> sourceOrder() {
        return Comparator
            .comparing(
                (PurchaseAllocationProjection source) -> source.getProcurementRequest().getOrderItem()
                    .getOrder().getRequestedShipAt(),
                Comparator.nullsLast(Comparator.naturalOrder())
            )
            .thenComparing(source -> source.getProcurementRequest().getOrderItem().getOrder().getId())
            .thenComparing(source -> source.getProcurementRequest().getOrderItem().getId())
            .thenComparing(PurchaseAllocationProjection::getId);
    }
}
