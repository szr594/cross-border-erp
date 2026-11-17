package com.szr.crosserp.orderservice.events;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.*;
import java.time.Instant;
import java.sql.Timestamp;
@Service
public class OrderOutbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public OrderOutbox(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void created(com.szr.crosserp.orderservice.order.model.SalesOrder order,List<Map<String,Object>> items,Instant now) {
        Long orderId=order.getId();long version=order.getVersion();
        String id=UUID.randomUUID().toString();
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("orderId",orderId);payload.put("orderNo",order.getOrderNo());
        payload.put("externalStatus",order.getExternalStatus().name());payload.put("exception",order.isFulfillmentException());
        payload.put("responsibleUserId",order.getResponsibleUser()==null?null:order.getResponsibleUser().getId());payload.put("items",items);
        payload.put("identityKey",order.getStore().getId()+":"+order.getExternalOrderId());
        payload.put("storeName",order.getStore().getName());payload.put("requestedShipAt",order.getRequestedShipAt()==null?null:order.getRequestedShipAt().toString());
        var envelope=Map.of("eventId",id,"eventType","order.created.v2","schemaVersion",3,
            "aggregateId",orderId,"aggregateVersion",version,"occurredAt",now.toString(),"correlationId",id,
            "payload",payload);
        try {
            jdbc.update("insert into outbox_events(event_id,event_type,aggregate_id,aggregate_version,payload,occurred_at) values(?,?,?,?,?,?)",
                id,"order.created.v2",orderId,version,mapper.writeValueAsString(envelope),Timestamp.from(now));
        } catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Cannot serialize order event",e);}
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void procurementSnapshot(long requestId){
        var request=jdbc.queryForMap("select r.*,i.order_id from order_procurement_requests r join sales_order_items i on i.id=r.order_item_id where r.id=?",requestId);
        long orderId=((Number)request.get("order_id")).longValue();
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("requestId",requestId);payload.put("orderItemId",request.get("order_item_id"));
        payload.put("quantity",request.get("requested_quantity"));payload.put("status",request.get("status"));
        payload.put("requestedAt",instant(request.get("requested_at")).toString());
        payload.put("orderSnapshot",snapshot(orderId));
        String id=UUID.randomUUID().toString();long version=((Number)request.get("version")).longValue();Instant now=Instant.now();
        var envelope=Map.of("eventId",id,"eventType","order.procurement.snapshot.v1","schemaVersion",1,"aggregateId",requestId,"aggregateVersion",version,"occurredAt",now.toString(),"payload",payload);
        try{jdbc.update("insert into outbox_events(event_id,event_type,aggregate_id,aggregate_version,payload,occurred_at) values(?,?,?,?,?,?)",id,"order.procurement.snapshot.v1",requestId,version,mapper.writeValueAsString(envelope),Timestamp.from(now));}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
    }

    private Map<String,Object> snapshot(long orderId){
        var row=jdbc.queryForMap("select o.*,s.name as store_name from sales_orders o join sales_stores s on s.id=o.store_id where o.id=?",orderId);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("orderId",orderId);result.put("orderVersion",row.get("version"));result.put("orderNo",row.get("order_no"));
        result.put("externalStatus",row.get("external_status"));result.put("exception",row.get("fulfillment_exception"));
        result.put("responsibleUserId",row.get("responsible_user_id"));result.put("identityKey",row.get("store_id")+":"+row.get("external_order_id"));
        result.put("storeName",row.get("store_name"));result.put("requestedShipAt",row.get("requested_ship_at")==null?null:instant(row.get("requested_ship_at")).toString());
        result.put("items",jdbc.query("select * from sales_order_items where order_id=? order by id",(rs,n)->{
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("orderItemId",rs.getLong("id"));item.put("quantity",rs.getInt("quantity"));item.put("productName",rs.getString("product_name"));
            item.put("externalSku",rs.getString("external_sku"));item.put("externalItemId",rs.getString("external_item_id"));item.put("variantName",rs.getString("variant_name"));
            item.put("erpSkuId",rs.getObject("erp_sku_id"));item.put("fulfillmentStatus",rs.getString("fulfillment_status"));return item;
        },orderId));return result;
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void changed(long orderId){
        var payload=snapshot(orderId);long version=((Number)payload.get("orderVersion")).longValue();String id=UUID.randomUUID().toString();Instant now=Instant.now();
        var event=Map.of("eventId",id,"eventType","order.updated.v2","schemaVersion",3,"aggregateId",orderId,"aggregateVersion",version,"occurredAt",now.toString(),"payload",payload);
        try{jdbc.update("insert into outbox_events(event_id,event_type,aggregate_id,aggregate_version,payload,occurred_at) values(?,?,?,?,?,?)",id,"order.updated.v2",orderId,version,mapper.writeValueAsString(event),Timestamp.from(now));}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
    }
    private Instant instant(Object value){return value instanceof Timestamp t?t.toInstant():((java.time.LocalDateTime)value).toInstant(java.time.ZoneOffset.UTC);}
}
