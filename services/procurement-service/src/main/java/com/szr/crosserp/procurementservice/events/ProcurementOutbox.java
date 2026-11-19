package com.szr.crosserp.procurementservice.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ProcurementOutbox {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public ProcurementOutbox(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 @Transactional(propagation=Propagation.MANDATORY)
 public void snapshot(long id) {
  var order=jdbc.queryForMap("select p.*,s.code supplier_code,s.name supplier_name from purchase_orders p join suppliers s on s.id=p.supplier_id where p.id=? for update",id);
  if(!Boolean.TRUE.equals(order.get("bridge_confirmed")))throw new IllegalStateException("Unconfirmed procurement must not publish");
  jdbc.update("insert ignore into procurement_event_versions(purchase_order_id,revision) values(?,0)",id);
  long version=jdbc.queryForObject("select revision from procurement_event_versions where purchase_order_id=? for update",Long.class,id)+1;
  jdbc.update("update procurement_event_versions set revision=? where purchase_order_id=?",version,id);
  Map<String,Object> payload=new LinkedHashMap<>();
  payload.put("purchaseOrderId",id);payload.put("purchaseOrderNo",order.get("purchase_order_no"));payload.put("supplierId",order.get("supplier_id"));payload.put("supplierCode",order.get("supplier_code"));payload.put("supplierName",order.get("supplier_name"));payload.put("status",order.get("status"));payload.put("currency",order.get("currency"));
  payload.put("items",jdbc.query("select id,product_name,variant_name,purchased_quantity,supplier_sku from purchase_order_items where purchase_order_id=? order by id",(rs,n)->{
   Map<String,Object> item=new LinkedHashMap<>();long itemId=rs.getLong("id");item.put("purchaseOrderItemId",itemId);item.put("productName",rs.getString("product_name"));item.put("variantName",rs.getString("variant_name"));item.put("supplierSku",rs.getString("supplier_sku"));item.put("purchasedQuantity",rs.getInt("purchased_quantity"));
   item.put("allocations",jdbc.query("select a.id,a.procurement_request_id,a.allocated_quantity,r.order_item_id from purchase_order_allocations a join procurement_request_projections r on r.request_id=a.procurement_request_id where a.purchase_order_item_id=? order by a.id",(a,index)->Map.of("allocationId",a.getLong("id"),"requestId",a.getLong("procurement_request_id"),"orderItemId",a.getLong("order_item_id"),"quantity",a.getInt("allocated_quantity")),itemId));return item;
  },id));
  String eventId=UUID.randomUUID().toString();var now=java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  var envelope=Map.of("eventId",eventId,"eventType","procurement.snapshot.v1","schemaVersion",1,"aggregateId",id,"aggregateVersion",version,"occurredAt",now.toString(),"payload",payload);
  try{jdbc.update("insert into procurement_outbox(event_id,event_type,aggregate_id,aggregate_version,payload,occurred_at) values(?,?,?,?,?,?)",eventId,"procurement.snapshot.v1",id,version,mapper.writeValueAsString(envelope),java.sql.Timestamp.from(now));}
  catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalStateException("Cannot serialize procurement snapshot",error);}
 }
}
