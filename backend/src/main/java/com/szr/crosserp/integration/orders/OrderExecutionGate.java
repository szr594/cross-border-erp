package com.szr.crosserp.integration.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.szr.crosserp.common.exception.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.util.*;
import java.util.function.Supplier;
import java.sql.Timestamp;
import java.time.Instant;

@Service @EnableScheduling
public class OrderExecutionGate {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(OrderExecutionGate.class);
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final ObjectMapper mapper;
    private final ObjectProvider<RemoteExecutionAuthority> remote;private final boolean enabled;
    private static final ThreadLocal<Set<Long>> AUTHORIZED=new ThreadLocal<>();
    public OrderExecutionGate(JdbcTemplate jdbc,PlatformTransactionManager manager,ObjectMapper mapper,ObjectProvider<RemoteExecutionAuthority> remote,@Value("${app.procurement.remote-enabled:false}")boolean enabled){
        this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.mapper=mapper;this.remote=remote;this.enabled=enabled;
    }
    public <T>T execute(Collection<Long> ids,Supplier<T> action){
        if(!enabled)return tx.execute(s->{for(long id:ids.stream().distinct().sorted().toList())jdbc.queryForList("select id from sales_orders where id=? for update",id);return action.get();});
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Execution gate must precede the transaction");
        List<Long> orders=ids.stream().distinct().sorted().toList();if(orders.isEmpty())return action.get();
        for(long id:orders)if(jdbc.queryForObject("select count(*) from order_business_projections where order_id=? and authority='ORDER'",Long.class,id)!=1)
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"ORDER_PROJECTION_NOT_READY","订单投影尚未完成权威交接");
        String operation=UUID.randomUUID().toString();
        jdbc.update("insert into fulfillment_execution_operations(operation_id,order_ids,state,lease_until) values(?,?,'ACQUIRING',?)",operation,write(orders),Timestamp.from(Instant.now().plusSeconds(60)));
        try{
            for(long id:orders)remote.getObject().acquire(id,operation);
            if(jdbc.update("update fulfillment_execution_operations set state='READY',lease_until=? where operation_id=? and state='ACQUIRING' and lease_until>CURRENT_TIMESTAMP(6)",Timestamp.from(Instant.now().plusSeconds(60)),operation)!=1)throw pending();
            T result=tx.execute(status->{
                var row=jdbc.queryForMap("select * from fulfillment_execution_operations where operation_id=? for update",operation);
                Object lease=row.get("lease_until");Instant until=lease instanceof Timestamp t?t.toInstant():((java.time.LocalDateTime)lease).toInstant(java.time.ZoneOffset.UTC);
                if(!"READY".equals(row.get("state"))||until.isBefore(Instant.now()))throw pending();
                AUTHORIZED.set(Set.copyOf(orders));
                try{T response=action.get();jdbc.update("update fulfillment_execution_operations set state='COMMITTED' where operation_id=?",operation);return response;}
                finally{AUTHORIZED.remove();}
            });
            releaseSafely(operation);return result;
        }catch(RuntimeException failure){releaseSafely(operation);if(failure instanceof org.springframework.dao.DataAccessException||failure instanceof org.springframework.transaction.TransactionException)throw pending();throw failure;}
    }
    public void require(long orderId){
        if(enabled&&(AUTHORIZED.get()==null||!AUTHORIZED.get().contains(orderId)))throw pending();
    }
    public static void requireRole(List<String> roles,Set<String> allowed,String code){
        if(roles==null||roles.stream().noneMatch(allowed::contains))throw new ApiException(HttpStatus.FORBIDDEN,code,"无权执行此操作");
    }
    private void releaseSafely(String operation){
        try{
            List<Long> orders=tx.execute(s->{
                var row=jdbc.queryForMap("select * from fulfillment_execution_operations where operation_id=? for update",operation);
                if("RELEASED".equals(row.get("state")))return List.<Long>of();
                jdbc.update("update fulfillment_execution_operations set state='RELEASING' where operation_id=?",operation);
                return read((String)row.get("order_ids"));
            });
            for(long id:Objects.requireNonNull(orders))remote.getObject().release(id,operation);
            jdbc.update("update fulfillment_execution_operations set state='RELEASED' where operation_id=?",operation);
        }catch(RuntimeException failure){LOG.warn("Execution fence release pending for operation {}",operation);}
    }
    @Scheduled(fixedDelayString="${app.orders.execution-recovery-delay-ms:5000}")
    public void recover(){
        if(!enabled)return;
        for(String id:jdbc.queryForList("select operation_id from fulfillment_execution_operations where state in ('COMMITTED','RELEASING') or (state in ('ACQUIRING','READY') and lease_until<CURRENT_TIMESTAMP(6)) order by created_at limit 20",String.class))releaseSafely(id);
    }
    private String write(List<Long> ids){try{return mapper.writeValueAsString(ids);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
    private List<Long> read(String value){try{return mapper.readValue(value,new com.fasterxml.jackson.core.type.TypeReference<List<Long>>(){});}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
    private ApiException pending(){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"ORDER_EXECUTION_PENDING","订单执行授权尚未就绪，请使用原幂等键重试");}
}
