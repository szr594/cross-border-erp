package com.szr.crosserp.integration.orders;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.inventory.service.InventoryOrderCancellationService;
import com.szr.crosserp.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.Map;

@Service
public class OrderCancellationCallbackService {
    public record Command(String idempotencyKey,long actorId,String reason) {}
    private final JdbcTemplate jdbc;private final InventoryOrderCancellationService inventory;private final UserRepository users;
    public OrderCancellationCallbackService(JdbcTemplate jdbc,InventoryOrderCancellationService inventory,UserRepository users){this.jdbc=jdbc;this.inventory=inventory;this.users=users;}
    @Transactional
    public Map<String,String> cancel(long orderId,Command command){
        if(command.idempotencyKey()==null||!command.idempotencyKey().matches("[A-Za-z0-9_-]{1,64}")||command.actorId()<=0||command.reason()==null||command.reason().isBlank()||command.reason().length()>500)throw new ApiException(HttpStatus.BAD_REQUEST,"ORDER_CANCEL_INVALID","取消参数无效");
        var rows=jdbc.queryForList("select authority from order_business_projections where order_id=? for update",orderId);
        if(rows.isEmpty())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"ORDER_PROJECTION_NOT_READY","订单投影尚未就绪");
        if(!"ORDER".equals(rows.get(0).get("authority")))throw new ApiException(HttpStatus.CONFLICT,"ORDER_AUTHORITY_CONFLICT","订单尚未完成所有权迁移");
        var previous=jdbc.queryForList("select * from order_cancellation_receipts where idempotency_key=?",command.idempotencyKey());
        if(!previous.isEmpty()){
            var row=previous.get(0);
            if(((Number)row.get("order_id")).longValue()!=orderId||((Number)row.get("actor_id")).longValue()!=command.actorId()||!command.reason().equals(row.get("reason")))throw new ApiException(HttpStatus.CONFLICT,"ORDER_CANCEL_IDEMPOTENCY_CONFLICT","取消幂等键已用于不同请求");
            return Map.of("status","RELEASED");
        }
        var actor=users.findById(command.actorId()).orElseThrow(()->new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"USER_PROJECTION_NOT_READY","操作员工尚未同步"));
        var items=jdbc.queryForList("select order_item_id from order_item_business_projections where order_id=? order by order_item_id",Long.class,orderId);
        inventory.releaseReservedForOrder(orderId,items,actor,command.reason(),Instant.now());
        jdbc.update("update order_business_projections set external_status='CANCELED' where order_id=?",orderId);
        jdbc.update("update order_item_business_projections set fulfillment_status='CANCELED' where order_id=?",orderId);
        jdbc.update("insert into order_cancellation_receipts(idempotency_key,order_id,actor_id,reason) values(?,?,?,?)",command.idempotencyKey(),orderId,command.actorId(),command.reason());
        return Map.of("status","RELEASED");
    }
}
