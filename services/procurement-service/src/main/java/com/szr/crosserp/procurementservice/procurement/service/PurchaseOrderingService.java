package com.szr.crosserp.procurementservice.procurement.service;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import com.szr.crosserp.procurementservice.common.exception.ApiException;
import com.szr.crosserp.procurementservice.procurement.model.*;
import com.szr.crosserp.procurementservice.procurement.repository.*;
import com.szr.crosserp.procurementservice.user.UserProjectionRepository;

@Service
public class PurchaseOrderingService {
    public record Result(long id,String status,long version,Instant orderedAt) {}
    @org.springframework.beans.factory.annotation.Autowired private com.szr.crosserp.procurementservice.events.ProcurementOutbox outbox;
    private final PurchaseOrderRepository orders;
    private final PurchaseOrderEventRepository events;
    private final UserProjectionRepository users;
    public PurchaseOrderingService(PurchaseOrderRepository orders,PurchaseOrderEventRepository events,UserProjectionRepository users) {
        this.orders=orders;this.events=events;this.users=users;
    }
    @Transactional
    public Result confirm(long id,long version,String subject,List<String> roles) {
        if(roles==null||roles.stream().noneMatch(List.of("OWNER","PROCUREMENT")::contains))
            throw new ApiException(HttpStatus.FORBIDDEN,"PURCHASE_COMMAND_FORBIDDEN","无权确认采购下单");
        long actorId;
        try {actorId=Long.parseLong(subject);} catch(NumberFormatException e){throw new ApiException(HttpStatus.UNAUTHORIZED,"AUTH_UNAUTHORIZED","登录状态无效");}
        var actor=users.findById(actorId).filter(u->"ACTIVE".equals(u.getStatus()))
            .orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"AUTH_UNAUTHORIZED","登录状态无效"));
        var order=orders.findByIdForUpdate(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PURCHASE_ORDER_NOT_FOUND","采购单不存在"));
        if(!order.isBridgeConfirmed())throw new ApiException(HttpStatus.CONFLICT,"PURCHASE_BRIDGE_PENDING","采购认领尚未完成");
        if(order.getStatus()==PurchaseOrderStatus.ORDERED && order.getVersion()==version+1
            && order.getOrderedBy()!=null && order.getOrderedBy().getId().equals(actorId))return result(order);
        if(order.getVersion()!=version)throw new ApiException(HttpStatus.CONFLICT,"PURCHASE_VERSION_CONFLICT","采购单已变化，请刷新后重试");
        if(order.getStatus()!=PurchaseOrderStatus.DRAFT)throw new ApiException(HttpStatus.CONFLICT,"PURCHASE_STATUS_CONFLICT","仅草稿可确认下单");
        Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);order.confirmOrdered(actor,now);orders.saveAndFlush(order);
        events.save(new PurchaseOrderEvent(order,"ORDERED",actor,"草稿确认下单",now));
        outbox.snapshot(id);
        return result(order);
    }
    private Result result(PurchaseOrder order){return new Result(order.getId(),order.getStatus().name(),order.getVersion(),order.getOrderedAt());}
}
