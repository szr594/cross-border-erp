package com.szr.crosserp.integration.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.sql.Timestamp;
import java.time.Instant;

@Service
public class OrderEventConsumer {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public OrderEventConsumer(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}

    @Transactional
    public void accept(byte[] body) throws java.io.IOException {
        JsonNode event=mapper.readTree(body);
        String eventId=event.path("eventId").asText();UUID.fromString(eventId);
        long id=positive(event,"aggregateId"),version=nonnegative(event,"aggregateVersion");
        String type=event.path("eventType").asText();
        JsonNode payload=event.path("payload");
        int schema=event.path("schemaVersion").asInt();
        if("order.procurement.snapshot.v1".equals(type)&&schema==1){
            if(positive(payload,"requestId")!=id)throw invalid("Demand aggregate mismatch");
            long itemId=positive(payload,"orderItemId");positiveInt(payload,"quantity");
            com.szr.crosserp.order.model.ProcurementRequestStatus.valueOf(text(payload,"status",32));
            Instant requestedAt=Instant.parse(text(payload,"requestedAt",64));
            JsonNode order=payload.path("orderSnapshot");validateOrder(order,true);
            long orderVersion=nonnegative(order,"orderVersion");
            boolean contained=false;
            for(JsonNode item:order.path("items"))if(item.path("orderItemId").asLong()==itemId)contained=true;
            if(!contained)throw invalid("Demand item absent from order snapshot");
            if(!inbox(eventId))return;
            applyOrder(order,orderVersion,true);
            jdbc.update("insert ignore into procurement_request_projections(request_id,order_item_id,requested_quantity,status,source_version,requested_at) values(?,?,?,?,-1,?)",
                id,itemId,payload.path("quantity").asInt(),payload.path("status").asText(),Timestamp.from(requestedAt));
            var current=jdbc.queryForMap("select * from procurement_request_projections where request_id=? for update",id);
            if(((Number)current.get("order_item_id")).longValue()!=itemId)throw invalid("Demand ID belongs to another item");
            if(version>((Number)current.get("source_version")).longValue())
                jdbc.update("update procurement_request_projections set requested_quantity=?,status=?,source_version=?,requested_at=? where request_id=?",
                    payload.path("quantity").asInt(),payload.path("status").asText(),version,Timestamp.from(requestedAt),id);
            return;
        }
        if(Set.of("order.created.v2","order.updated.v2").contains(type)&&(schema==2||schema==3)){
            if(positive(payload,"orderId")!=id)throw invalid("Order aggregate mismatch");
            validateOrder(payload,schema==3);
            if(!inbox(eventId))return;
            applyOrder(payload,version,schema==3);
            return;
        }
        if(!"order.created.v1".equals(type)||schema!=1||positive(payload,"orderId")!=id
            ||!payload.path("items").isArray()||payload.path("items").isEmpty())throw invalid("Unsupported event envelope");
        for(var item:payload.path("items")){positive(item,"orderItemId");positiveInt(item,"quantity");}
        if(!inbox(eventId))return;
        jdbc.update("insert ignore into order_event_projections(order_id,source_version,items_payload,updated_at) values(?,-1,'[]',CURRENT_TIMESTAMP(6))",id);
        long current=jdbc.queryForObject("select source_version from order_event_projections where order_id=? for update",Long.class,id);
        List<Map<String,Object>> safeItems=new ArrayList<>();
        for(var item:payload.path("items"))safeItems.add(Map.of("orderItemId",item.path("orderItemId").asLong(),"quantity",item.path("quantity").asInt()));
        if(version>current)jdbc.update("update order_event_projections set source_version=?,items_payload=?,updated_at=CURRENT_TIMESTAMP(6) where order_id=?",
            version,mapper.writeValueAsString(safeItems),id);
    }
    private void validateOrder(JsonNode order,boolean extended){
        positive(order,"orderId");text(order,"orderNo",32);
        com.szr.crosserp.order.model.ExternalOrderStatus.valueOf(text(order,"externalStatus",64));
        if(!order.path("exception").isBoolean()||!order.path("items").isArray()||order.path("items").isEmpty())throw invalid("Invalid order snapshot");
        if(extended){text(order,"identityKey",512);text(order,"storeName",200);nullableTime(order,"requestedShipAt");}
        var ids=new HashSet<Long>();
        for(var item:order.path("items")){
            if(!ids.add(positive(item,"orderItemId")))throw invalid("Duplicate order item");
            positiveInt(item,"quantity");text(item,"productName",500);nullableText(item,"externalSku",128);
            com.szr.crosserp.order.model.OrderItemFulfillmentStatus.valueOf(text(item,"fulfillmentStatus",32));
            if(extended){text(item,"externalItemId",128);nullableText(item,"variantName",500);}
        }
    }
    private void applyOrder(JsonNode payload,long version,boolean extended){
        long orderId=payload.path("orderId").asLong();
        jdbc.update("insert ignore into order_business_projections(order_id,order_no,external_status,fulfillment_exception,source_version,updated_at,authority,identity_key) values(?,?,?,?,-1,CURRENT_TIMESTAMP(6),'ORDER',?)",
            orderId,payload.path("orderNo").asText(),payload.path("externalStatus").asText(),payload.path("exception").asBoolean(),extended?payload.path("identityKey").asText():null);
        var current=jdbc.queryForMap("select * from order_business_projections where order_id=? for update",orderId);
        String identity=(String)current.get("identity_key");
        if(identity!=null&&extended&&!identity.equals(payload.path("identityKey").asText()))throw invalid("Order identity conflict");
        boolean legacy="LEGACY".equals(current.get("authority"));
        if(legacy&&(!extended||identity==null||!identity.equals(payload.path("identityKey").asText())))throw invalid("Legacy handoff requires matching source identity");
        long previous=((Number)current.get("source_version")).longValue();
        if(version<previous||(!legacy&&version==previous&&(!extended||identity!=null)))return;
        if(version==previous&&identity==null&&!payload.path("orderNo").asText().equals(current.get("order_no")))throw invalid("Same-version identity conflict");
        Set<Long> ids=new HashSet<>();for(var item:payload.path("items"))ids.add(item.path("orderItemId").asLong());
        var existing=jdbc.queryForList("select order_item_id from order_item_business_projections where order_id=?",Long.class,orderId);
        if(!ids.containsAll(existing))throw invalid("Snapshot cannot remove historical item IDs");
        Long responsible=payload.path("responsibleUserId").isIntegralNumber()?payload.path("responsibleUserId").asLong():null;
        jdbc.update("update order_business_projections set order_no=?,external_status=?,fulfillment_exception=?,responsible_user_id=?,source_version=?,authority='ORDER',updated_at=CURRENT_TIMESTAMP(6) where order_id=?",
            payload.path("orderNo").asText(),payload.path("externalStatus").asText(),payload.path("exception").asBoolean(),responsible,version,orderId);
        if(extended)jdbc.update("update order_business_projections set identity_key=?,store_name=?,requested_ship_at=? where order_id=?",
            payload.path("identityKey").asText(),payload.path("storeName").asText(),nullableTime(payload,"requestedShipAt"),orderId);
        for(var item:payload.path("items")){
            long itemId=item.path("orderItemId").asLong();
            jdbc.update("insert ignore into order_item_business_projections(order_item_id,order_id,product_name,quantity,fulfillment_status,source_version) values(?,?,?,?,?,-1)",
                itemId,orderId,item.path("productName").asText(),item.path("quantity").asInt(),item.path("fulfillmentStatus").asText());
            var row=jdbc.queryForMap("select order_id,external_item_id from order_item_business_projections where order_item_id=? for update",itemId);
            if(((Number)row.get("order_id")).longValue()!=orderId)throw invalid("Item ID belongs to a different order");
            if(extended&&row.get("external_item_id")!=null&&!row.get("external_item_id").equals(item.path("externalItemId").asText()))throw invalid("Item source identity conflict");
            jdbc.update("update order_item_business_projections set product_name=?,external_sku=?,quantity=?,fulfillment_status=?,source_version=? where order_item_id=?",
                item.path("productName").asText(),nullableText(item,"externalSku",128),item.path("quantity").asInt(),item.path("fulfillmentStatus").asText(),version,itemId);
            if(extended)jdbc.update("update order_item_business_projections set variant_name=?,external_item_id=?,erp_sku_id=? where order_item_id=?",
                nullableText(item,"variantName",500),item.path("externalItemId").asText(),item.path("erpSkuId").isIntegralNumber()?item.path("erpSkuId").asLong():null,itemId);
        }
    }
    private boolean inbox(String id){return jdbc.update("insert ignore into order_event_inbox(consumer_name,event_id,processed_at) values('legacy-order-sync',?,CURRENT_TIMESTAMP(6))",id)==1;}
    private long positive(JsonNode n,String key){long v=nonnegative(n,key);if(v==0)throw invalid("Expected positive "+key);return v;}
    private long nonnegative(JsonNode n,String key){var value=n.path(key);if(!value.isIntegralNumber()||!value.canConvertToLong()||value.longValue()<0)throw invalid("Invalid "+key);return value.longValue();}
    private int positiveInt(JsonNode n,String key){if(!n.path(key).canConvertToInt())throw invalid("Invalid "+key);return Math.toIntExact(positive(n,key));}
    private String text(JsonNode n,String key,int limit){var v=n.path(key);if(!v.isTextual()||v.asText().isBlank()||v.asText().length()>limit)throw invalid("Invalid "+key);return v.asText();}
    private String nullableText(JsonNode n,String key,int limit){var v=n.path(key);if(v.isNull()||v.isMissingNode())return null;if(!v.isTextual()||v.asText().length()>limit)throw invalid("Invalid "+key);return v.asText();}
    private Timestamp nullableTime(JsonNode n,String key){String value=nullableText(n,key,64);return value==null?null:Timestamp.from(Instant.parse(value));}
    private IllegalArgumentException invalid(String detail){return new IllegalArgumentException(detail);}
}
