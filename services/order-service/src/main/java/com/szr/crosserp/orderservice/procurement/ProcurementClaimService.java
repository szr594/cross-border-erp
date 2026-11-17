package com.szr.crosserp.orderservice.procurement;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import com.szr.crosserp.orderservice.common.exception.ApiException;

@Service
public class ProcurementClaimService {
    public record Item(long requestId,int quantity,long version) {}
    public record Command(String idempotencyKey,List<Item> items) {}
    public record Result(String claimId,String status,String purchaseReference) {}
    private final JdbcTemplate jdbc;
    private final com.szr.crosserp.orderservice.events.OrderOutbox outbox;
    public ProcurementClaimService(JdbcTemplate jdbc,com.szr.crosserp.orderservice.events.OrderOutbox outbox){this.jdbc=jdbc;this.outbox=outbox;}
    @Transactional
    public Result claim(Command command) {
        if(command==null || command.idempotencyKey()==null || !command.idempotencyKey().matches("[A-Za-z0-9_-]{1,64}")
            || command.items()==null || command.items().isEmpty() || command.items().size()>1000) throw bad("认领参数无效");
        var items=command.items().stream().sorted(Comparator.comparingLong(Item::requestId)).toList();
        lockOrders(items.stream().map(Item::requestId).toList());
        Set<Long> seen=new HashSet<>();StringBuilder canonical=new StringBuilder();
        for(var item:items){if(item.requestId()<=0 || item.quantity()<=0 || item.version()<0 || !seen.add(item.requestId()))throw bad("采购需求不可重复且必须完整认领");
            canonical.append(item.requestId()).append(':').append(item.quantity()).append(':').append(item.version()).append(';');}
        String hash;
        try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        int created=jdbc.update("insert ignore into procurement_claims(claim_id,request_hash,status,created_at,updated_at) values(?,?,'CLAIMED',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",command.idempotencyKey(),hash);
        var row=lock(command.idempotencyKey());
        if(!hash.equals(row.get("request_hash")))throw conflict("CLAIM_IDEMPOTENCY_CONFLICT","幂等键已用于不同需求");
        if(created==0)return result(row);
        for(var item:items){
            var requests=jdbc.queryForList("select requested_quantity,status,version,order_item_id from order_procurement_requests where id=? for update",item.requestId());
            if(requests.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"PROCUREMENT_REQUEST_NOT_FOUND","采购需求不存在");
            var request=requests.get(0);
            if(!"REQUESTED".equals(request.get("status")) || ((Number)request.get("version")).longValue()!=item.version()
                || ((Number)request.get("requested_quantity")).intValue()!=item.quantity())throw conflict("PROCUREMENT_REQUEST_UNAVAILABLE","采购需求状态、版本或数量已变化");
            long itemId=((Number)request.get("order_item_id")).longValue();
            var order=jdbc.queryForMap("select o.external_status,i.fulfillment_status from sales_order_items i join sales_orders o on o.id=i.order_id where i.id=?",itemId);
            if("CANCELED".equals(order.get("external_status")) || !"NORMAL".equals(order.get("fulfillment_status")))throw conflict("ORDER_UNAVAILABLE","订单已不可采购");
            if(jdbc.update("insert ignore into procurement_claim_owners(request_id,claim_id) values(?,?)",item.requestId(),command.idempotencyKey())==0)
                throw conflict("PROCUREMENT_REQUEST_CLAIMED","采购需求已被认领");
        }
        return result(row);
    }
    @Transactional
    public Result confirm(String id,String reference) {
        if(reference==null || reference.isBlank() || reference.length()>64)throw bad("采购单引用无效");
        lockOrders(requestIds(id));
        var claim=lock(id);String state=(String)claim.get("status");
        if("CONFIRMED".equals(state)){if(!reference.equals(claim.get("purchase_reference")))throw conflict("CLAIM_REFERENCE_CONFLICT","认领已用于其他采购单");return result(claim);}
        if(!"CLAIMED".equals(state))throw conflict("CLAIM_NOT_CONFIRMABLE","认领已释放");
        for(Long request:requestIds(id)) {
            var row=jdbc.queryForMap("select status from order_procurement_requests where id=? for update",request);
            if(!"REQUESTED".equals(row.get("status")))throw conflict("PROCUREMENT_REQUEST_UNAVAILABLE","需求状态已变化");
            jdbc.update("update order_procurement_requests set status='CONVERTED',version=version+1 where id=?",request);
            outbox.procurementSnapshot(request);
        }
        jdbc.update("update procurement_claims set status='CONFIRMED',purchase_reference=?,updated_at=CURRENT_TIMESTAMP(6) where claim_id=?",reference,id);
        return new Result(id,"CONFIRMED",reference);
    }
    @Transactional
    public Result release(String id) {
        lockOrders(requestIds(id));
        var claim=lock(id);
        if("CONFIRMED".equals(claim.get("status")))throw conflict("CLAIM_ALREADY_CONFIRMED","已确认认领不能释放");
        if("RELEASED".equals(claim.get("status")))return result(claim);
        jdbc.update("delete from procurement_claim_owners where claim_id=?",id);
        jdbc.update("update procurement_claims set status='RELEASED',updated_at=CURRENT_TIMESTAMP(6) where claim_id=?",id);
        return new Result(id,"RELEASED",null);
    }
    @Transactional(readOnly=true)
    public Result get(String id) {
        var rows=jdbc.queryForList("select * from procurement_claims where claim_id=?",id);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"CLAIM_NOT_FOUND","认领不存在");return result(rows.get(0));
    }
    private Map<String,Object> lock(String id){var rows=jdbc.queryForList("select * from procurement_claims where claim_id=? for update",id);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"CLAIM_NOT_FOUND","认领不存在");return rows.get(0);}
    private List<Long> requestIds(String id){return jdbc.queryForList("select request_id from procurement_claim_owners where claim_id=? order by request_id",Long.class,id);}
    private void lockOrders(List<Long> requests){
        Set<Long> orders=new TreeSet<>();
        for(long id:requests)orders.addAll(jdbc.queryForList("select i.order_id from order_procurement_requests r join sales_order_items i on i.id=r.order_item_id where r.id=?",Long.class,id));
        for(long id:orders){
            jdbc.queryForList("select id from sales_orders where id=? for update",id);
            if(!jdbc.queryForList("select idempotency_key from order_cancel_operations where order_id=? and state='PENDING' for update",String.class,id).isEmpty())throw conflict("ORDER_CANCEL_PENDING","订单正在取消");
        }
    }
    private Result result(Map<String,Object> row){return new Result((String)row.get("claim_id"),(String)row.get("status"),(String)row.get("purchase_reference"));}
    private ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,"CLAIM_REQUEST_INVALID",message);}
    private ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
}
