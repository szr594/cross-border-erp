package com.szr.crosserp.integration.procurement;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
@Service
public class ReceivingOutbox {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public ReceivingOutbox(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 @Transactional(propagation=Propagation.MANDATORY)
 public void snapshot(long orderId,long receiptId){
  jdbc.update("insert ignore into receiving_event_versions(purchase_order_id,revision) values(?,0)",orderId);
  long version=jdbc.queryForObject("select revision from receiving_event_versions where purchase_order_id=? for update",Long.class,orderId)+1;
  jdbc.update("update receiving_event_versions set revision=? where purchase_order_id=?",version,orderId);
  var items=jdbc.query("select i.id,coalesce(sum(r.accepted_quantity),0) accepted,coalesce(sum(r.accepted_quantity+r.damaged_quantity),0) inspected from purchase_item_projections i left join goods_receipt_items r on r.purchase_order_item_id=i.id where i.purchase_order_id=? group by i.id order by i.id",(rs,n)->Map.of("purchaseOrderItemId",rs.getLong("id"),"sourceVersion",version,"acceptedQuantity",rs.getLong("accepted"),"inspectedQuantity",rs.getLong("inspected")),orderId);
  String id=UUID.randomUUID().toString();var now=java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  var event=Map.of("eventId",id,"eventType","receiving.snapshot.v1","schemaVersion",1,"aggregateId",orderId,"aggregateVersion",version,"occurredAt",now.toString(),"payload",Map.of("receiptId",receiptId,"items",items));
  try{jdbc.update("insert into receiving_outbox(event_id,event_type,aggregate_id,aggregate_version,payload,occurred_at) values(?,?,?,?,?,?)",id,"receiving.snapshot.v1",orderId,version,mapper.writeValueAsString(event),java.sql.Timestamp.from(now));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
 }
}
