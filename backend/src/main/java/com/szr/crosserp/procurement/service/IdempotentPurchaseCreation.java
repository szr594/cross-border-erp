package com.szr.crosserp.procurement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.szr.crosserp.procurement.dto.*;
import com.szr.crosserp.common.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class IdempotentPurchaseCreation {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PurchaseOrderCommandService commands;
    private final TransactionTemplate transactions;
    public IdempotentPurchaseCreation(JdbcTemplate jdbc,ObjectMapper mapper,PurchaseOrderCommandService commands,PlatformTransactionManager manager){
        this.jdbc=jdbc;this.mapper=mapper;this.commands=commands;this.transactions=new TransactionTemplate(manager);
    }
    public PurchaseOrderCommandResponse create(String key,CreatePurchaseOrderRequest request,String actor,List<String> roles){
        if(key==null || !key.matches("[A-Za-z0-9_-]{1,64}"))throw new ApiException(HttpStatus.BAD_REQUEST,"PURCHASE_IDEMPOTENCY_KEY_REQUIRED","请提供有效的Idempotency-Key");
        if(roles==null || roles.stream().noneMatch(Set.of("OWNER","PROCUREMENT")::contains))throw new ApiException(HttpStatus.FORBIDDEN,"PURCHASE_ORDER_COMMAND_FORBIDDEN","无权创建采购单");
        long actorId;
        try{actorId=Long.parseLong(actor);}catch(NumberFormatException e){throw new ApiException(HttpStatus.UNAUTHORIZED,"AUTH_UNAUTHORIZED","登录状态无效");}
        String payload=write(request);String hash;
        try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        String fingerprint=hash;
        transactions.executeWithoutResult(status->jdbc.update("insert ignore into purchase_create_operations(idempotency_key,actor_id,request_hash,request_payload,state,created_at,updated_at) values(?,?,?,?,'PENDING',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",key,actorId,fingerprint,payload));
        try {
            return transactions.execute(status->{
                var row=jdbc.queryForMap("select * from purchase_create_operations where idempotency_key=? for update",key);
                if(((Number)row.get("actor_id")).longValue()!=actorId || !fingerprint.equals(row.get("request_hash")))
                    throw new ApiException(HttpStatus.CONFLICT,"PURCHASE_IDEMPOTENCY_CONFLICT","幂等键已用于不同请求或账号");
                if("SUCCEEDED".equals(row.get("state")))return read((String)row.get("response_payload"));
                var response=commands.create(request,actor,roles);
                jdbc.update("update purchase_create_operations set state='SUCCEEDED',response_payload=?,updated_at=CURRENT_TIMESTAMP(6) where idempotency_key=?",write(response),key);
                return response;
            });
        } catch(org.springframework.dao.DataAccessException e){
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PURCHASE_OPERATION_PENDING","采购操作结果暂未确定，请使用相同幂等键重试");
        }
    }
    private String write(Object value){try{return mapper.writeValueAsString(value);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
    private PurchaseOrderCommandResponse read(String value){try{return mapper.readValue(value,PurchaseOrderCommandResponse.class);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
}
