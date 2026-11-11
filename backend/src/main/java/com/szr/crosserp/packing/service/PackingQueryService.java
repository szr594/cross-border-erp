package com.szr.crosserp.packing.service;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.inventory.model.*;
import com.szr.crosserp.inventory.repository.InventoryAllocationRepository;
import com.szr.crosserp.order.model.*;
import com.szr.crosserp.order.repository.*;
import com.szr.crosserp.packing.dto.PackingDtos.*;
import com.szr.crosserp.packing.model.*;
import com.szr.crosserp.packing.repository.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class PackingQueryService {
    private final PackingTaskRepository tasks; private final PackingBoxRepository boxes; private final PackingBoxItemRepository boxItems; private final PackingEventRepository events; private final SalesOrderRepository orders; private final SalesOrderItemRepository orderItems; private final InventoryAllocationRepository allocations;
    public PackingQueryService(PackingTaskRepository tasks,PackingBoxRepository boxes,PackingBoxItemRepository boxItems,PackingEventRepository events,SalesOrderRepository orders,SalesOrderItemRepository orderItems,InventoryAllocationRepository allocations){this.tasks=tasks;this.boxes=boxes;this.boxItems=boxItems;this.events=events;this.orders=orders;this.orderItems=orderItems;this.allocations=allocations;}
    public TaskPage findTasks(String keyword,String status){
        List<TaskListItem> result=new ArrayList<>(); Map<Long,PackingTask> byOrder=new HashMap<>();
        for(PackingTask task:tasks.findAllByOrderByStartedAtDesc()){byOrder.put(task.getOrder().getId(),task);if(matches(task,keyword,status))result.add(listItem(task));}
        for(SalesOrder order:orders.findAll()) if(!byOrder.containsKey(order.getId())&&available(order)&&ready(order.getId())&&matchText(order.getOrderNo(),keyword)&&(status==null||status.isBlank()||"READY".equalsIgnoreCase(status))) result.add(readyItem(order));
        result.sort(Comparator.comparing(TaskListItem::requestedShipAt,Comparator.nullsLast(Comparator.naturalOrder())));
        long pending=orders.findAll().stream().filter(o->!byOrder.containsKey(o.getId())&&available(o)&&ready(o.getId())).count();
        return new TaskPage(result,pending,tasks.countByStatus(PackingTaskStatus.DRAFT),tasks.countByStatus(PackingTaskStatus.COMPLETED));
    }
    public Detail findDetail(Long orderId){SalesOrder order=orders.findById(orderId).orElseThrow(()->notFound("订单不存在"));PackingTask task=tasks.findByOrderId(orderId).orElse(null);return detail(order,task);}
    private Detail detail(SalesOrder order,PackingTask task){
        List<SalesOrderItem> items=orderItems.findByOrderIdOrderByIdAsc(order.getId()); List<InventoryAllocation> active=allocations.findBySalesOrderItemIdInOrderByIdAsc(items.stream().map(SalesOrderItem::getId).toList());
        List<AllocationItem> allocationDtos=active.stream().filter(a->a.getStatus()!=InventoryAllocationStatus.RELEASED).map(a->new AllocationItem(a.getId(),a.getAllocationNo(),a.getInventoryLot().getId(),a.getInventoryLot().getLotNo(),a.getSalesOrderItem().getId(),a.getSalesOrderItem().getProductName(),a.getSalesOrderItem().getVariantName(),a.getQuantity(),a.getStatus().name())).toList();
        if(task==null)return new Detail(null,null,"READY",0,order.getId(),order.getOrderNo(),order.getStore().getName(),order.getRequestedShipAt(),null,null,allocationDtos,List.of(),List.of(),available(order)&&ready(order.getId())?List.of("START"):List.of());
        List<PackingBoxItem> packed=boxItems.findByBoxTaskIdOrderByBoxIdAscIdAsc(task.getId());Map<Long,List<PackingBoxItem>> byBox=new HashMap<>();packed.forEach(i->byBox.computeIfAbsent(i.getBox().getId(),x->new ArrayList<>()).add(i));
        List<Box> boxDtos=boxes.findByTaskIdOrderByIdAsc(task.getId()).stream().map(box->new Box(box.getId(),box.getBoxNo(),box.getWeightGrams(),box.getLengthMm(),box.getWidthMm(),box.getHeightMm(),box.getNote(),box.getVersion(),byBox.getOrDefault(box.getId(),List.of()).stream().map(i->new BoxItem(i.getId(),i.getAllocation().getId(),i.getAllocation().getAllocationNo(),i.getAllocation().getInventoryLot().getId(),i.getAllocation().getInventoryLot().getLotNo(),i.getOrderItem().getId(),i.getOrderItem().getProductName(),i.getOrderItem().getVariantName(),i.getQuantity())).toList())).toList();
        List<Event> eventDtos=events.findByTaskIdOrderByOccurredAtDescIdDesc(task.getId()).stream().map(e->new Event(e.getId(),e.getEventType().name(),e.getOperator()==null?"系统":e.getOperator().getDisplayName(),e.getRemark(),e.getOccurredAt())).toList();
        List<String> actions=task.getStatus()==PackingTaskStatus.DRAFT?List.of("EDIT_BOXES","COMPLETE","CANCEL"):List.of();
        return new Detail(task.getId(),task.getTaskNo(),task.getStatus().name(),task.getVersion(),order.getId(),order.getOrderNo(),order.getStore().getName(),order.getRequestedShipAt(),task.getStartedAt(),task.getCompletedAt(),allocationDtos,boxDtos,eventDtos,actions);
    }
    private boolean ready(Long orderId){List<SalesOrderItem> items=orderItems.findByOrderIdOrderByIdAsc(orderId);if(items.isEmpty())return false;List<InventoryAllocation> all=allocations.findBySalesOrderItemIdInOrderByIdAsc(items.stream().map(SalesOrderItem::getId).toList());return items.stream().filter(i->i.getFulfillmentStatus()==OrderItemFulfillmentStatus.NORMAL).allMatch(i->all.stream().filter(a->a.getSalesOrderItem().getId().equals(i.getId())&&a.getStatus()!=InventoryAllocationStatus.RELEASED).mapToInt(InventoryAllocation::getQuantity).sum()==i.getQuantity());}
    private boolean available(SalesOrder order){return order.getExternalStatus()!=ExternalOrderStatus.CANCELED&&!order.isFulfillmentException();}
    private TaskListItem listItem(PackingTask t){List<SalesOrderItem> items=orderItems.findByOrderIdOrderByIdAsc(t.getOrder().getId());return new TaskListItem(t.getId(),t.getOrder().getId(),t.getOrder().getOrderNo(),t.getOrder().getStore().getName(),t.getStatus().name(),items.size(),items.stream().mapToInt(SalesOrderItem::getQuantity).sum(),boxes.findByTaskIdOrderByIdAsc(t.getId()).size(),t.getOrder().getRequestedShipAt(),t.getStartedAt(),t.getCompletedAt());}
    private TaskListItem readyItem(SalesOrder o){List<SalesOrderItem> items=orderItems.findByOrderIdOrderByIdAsc(o.getId());return new TaskListItem(null,o.getId(),o.getOrderNo(),o.getStore().getName(),"READY",items.size(),items.stream().mapToInt(SalesOrderItem::getQuantity).sum(),0,o.getRequestedShipAt(),null,null);}
    private boolean matches(PackingTask t,String keyword,String status){return matchText(t.getOrder().getOrderNo(),keyword)&&(status==null||status.isBlank()||t.getStatus().name().equalsIgnoreCase(status));}
    private boolean matchText(String value,String keyword){return keyword==null||keyword.isBlank()||value.toLowerCase().contains(keyword.trim().toLowerCase());}
    private ApiException notFound(String message){return new ApiException(HttpStatus.NOT_FOUND,"PACKING_NOT_FOUND",message);}
}
