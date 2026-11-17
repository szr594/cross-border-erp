package com.szr.crosserp.orderservice.fulfillment;

import com.szr.crosserp.orderservice.common.exception.ApiException;
import com.szr.crosserp.orderservice.events.OrderOutbox;
import com.szr.crosserp.orderservice.order.dto.*;
import com.szr.crosserp.orderservice.order.model.ExternalOrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.web.client.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service @EnableScheduling
public class OrderCancellationService {
    public record Callback(String idempotencyKey,long actorId,String reason) {}
    public record Released(String status) {}
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final OrderOutbox outbox;private final ObjectMapper mapper;
    private final String url,token;
    public OrderCancellationService(JdbcTemplate jdbc,PlatformTransactionManager manager,OrderOutbox outbox,ObjectMapper mapper,@Value("${LEGACY_BACKEND_INTERNAL_URL:}")String url,@Value("${ORDER_INTERNAL_SERVICE_TOKEN:}")String token){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.outbox=outbox;this.mapper=mapper;this.url=url;this.token=token;}
    public OrderCommandResponse cancel(long orderId,ReasonVersionRequest command,String suppliedKey,String subject,List<String> roles){
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Cancellation RPC must run outside the transaction");
        if(roles==null||!roles.contains("OWNER"))throw new ApiException(HttpStatus.FORBIDDEN,"ORDER_COMMAND_FORBIDDEN","仅老板可以取消订单");
        long actor;try{actor=Long.parseLong(subject);}catch(NumberFormatException e){throw new ApiException(HttpStatus.UNAUTHORIZED,"AUTH_UNAUTHORIZED","账号无效");}
        if(jdbc.queryForObject("select count(*) from user_display_projections where id=? and status='ACTIVE'",Long.class,actor)!=1)throw new ApiException(HttpStatus.UNAUTHORIZED,"AUTH_UNAUTHORIZED","账号无效");
        String key=suppliedKey==null?derivedKey(orderId,actor,command):suppliedKey;
        if(!key.matches("[A-Za-z0-9_-]{1,64}"))throw new ApiException(HttpStatus.BAD_REQUEST,"ORDER_CANCEL_IDEMPOTENCY_INVALID","取消幂等键无效");
        tx.executeWithoutResult(s->{
            var order=lockOrder(orderId);
            var existing=jdbc.queryForList("select * from order_cancel_operations where idempotency_key=? for update",key);
            if(!existing.isEmpty()){
                var row=existing.get(0);
                if(((Number)row.get("order_id")).longValue()!=orderId||((Number)row.get("actor_id")).longValue()!=actor||((Number)row.get("expected_version")).longValue()!=command.version()||!command.reason().equals(row.get("reason")))throw conflict("ORDER_CANCEL_IDEMPOTENCY_CONFLICT");
                return;
            }
            if(((Number)order.get("version")).longValue()!=command.version())throw conflict("ORDER_VERSION_CONFLICT");
            if("CANCELED".equals(order.get("external_status")))throw conflict("ORDER_ALREADY_CANCELED");
            if(jdbc.queryForObject("select count(*) from order_cancel_operations where order_id=? and state='PENDING'",Long.class,orderId)>0)throw conflict("ORDER_CANCEL_IN_PROGRESS");
            if(jdbc.queryForObject("select count(*) from fulfillment_execution_holds where order_id=? and status='ACTIVE'",Long.class,orderId)>0)throw pending();
            if(jdbc.queryForObject("select count(*) from procurement_claim_owners owner join procurement_claims c on c.claim_id=owner.claim_id join order_procurement_requests r on r.id=owner.request_id join sales_order_items i on i.id=r.order_item_id where i.order_id=? and c.status='CLAIMED'",Long.class,orderId)>0)throw pending();
            jdbc.update("insert into order_cancel_operations(idempotency_key,order_id,actor_id,expected_version,reason,state) values(?,?,?,?,?,'PENDING')",key,orderId,actor,command.version(),command.reason());
        });
        return resume(key);
    }
    public OrderCommandResponse resume(String key){
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Cancellation recovery RPC must run outside the transaction");
        var row=jdbc.queryForMap("select * from order_cancel_operations where idempotency_key=?",key);
        long orderId=((Number)row.get("order_id")).longValue();
        if("SUCCEEDED".equals(row.get("state")))return response(orderId,((Number)row.get("completed_version")).longValue());
        if("FAILED".equals(row.get("state")))throw conflict((String)row.get("failure_code"));
        if(url.isBlank()||token.getBytes(StandardCharsets.UTF_8).length<32)throw pending();
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(2000);factory.setReadTimeout(5000);
        try{
            var released=RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-Service-Token",token).build().post().uri("/internal/orders/{id}/cancel-inventory",orderId)
                .body(new Callback(key,((Number)row.get("actor_id")).longValue(),(String)row.get("reason"))).retrieve().body(Released.class);
            if(released==null||!"RELEASED".equals(released.status()))throw pending();
        }catch(RestClientResponseException e){
            String code="";try{code=mapper.readTree(e.getResponseBodyAsString()).path("code").asText();}catch(java.io.IOException ignored){/* Unknown response stays pending. */}
            if(e.getStatusCode().value()==409&&"ORDER_CANCEL_BLOCKED_BY_CONSUMED_INVENTORY".equals(code)){
                jdbc.update("update order_cancel_operations set state='FAILED',failure_code=? where idempotency_key=? and state='PENDING'",code,key);throw conflict(code);
            }
            throw pending();
        }catch(RestClientException e){throw pending();}
        return tx.execute(s->{
            var order=lockOrder(orderId);
            var operation=jdbc.queryForMap("select * from order_cancel_operations where idempotency_key=? for update",key);
            if("SUCCEEDED".equals(operation.get("state")))return response(orderId,((Number)operation.get("completed_version")).longValue());
            if(!"PENDING".equals(operation.get("state")))throw conflict("ORDER_CANCEL_OPERATION_CHANGED");
            var requests=jdbc.queryForList("select r.id from order_procurement_requests r join sales_order_items i on i.id=r.order_item_id where i.order_id=? and r.status='REQUESTED' order by r.id",Long.class,orderId);
            for(long request:requests)jdbc.update("update order_procurement_requests set status='WITHDRAWN',withdrawn_by=?,withdrawn_at=CURRENT_TIMESTAMP(6),version=version+1 where id=?",operation.get("actor_id"),request);
            jdbc.update("update sales_order_items set fulfillment_status='CANCELED',version=version+1 where order_id=?",orderId);
            jdbc.update("update sales_orders set external_status='CANCELED',version=version+1,updated_at=CURRENT_TIMESTAMP(6) where id=?",orderId);
            long version=((Number)order.get("version")).longValue()+1;
            for(long request:requests)outbox.procurementSnapshot(request);
            outbox.changed(orderId);
            jdbc.update("update order_cancel_operations set state='SUCCEEDED',completed_version=? where idempotency_key=?",version,key);
            return response(orderId,version);
        });
    }
    @Scheduled(fixedDelayString="${app.orders.cancel-recovery-delay-ms:5000}")
    public void recover(){if(url.isBlank())return;for(String key:jdbc.queryForList("select idempotency_key from order_cancel_operations where state='PENDING' order by created_at limit 20",String.class))try{resume(key);}catch(RuntimeException ignored){/* Durable pending operations are retried. */}}
    private Map<String,Object> lockOrder(long id){var rows=jdbc.queryForList("select * from sales_orders where id=? for update",id);if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","订单不存在");return rows.get(0);}
    private OrderCommandResponse response(long id,long version){boolean exception=Boolean.TRUE.equals(jdbc.queryForObject("select fulfillment_exception from sales_orders where id=?",Boolean.class,id));return new OrderCommandResponse(id,ExternalOrderStatus.CANCELED,exception,version);}
    private String derivedKey(long id,long actor,ReasonVersionRequest input){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((id+":"+actor+":"+input.version()+":"+input.reason()).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private ApiException pending(){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"ORDER_CANCEL_PENDING","订单取消尚未完成，请使用原幂等键重试");}
    private ApiException conflict(String code){return new ApiException(HttpStatus.CONFLICT,code,"订单取消状态冲突");}
}
