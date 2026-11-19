package com.szr.crosserp.procurementservice.migration;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.szr.crosserp.procurementservice.events.ProcurementOutbox;
/** Maintenance-only: all source and target business writers must be stopped. */
@Service
public class ProcurementDataMigrationService {
 private static final List<String> TABLES=List.of("suppliers","purchase_orders","purchase_order_items","purchase_order_allocations","purchase_order_events","purchase_create_operations");
 private final JdbcTemplate target;private final ProcurementOutbox outbox;
 public ProcurementDataMigrationService(JdbcTemplate target,ProcurementOutbox outbox){this.target=target;this.outbox=outbox;}
 @Transactional(rollbackFor=Exception.class)
 public Map<String,Long> migrate(DataSource source)throws SQLException{
  for(String table:TABLES)if(target.queryForObject("select count(*) from "+table,Long.class)!=0)throw new IllegalStateException("Target must be empty: "+table);
  for(String table:List.of("procurement_outbox","procurement_event_versions","procurement_receiving_projections"))if(target.queryForObject("select count(*) from "+table,Long.class)!=0)throw new IllegalStateException("Target event state must be empty");
  var counts=new LinkedHashMap<String,Long>();
  try(var connection=source.getConnection()){
   connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);connection.setReadOnly(true);connection.setAutoCommit(false);
   try{
    if(count(connection,"select count(*) from purchase_create_operations where state='PENDING'")!=0)throw new IllegalStateException("Resolve pending claims before migration");
    if(count(connection,"select count(*) from purchase_orders where bridge_confirmed=false")!=0)throw new IllegalStateException("Unconfirmed purchases cannot migrate");
    // Identity is synchronized separately; never copy credentials or overwrite a newer directory.
    try(var st=connection.createStatement();var rows=st.executeQuery("select distinct created_by from purchase_orders union select actor_id from purchase_create_operations union select operator_id from purchase_order_events where operator_id is not null union select ordered_by from purchase_orders where ordered_by is not null union select canceled_by from purchase_orders where canceled_by is not null")){
     while(rows.next())if(target.queryForObject("select count(*) from user_display_projections where id=?",Integer.class,rows.getLong(1))!=1)throw new IllegalStateException("Synchronize employee directory before migration");
    }
    // Demand IDs and quantities must already be projected from Order authority.
    try(var st=connection.createStatement();var rows=st.executeQuery("select procurement_request_id,allocated_quantity from purchase_order_allocations")){
     while(rows.next()){
      var demand=target.queryForList("select requested_quantity from procurement_request_projections where request_id=?",Integer.class,rows.getLong(1));
      if(demand.size()!=1||demand.get(0)!=rows.getInt(2))throw new IllegalStateException("Missing or conflicting demand projection");
     }
    }
    for(String table:TABLES){
     var columns=target.queryForRowSet("select * from "+table+" where 1=0").getMetaData().getColumnNames();
     for(String column:columns)if(!column.matches("[a-z_][a-z0-9_]*"))throw new IllegalStateException("Invalid column");
     String fields=String.join(",",Arrays.stream(columns).map(c->"`"+c+"`").toList());
     long copied=0;
     try(var st=connection.createStatement();var rows=st.executeQuery("select "+fields+" from "+table)){
      while(rows.next()){Object[] values=new Object[columns.length];for(int i=0;i<values.length;i++)values[i]=rows.getObject(i+1);
       target.update("insert into "+table+"("+fields+") values("+String.join(",",Collections.nCopies(values.length,"?"))+")",values);copied++;
      }
     }
     if(target.queryForObject("select count(*) from "+table,Long.class)!=copied)throw new IllegalStateException("Count mismatch: "+table);
     counts.put(table,copied);
    }
    try(var st=connection.createStatement();var rows=st.executeQuery("select i.id,i.received_quantity,i.purchased_quantity,coalesce(sum(r.accepted_quantity),0) accepted,coalesce(sum(r.accepted_quantity+r.damaged_quantity),0) inspected from purchase_order_items i left join goods_receipt_items r on r.purchase_order_item_id=i.id group by i.id,i.received_quantity,i.purchased_quantity")){
     while(rows.next()){
      long accepted=rows.getLong("accepted"),inspected=rows.getLong("inspected");
      if(accepted!=rows.getLong("received_quantity")||accepted>inspected||inspected>rows.getLong("purchased_quantity"))throw new IllegalStateException("Historical receiving totals mismatch");
      target.update("insert into procurement_receiving_projections values(?,?,?,0)",rows.getLong("id"),inspected,accepted);
     }
    }
    for(Long id:target.queryForList("select id from purchase_orders order by id",Long.class))outbox.snapshot(id);
   }finally{connection.rollback();}
  }
  return counts;
 }
 private long count(Connection c,String sql)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
}
