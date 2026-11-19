package com.szr.crosserp.procurementservice.procurement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.szr.crosserp.procurementservice.common.exception.ApiException;
import com.szr.crosserp.procurementservice.procurement.dto.*;
import com.szr.crosserp.procurementservice.user.UserProjectionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@EnableScheduling
@ConditionalOnProperty(name="app.procurement.remote-enabled",havingValue="true")
public class PurchaseBridgeWorkflow {
    @org.springframework.beans.factory.annotation.Autowired private com.szr.crosserp.procurementservice.events.ProcurementOutbox outbox;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final RemoteProcurementClaims remote;
    private final PurchaseOrderCommandService local;
    private final UserProjectionRepository users;
    public PurchaseBridgeWorkflow(JdbcTemplate jdbc,ObjectMapper mapper,PlatformTransactionManager manager,
        RemoteProcurementClaims remote,PurchaseOrderCommandService local,UserProjectionRepository users){
        this.jdbc=jdbc;this.mapper=mapper;this.tx=new TransactionTemplate(manager);this.remote=remote;this.local=local;this.users=users;
    }
    public PurchaseOrderCommandResponse create(String key,CreatePurchaseOrderRequest command,String actor,List<String> roles){
        if(key==null || !key.matches("[A-Za-z0-9_-]{1,64}"))throw error(400,"PURCHASE_IDEMPOTENCY_KEY_REQUIRED");
        if(roles==null || roles.stream().noneMatch(Set.of("OWNER","PROCUREMENT")::contains))throw error(403,"PURCHASE_ORDER_COMMAND_FORBIDDEN");
        long actorId;try{actorId=Long.parseLong(actor);}catch(NumberFormatException e){throw error(401,"AUTH_UNAUTHORIZED");}
        if(users.findById(actorId).filter(u->"ACTIVE".equals(u.getStatus())).isEmpty())throw error(401,"AUTH_UNAUTHORIZED");
        String payload=write(command),hash=hash(payload);
        tx.executeWithoutResult(s->jdbc.update("insert ignore into purchase_create_operations(idempotency_key,actor_id,request_hash,request_payload,state,actor_roles,created_at,updated_at) values(?,?,?,?,'PENDING',?,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
            key,actorId,hash,payload,write(roles.stream().filter(Set.of("OWNER","PROCUREMENT")::contains).sorted().toList())));
        var row=row(key);
        if(((Number)row.get("actor_id")).longValue()!=actorId || !hash.equals(row.get("request_hash")))throw error(409,"PURCHASE_IDEMPOTENCY_CONFLICT");
        return resume(key);
    }
    public PurchaseOrderCommandResponse resume(String key){
        var initial=row(key);if("SUCCEEDED".equals(initial.get("state")))return read((String)initial.get("response_payload"),PurchaseOrderCommandResponse.class);
        if("FAILED".equals(initial.get("operation_phase")))throw stored(initial);
        String lease=UUID.randomUUID().toString();
        int acquired=jdbc.update("update purchase_create_operations set lease_owner=?,lease_until=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 30 SECOND) where idempotency_key=? and state='PENDING' and (lease_until is null or lease_until<CURRENT_TIMESTAMP(6))",lease,key);
        if(acquired!=1)throw pending();
        try {
            var operation=row(key);
            var command=read((String)operation.get("request_payload"),CreatePurchaseOrderRequest.class);
            List<String> roles=readRoles((String)operation.get("actor_roles"));
            String actor=operation.get("actor_id").toString();
            RemoteProcurementClaims.Result claim;
            try{claim=remote.get(key);}catch(ApiException e){
                if(e.getStatus()!=HttpStatus.NOT_FOUND)throw e;
                if("RELEASE_PENDING".equals(operation.get("operation_phase"))){fail(key,lease);throw stored(row(key));}
                try {claim=remote.claim(key,command);}
                catch(ApiException rejected){
                    if("PURCHASE_REMOTE_CLAIM_REJECTED".equals(rejected.getCode())){
                        recordFailure(key,lease,rejected);fail(key,lease);
                    }
                    throw rejected;
                }
            }
            if("RELEASE_PENDING".equals(operation.get("operation_phase"))) {
                if("CLAIMED".equals(claim.status()))claim=remote.release(key);
                if(!"RELEASED".equals(claim.status()))throw pending();
                fail(key,lease);throw stored(row(key));
            }
            if("RELEASED".equals(claim.status())){recordFailure(key,lease,error(409,"PURCHASE_CLAIM_RELEASED"));fail(key,lease);throw stored(row(key));}
            PurchaseOrderCommandResponse response=operation.get("response_payload")==null ? null : read((String)operation.get("response_payload"),PurchaseOrderCommandResponse.class);
            if(response==null){
                if(!"CLAIMED".equals(claim.status()))throw pending();
                phase(key,lease,"CLAIMED");
                try {
                    response=tx.execute(s->{
                        var locked=jdbc.queryForMap("select * from purchase_create_operations where idempotency_key=? for update",key);
                        if(!lease.equals(locked.get("lease_owner")))throw pending();
                        if(locked.get("response_payload")!=null)return read((String)locked.get("response_payload"),PurchaseOrderCommandResponse.class);
                        var created=local.createClaimed(command,actor,roles);
                        jdbc.update("update purchase_create_operations set response_payload=?,purchase_order_id=?,operation_phase='LOCAL_CREATED',updated_at=CURRENT_TIMESTAMP(6) where idempotency_key=?",write(created),created.id(),key);
                        return created;
                    });
                } catch(ApiException e){
                    if(e.getStatus().is4xxClientError())recordFailure(key,lease,e);
                    throw pending();
                }
            }
            phase(key,lease,"CONFIRM_PENDING");
            String reference=String.valueOf(response.id());
            var confirmed=remote.confirm(key,reference);
            if(!"CONFIRMED".equals(confirmed.status()) || !reference.equals(confirmed.purchaseReference()))throw pending();
            Long purchaseOrderId=response.id();
            tx.executeWithoutResult(s->{
                var locked=jdbc.queryForMap("select * from purchase_create_operations where idempotency_key=? for update",key);
                if(!lease.equals(locked.get("lease_owner")) || !(locked.get("purchase_order_id") instanceof Number linked)
                    || linked.longValue()!=purchaseOrderId.longValue())throw pending();
                int visible=jdbc.update("update purchase_orders set bridge_confirmed=true where id=?",purchaseOrderId);
                if(visible!=1)throw pending();
                int updated=jdbc.update("update purchase_create_operations set state='SUCCEEDED',operation_phase='CONFIRMED',updated_at=CURRENT_TIMESTAMP(6) where idempotency_key=? and lease_owner=?",key,lease);
                if(updated!=1)throw pending();
                outbox.snapshot(purchaseOrderId);
            });
            return response;
        } catch(org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException e){throw pending();}
        finally {jdbc.update("update purchase_create_operations set lease_owner=null,lease_until=null,next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 5 SECOND) where idempotency_key=? and lease_owner=?",key,lease);}
    }
    @Scheduled(fixedDelayString="${app.procurement.recovery-delay-ms:5000}")
    public void recover(){
        var keys=jdbc.queryForList("select idempotency_key from purchase_create_operations where state='PENDING' and actor_roles is not null and operation_phase<>'FAILED' and (next_attempt_at is null or next_attempt_at<=CURRENT_TIMESTAMP(6)) and (lease_until is null or lease_until<CURRENT_TIMESTAMP(6)) order by created_at limit 20",String.class);
        for(String key:keys){try{resume(key);}catch(ApiException | org.springframework.dao.DataAccessException ignored){/* Durable pending state is retried on the next bounded scan. */}}
    }
    private Map<String,Object> row(String key){return jdbc.queryForMap("select * from purchase_create_operations where idempotency_key=?",key);}
    private void phase(String key,String lease,String phase){if(jdbc.update("update purchase_create_operations set operation_phase=?,updated_at=CURRENT_TIMESTAMP(6) where idempotency_key=? and lease_owner=?",phase,key,lease)!=1)throw pending();}
    private void recordFailure(String key,String lease,ApiException e){jdbc.update("update purchase_create_operations set operation_phase='RELEASE_PENDING',error_code=?,error_status=?,updated_at=CURRENT_TIMESTAMP(6) where idempotency_key=? and lease_owner=?",e.getCode(),e.getStatus().value(),key,lease);}
    private void fail(String key,String lease){phase(key,lease,"FAILED");}
    private ApiException stored(Map<String,Object> row){return error(row.get("error_status")==null?409:((Number)row.get("error_status")).intValue(),row.get("error_code")==null?"PURCHASE_OPERATION_FAILED":row.get("error_code").toString());}
    private ApiException error(int status,String code){return new ApiException(HttpStatus.valueOf(status),code,"采购操作未完成，请使用原操作键查询或重试");}
    private ApiException pending(){return error(503,"PURCHASE_OPERATION_PENDING");}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private String write(Object value){try{return mapper.writeValueAsString(value);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
    private List<String> readRoles(String value){try{return mapper.readValue(value,new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){});}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
    private <T>T read(String value,Class<T> type){try{return mapper.readValue(value,type);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
}
