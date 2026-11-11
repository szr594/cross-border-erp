package com.szr.crosserp.packing.dto;

import java.time.Instant;
import java.util.List;

public final class PackingDtos {
    private PackingDtos() {}
    public record StartRequest(String reason) {}
    public record BoxItemCommand(Long allocationId,Integer quantity) {}
    public record SaveBoxRequest(Integer weightGrams,Integer lengthMm,Integer widthMm,Integer heightMm,String note,Long version,List<BoxItemCommand> items) {}
    public record CompleteRequest(String idempotencyKey,Long version) {}
    public record CancelRequest(String reason,Long version) {}
    public record CommandResponse(Long taskId,String taskNo,String status,long version) {}
    public record TaskListItem(Long taskId,Long orderId,String orderNo,String storeName,String status,int itemCount,int totalQuantity,int boxCount,Instant requestedShipAt,Instant startedAt,Instant completedAt) {}
    public record TaskPage(List<TaskListItem> items,long pendingCount,long draftCount,long completedCount) {}
    public record AllocationItem(Long allocationId,String allocationNo,Long lotId,String lotNo,Long orderItemId,String productName,String variantName,int quantity,String status) {}
    public record BoxItem(Long id,Long allocationId,String allocationNo,Long lotId,String lotNo,Long orderItemId,String productName,String variantName,int quantity) {}
    public record Box(Long id,String boxNo,Integer weightGrams,Integer lengthMm,Integer widthMm,Integer heightMm,String note,long version,List<BoxItem> items) {}
    public record Event(Long id,String eventType,String operatorName,String remark,Instant occurredAt) {}
    public record Detail(Long taskId,String taskNo,String status,long version,Long orderId,String orderNo,String storeName,Instant requestedShipAt,Instant startedAt,Instant completedAt,List<AllocationItem> allocations,List<Box> boxes,List<Event> events,List<String> availableActions) {}
}
