package com.szr.crosserp.procurementservice.events;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class DemandSnapshotConsumer {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public DemandSnapshotConsumer(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 @Transactional public void accept(byte[] body)throws java.io.IOException{
  var event=json.readTree(body);String eventId=text(event,"eventId",36);UUID.fromString(eventId);
  String type=text(event,"eventType",64);boolean demand="order.procurement.snapshot.v1".equals(type);
  if(!(demand&&event.path("schemaVersion").asInt()==1)&&!(Set.of("order.created.v2","order.updated.v2").contains(type)&&event.path("schemaVersion").asInt()==3))throw bad();
  long aggregate=number(event,"aggregateId",true),version=number(event,"aggregateVersion",false);
  var payload=event.path("payload");var order=demand?payload.path("orderSnapshot"):payload;
  long orderId=number(order,"orderId",true),orderVersion=demand?number(order,"orderVersion",false):version;
  if(!demand&&aggregate!=orderId)throw bad();
  String identity=text(order,"identityKey",300),orderNo=text(order,"orderNo",32),external=text(order,"externalStatus",64);
  if(!order.path("exception").isBoolean()||!order.path("items").isArray()||order.path("items").isEmpty())throw bad();
  Set<Long> items=new HashSet<>();for(var item:order.path("items")){
   if(!items.add(number(item,"orderItemId",true)))throw bad();
   quantity(item,"quantity");text(item,"productName",500);text(item,"fulfillmentStatus",32);
  }
  long requestId=0,itemId=0;int amount=0;String status=null;
  if(demand){requestId=number(payload,"requestId",true);itemId=number(payload,"orderItemId",true);amount=quantity(payload,"quantity");status=text(payload,"status",32);if(requestId!=aggregate||!items.contains(itemId)||!Set.of("REQUESTED","CONVERTED","WITHDRAWN").contains(status))throw bad();}
  if(jdbc.update("insert ignore into procurement_event_inbox values('order-demands',?,CURRENT_TIMESTAMP(6))",eventId)==0)return;
  int inserted=jdbc.update("insert ignore into procurement_order_projections values(?,?,?,?,?,?)",orderId,orderNo,identity,external,order.path("exception").asBoolean(),orderVersion);
  var old=jdbc.queryForMap("select * from procurement_order_projections where order_id=? for update",orderId);
  if(!identity.equals(old.get("identity_key"))||!orderNo.equals(old.get("order_no")))throw bad();
  long oldVersion=((Number)old.get("source_version")).longValue();
  if(inserted==1||orderVersion>=oldVersion){
   if(orderVersion==oldVersion&&inserted==0&&(!external.equals(old.get("external_status"))||order.path("exception").asBoolean()!=Boolean.TRUE.equals(old.get("fulfillment_exception"))))throw bad();
   if(!items.containsAll(jdbc.queryForList("select order_item_id from procurement_item_projections where order_id=?",Long.class,orderId)))throw bad();
   jdbc.update("update procurement_order_projections set external_status=?,fulfillment_exception=?,source_version=? where order_id=?",external,order.path("exception").asBoolean(),orderVersion,orderId);
   for(var item:order.path("items")){
    long id=number(item,"orderItemId",true);int count=quantity(item,"quantity");String name=text(item,"productName",500),state=text(item,"fulfillmentStatus",32);
    int fresh=jdbc.update("insert ignore into procurement_item_projections(order_item_id,order_id,product_name,quantity,fulfillment_status,source_version) values(?,?,?,?,?,?)",id,orderId,name,count,state,orderVersion);
    var previous=jdbc.queryForMap("select * from procurement_item_projections where order_item_id=? for update",id);
    if(((Number)previous.get("order_id")).longValue()!=orderId)throw bad();
    if(fresh==0&&orderVersion==((Number)previous.get("source_version")).longValue()&&(!name.equals(previous.get("product_name"))||count!=((Number)previous.get("quantity")).intValue()||!state.equals(previous.get("fulfillment_status"))))throw bad();
    jdbc.update("update procurement_item_projections set product_name=?,variant_name=?,external_sku=?,quantity=?,fulfillment_status=?,source_version=? where order_item_id=?",name,optional(item,"variantName",500),optional(item,"externalSku",128),count,state,orderVersion,id);
   }
  }
  if(demand){
   int fresh=jdbc.update("insert ignore into procurement_request_projections values(?,?,?,?,?)",requestId,itemId,amount,status,version);
   var previous=jdbc.queryForMap("select * from procurement_request_projections where request_id=? for update",requestId);
   if(((Number)previous.get("order_item_id")).longValue()!=itemId)throw bad();
   long before=((Number)previous.get("source_version")).longValue();
   if(fresh==0&&version==before&&(amount!=((Number)previous.get("requested_quantity")).intValue()||!status.equals(previous.get("status"))))throw bad();
   if(version>before)jdbc.update("update procurement_request_projections set requested_quantity=?,status=?,source_version=? where request_id=?",amount,status,version,requestId);
  }
 }
 private long number(JsonNode node,String key,boolean positive){var v=node.path(key);if(!v.isIntegralNumber()||!v.canConvertToLong()||v.longValue()<(positive?1:0))throw bad();return v.longValue();}
 private int quantity(JsonNode n,String k){return Math.toIntExact(number(n,k,true));}
 private String text(JsonNode n,String k,int limit){var v=n.path(k);if(!v.isTextual()||v.asText().isBlank()||v.asText().length()>limit)throw bad();return v.asText();}
 private String optional(JsonNode n,String k,int limit){return n.path(k).isNull()||n.path(k).isMissingNode()?null:text(n,k,limit);}
 private IllegalArgumentException bad(){return new IllegalArgumentException("Invalid or conflicting order demand snapshot");}
}
