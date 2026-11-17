package com.szr.crosserp.orderservice.procurement;

import com.szr.crosserp.orderservice.common.exception.ApiException;
import com.szr.crosserp.orderservice.events.OrderOutbox;
import com.szr.crosserp.orderservice.order.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service
public class ProcurementRequestService {
    private final JdbcTemplate jdbc;private final OrderOutbox outbox;
    public ProcurementRequestService(JdbcTemplate jdbc,OrderOutbox outbox){this.jdbc=jdbc;this.outbox=outbox;}
    @Transactional
    public ProcurementRequestBatchResponse create(long orderId,CreateProcurementRequestsRequest command,String subject,List<String> roles){
        long actor=actor(subject,roles);
        var order=order(orderId);
        if(((Number)order.get("version")).longValue()!=command.version())throw conflict("ORDER_VERSION_CONFLICT");
        active(order);
        Set<Long> ids=new HashSet<>();
        for(var item:command.items()){
            if(!ids.add(item.orderItemId()))throw new ApiException(HttpStatus.BAD_REQUEST,"PROCUREMENT_REQUEST_INVALID","商品不能重复");
            var rows=jdbc.queryForList("select quantity,fulfillment_status from sales_order_items where id=? and order_id=?",item.orderItemId(),orderId);
            if(rows.isEmpty())throw new ApiException(HttpStatus.BAD_REQUEST,"PROCUREMENT_REQUEST_INVALID","商品不属于当前订单");
            var row=rows.get(0);
            int requested=jdbc.queryForList("select requested_quantity from order_procurement_requests where order_item_id=? and status<>'WITHDRAWN' for update",Integer.class,item.orderItemId()).stream().mapToInt(Integer::intValue).sum();
            if(!"NORMAL".equals(row.get("fulfillment_status"))||item.quantity()>((Number)row.get("quantity")).intValue()-requested)throw conflict("PROCUREMENT_GAP_CHANGED");
        }
        List<ProcurementRequestResponse> result=new ArrayList<>();
        for(var item:command.items()){
            org.springframework.jdbc.support.KeyHolder key=new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(connection->{var statement=connection.prepareStatement("insert into order_procurement_requests(order_item_id,requested_quantity,status,requested_by,requested_at,note,version) values(?,?,'REQUESTED',?,CURRENT_TIMESTAMP(6),?,0)",java.sql.Statement.RETURN_GENERATED_KEYS);statement.setLong(1,item.orderItemId());statement.setInt(2,item.quantity());statement.setLong(3,actor);statement.setString(4,command.note());return statement;},key);
            long id=Objects.requireNonNull(key.getKey()).longValue();result.add(new ProcurementRequestResponse(id,item.orderItemId(),item.quantity(),com.szr.crosserp.orderservice.order.model.ProcurementRequestStatus.REQUESTED,0));
        }
        jdbc.update("update sales_orders set version=version+1,updated_at=CURRENT_TIMESTAMP(6) where id=?",orderId);
        result.forEach(r->outbox.procurementSnapshot(r.id()));
        return new ProcurementRequestBatchResponse(result,command.version()+1);
    }
    @Transactional
    public ProcurementRequestResponse withdraw(long orderId,long requestId,ReasonVersionRequest command,String subject,List<String> roles){
        actor(subject,roles);order(orderId);
        var rows=jdbc.queryForList("select r.* from order_procurement_requests r join sales_order_items i on i.id=r.order_item_id where r.id=? and i.order_id=? for update",requestId,orderId);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"PROCUREMENT_REQUEST_NOT_FOUND","采购需求不存在");
        var row=rows.get(0);
        if(((Number)row.get("version")).longValue()!=command.version())throw conflict("PROCUREMENT_VERSION_CONFLICT");
        if(!"REQUESTED".equals(row.get("status")))throw conflict("CONVERTED".equals(row.get("status"))?"PROCUREMENT_ALREADY_CONVERTED":"PROCUREMENT_ALREADY_WITHDRAWN");
        if(!jdbc.queryForList("select request_id from procurement_claim_owners where request_id=? for update",Long.class,requestId).isEmpty())throw conflict("PROCUREMENT_REQUEST_CLAIMED");
        jdbc.update("update order_procurement_requests set status='WITHDRAWN',withdrawn_by=?,withdrawn_at=CURRENT_TIMESTAMP(6),note=?,version=version+1 where id=?",Long.parseLong(subject),command.reason(),requestId);
        outbox.procurementSnapshot(requestId);
        return new ProcurementRequestResponse(requestId,((Number)row.get("order_item_id")).longValue(),((Number)row.get("requested_quantity")).intValue(),com.szr.crosserp.orderservice.order.model.ProcurementRequestStatus.WITHDRAWN,command.version()+1);
    }
    private Map<String,Object> order(long id){var rows=jdbc.queryForList("select * from sales_orders where id=? for update",id);if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","订单不存在");if(!jdbc.queryForList("select idempotency_key from order_cancel_operations where order_id=? and state='PENDING' for update",String.class,id).isEmpty())throw conflict("ORDER_CANCEL_PENDING");return rows.get(0);}
    private void active(Map<String,Object> order){if("CANCELED".equals(order.get("external_status"))||Boolean.TRUE.equals(order.get("fulfillment_exception")))throw conflict("ORDER_NOT_ACTIVE");}
    private long actor(String subject,List<String> roles){
        if(roles==null||roles.stream().noneMatch(Set.of("OWNER","OPERATIONS","PROCUREMENT")::contains))throw new ApiException(HttpStatus.FORBIDDEN,"ORDER_COMMAND_FORBIDDEN","无权执行采购需求操作");
        long actor;try{actor=Long.parseLong(subject);}catch(NumberFormatException e){throw new ApiException(HttpStatus.UNAUTHORIZED,"AUTH_UNAUTHORIZED","账号无效");}
        if(jdbc.queryForObject("select count(*) from user_display_projections where id=? and status='ACTIVE'",Long.class,actor)!=1)throw new ApiException(HttpStatus.UNAUTHORIZED,"AUTH_UNAUTHORIZED","账号无效");return actor;
    }
    private ApiException conflict(String code){return new ApiException(HttpStatus.CONFLICT,code,"订单或采购需求已发生变化，请刷新后重试");}
}
