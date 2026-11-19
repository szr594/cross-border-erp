package com.szr.crosserp.procurementservice.events;

import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ReceivingFactsConsumer {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public ReceivingFactsConsumer(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 @Transactional
 public void accept(byte[] body)throws java.io.IOException {
  JsonNode event=mapper.readTree(body);String eventId=event.path("eventId").asText();UUID.fromString(eventId);
  if(!"receiving.snapshot.v1".equals(event.path("eventType").asText())||event.path("schemaVersion").asInt()!=1)throw new IllegalArgumentException("Unsupported receiving event");
  long orderId=positive(event.path("aggregateId"));JsonNode items=event.path("payload").path("items");
  if(!items.isArray()||items.isEmpty())throw new IllegalArgumentException("Missing receiving items");
  var orders=jdbc.queryForList("select status from purchase_orders where id=? for update",orderId);if(orders.isEmpty())throw new IllegalStateException("Purchase projection not ready");
  if(jdbc.update("insert ignore into procurement_event_inbox(consumer_name,event_id,processed_at) values('receiving-facts',?,CURRENT_TIMESTAMP(6))",eventId)==0)return;
  if(!Set.of("ORDERED","PARTIALLY_RECEIVED","RECEIVED").contains(orders.get(0).get("status")))throw new IllegalStateException("Purchase not receiving");
  Set<Long> seen=new HashSet<>();boolean changed=false;
  for(JsonNode item:items){
   long id=positive(item.path("purchaseOrderItemId"));long version=nonnegative(item.path("sourceVersion"));long inspected=nonnegative(item.path("inspectedQuantity")),accepted=nonnegative(item.path("acceptedQuantity"));
   if(!seen.add(id)||accepted>inspected||inspected>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid receiving counts");
   var rows=jdbc.queryForList("select purchased_quantity from purchase_order_items where id=? and purchase_order_id=? for update",id,orderId);
   if(rows.isEmpty()||inspected>((Number)rows.get(0).get("purchased_quantity")).longValue())throw new IllegalArgumentException("Invalid receiving item");
   var previous=jdbc.queryForList("select * from procurement_receiving_projections where purchase_order_item_id=?",id);
   if(!previous.isEmpty()){
    var old=previous.get(0);long oldVersion=((Number)old.get("source_version")).longValue();
    if(version<oldVersion)continue;
    if(version==oldVersion){if(inspected!=((Number)old.get("inspected_quantity")).longValue()||accepted!=((Number)old.get("accepted_quantity")).longValue())throw new IllegalArgumentException("Same version receiving conflict");continue;}
    if(inspected<((Number)old.get("inspected_quantity")).longValue()||accepted<((Number)old.get("accepted_quantity")).longValue())throw new IllegalArgumentException("Receiving counts regressed");
    jdbc.update("update procurement_receiving_projections set inspected_quantity=?,accepted_quantity=?,source_version=? where purchase_order_item_id=?",inspected,accepted,version,id);
   }else jdbc.update("insert into procurement_receiving_projections values(?,?,?,?)",id,inspected,accepted,version);
   jdbc.update("update purchase_order_items set received_quantity=?,version=version+1,updated_at=CURRENT_TIMESTAMP(6) where id=?",accepted,id);changed=true;
  }
  if(changed){var totals=jdbc.queryForMap("select sum(i.purchased_quantity) purchased,sum(coalesce(r.inspected_quantity,0)) received from purchase_order_items i left join procurement_receiving_projections r on r.purchase_order_item_id=i.id where i.purchase_order_id=?",orderId);long expected=((Number)totals.get("purchased")).longValue(),received=((Number)totals.get("received")).longValue();
   jdbc.update("update purchase_orders set status=?,version=version+1,updated_at=CURRENT_TIMESTAMP(6) where id=?",received>=expected?"RECEIVED":received>0?"PARTIALLY_RECEIVED":"ORDERED",orderId);
  }
 }
 private long nonnegative(JsonNode value){if(!value.isIntegralNumber()||!value.canConvertToLong()||value.longValue()<0)throw new IllegalArgumentException("Invalid receiving number");return value.longValue();}
 private long positive(JsonNode value){long number=nonnegative(value);if(number==0)throw new IllegalArgumentException("Missing receiving ID");return number;}
}
