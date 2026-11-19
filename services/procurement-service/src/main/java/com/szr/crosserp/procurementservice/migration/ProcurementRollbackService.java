package com.szr.crosserp.procurementservice.migration;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
/** Offline reverse handoff: all writers/publishers must be stopped and receipt facts drained. */
@Service
public class ProcurementRollbackService {
 private final JdbcTemplate source;
 private static final Map<String,List<String>> IDENTITIES=new LinkedHashMap<>();
 static {
  IDENTITIES.put("suppliers",List.of("code"));
  IDENTITIES.put("purchase_orders",List.of("purchase_order_no","supplier_id"));
  IDENTITIES.put("purchase_order_items",List.of("purchase_order_id","purchased_quantity"));
  IDENTITIES.put("purchase_order_allocations",List.of("purchase_order_item_id","procurement_request_id","allocated_quantity"));
  IDENTITIES.put("purchase_order_events",List.of("purchase_order_id","event_type","occurred_at"));
  IDENTITIES.put("purchase_create_operations",List.of("actor_id","request_hash"));
 }
 public ProcurementRollbackService(JdbcTemplate source){this.source=source;}
 public Map<String,Long> restore(DataSource legacy){
  if(source.queryForObject("select count(*) from purchase_create_operations where state='PENDING'",Long.class)!=0)throw new IllegalStateException("Resolve pending procurement operations");
  if(source.queryForObject("select count(*) from procurement_outbox where published_at is null",Long.class)!=0)throw new IllegalStateException("Drain procurement outbox before rollback");
  JdbcTemplate target=new JdbcTemplate(legacy);
  return new TransactionTemplate(new DataSourceTransactionManager(legacy)).execute(transaction->{
   if(target.queryForObject("select count(*) from receiving_outbox where published_at is null",Long.class)!=0)throw new IllegalStateException("Drain receiving outbox before rollback");
   // Do not overwrite unpropagated warehouse truth with stale remote counters.
   for(var item:source.queryForList("select id,received_quantity,purchased_quantity from purchase_order_items order by id")){
    var totals=target.queryForMap("select coalesce(sum(accepted_quantity),0) accepted,coalesce(sum(accepted_quantity+damaged_quantity),0) inspected from goods_receipt_items where purchase_order_item_id=?",item.get("id"));
    if(((Number)totals.get("accepted")).longValue()!=((Number)item.get("received_quantity")).longValue()||((Number)totals.get("inspected")).longValue()>((Number)item.get("purchased_quantity")).longValue())throw new IllegalStateException("Receiving facts not reconciled");
   }
   var counts=new LinkedHashMap<String,Long>();
   for(var definition:IDENTITIES.entrySet()){
    String table=definition.getKey(),key=table.equals("purchase_create_operations")?"idempotency_key":"id";
    var rows=source.queryForList("select * from "+table+" order by "+key);
    for(Object oldKey:target.queryForList("select "+key+" from "+table,Object.class))if(rows.stream().noneMatch(r->Objects.equals(r.get(key),oldKey)))throw new IllegalStateException("Legacy-only row requires reconciliation: "+table);
    for(var row:rows){
     var previous=target.queryForList("select * from "+table+" where "+key+"=? for update",row.get(key));
     if(!previous.isEmpty()){
      for(String identity:definition.getValue())if(!Objects.equals(row.get(identity),previous.get(0).get(identity)))throw new IllegalStateException("Rollback identity conflict: "+table);
      if(row.get("version") instanceof Number version && ((Number)previous.get(0).get("version")).longValue()>version.longValue())throw new IllegalStateException("Legacy version is newer: "+table);
     }
     var columns=new ArrayList<>(row.keySet());for(String column:columns)if(!column.matches("[a-z_][a-z0-9_]*"))throw new IllegalStateException("Invalid column");
     String insert="insert into "+table+"("+String.join(",",columns)+") values("+String.join(",",Collections.nCopies(columns.size(),"?"))+") on duplicate key update "+String.join(",",columns.stream().filter(c->!c.equals(key)).map(c->c+"=values("+c+")").toList());
     target.update(insert,columns.stream().map(row::get).toArray());
     var copied=target.queryForMap("select * from "+table+" where "+key+"=?",row.get(key));
     for(String column:columns)if(!Objects.equals(row.get(column),copied.get(column)))throw new IllegalStateException("Rollback field mismatch: "+table+"."+column);
    }
    counts.put(table,(long)rows.size());
   }
   for(var order:source.queryForList("select id,purchase_order_no,supplier_id from purchase_orders order by id")){
    var projection=target.queryForMap("select purchase_order_no,supplier_id from purchase_business_projections where id=? for update",order.get("id"));
    if(!Objects.equals(order.get("purchase_order_no"),projection.get("purchase_order_no"))||!Objects.equals(order.get("supplier_id"),projection.get("supplier_id")))throw new IllegalStateException("Rollback projection identity conflict");
    target.update("update purchase_business_projections set authority='LEGACY',status=(select status from purchase_orders where id=?),bridge_confirmed=true,version=version+1 where id=?",order.get("id"),order.get("id"));
   }
   // Data and authority change commit together. Caller restores legacy routes only afterwards.
   return counts;
  });
 }
}
