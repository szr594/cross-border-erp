package com.szr.crosserp.orderservice.fulfillment;

import com.szr.crosserp.orderservice.common.exception.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service
public class ExecutionAuthorityService {
    public record Authorization(String operationId,long orderId,String status) {}
    private final JdbcTemplate jdbc;
    public ExecutionAuthorityService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional
    public Authorization acquire(long orderId,String operationId){
        UUID.fromString(operationId);
        var rows=jdbc.queryForList("select external_status,fulfillment_exception from sales_orders where id=? for update",orderId);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","订单不存在");
        var existing=jdbc.queryForList("select status from fulfillment_execution_holds where order_id=? and operation_id=?",orderId,operationId);
        if(!existing.isEmpty()){
            if(!"ACTIVE".equals(existing.get(0).get("status")))throw new ApiException(HttpStatus.CONFLICT,"ORDER_EXECUTION_RELEASED","执行授权已释放");
            return new Authorization(operationId,orderId,"ACTIVE");
        }
        var row=rows.get(0);
        if("CANCELED".equals(row.get("external_status"))||Boolean.TRUE.equals(row.get("fulfillment_exception"))
            ||jdbc.queryForObject("select count(*) from order_cancel_operations where order_id=? and state='PENDING'",Long.class,orderId)>0)
            throw new ApiException(HttpStatus.CONFLICT,"ORDER_EXECUTION_FORBIDDEN","订单当前不允许履约操作");
        jdbc.update("insert into fulfillment_execution_holds(operation_id,order_id,status) values(?,?,'ACTIVE')",operationId,orderId);
        return new Authorization(operationId,orderId,"ACTIVE");
    }
    @Transactional
    public Authorization release(long orderId,String operationId){
        UUID.fromString(operationId);
        if(jdbc.queryForList("select id from sales_orders where id=? for update",orderId).isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","订单不存在");
        // A release tombstone also fences a delayed acquire whose HTTP response was lost.
        jdbc.update("insert ignore into fulfillment_execution_holds(operation_id,order_id,status) values(?,?,'RELEASED')",operationId,orderId);
        jdbc.update("update fulfillment_execution_holds set status='RELEASED' where order_id=? and operation_id=?",orderId,operationId);
        return new Authorization(operationId,orderId,"RELEASED");
    }
}
