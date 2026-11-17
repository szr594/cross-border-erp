package db.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserve IDs and verify every historical reference before changing any FK. */
public class V15__replace_order_references extends BaseJavaMigration {
    private record Reference(String table,String column,String oldTarget,String target,String key,String constraint) {}
    private static final List<Reference> REFERENCES=List.of(
        new Reference("purchase_order_allocations","procurement_request_id","order_procurement_requests","procurement_request_projections","request_id","fk_purchase_allocation_demand_projection"),
        new Reference("inventory_allocations","sales_order_item_id","sales_order_items","order_item_business_projections","order_item_id","fk_inventory_allocation_item_projection"),
        new Reference("packing_tasks","sales_order_id","sales_orders","order_business_projections","order_id","fk_packing_task_order_projection"),
        new Reference("packing_box_items","sales_order_item_id","sales_order_items","order_item_business_projections","order_item_id","fk_packing_box_item_projection")
    );
    @Override public void migrate(Context context) throws Exception {
        Connection connection=context.getConnection();
        for(String query:List.of(
            "select count(*) from sales_orders o join order_business_projections p on p.order_id=o.id where p.order_no<>o.order_no",
            "select count(*) from sales_order_items i join order_item_business_projections p on p.order_item_id=i.id where p.order_id<>i.order_id"
        ))try(var statement=connection.createStatement();var rows=statement.executeQuery(query)){
            rows.next();if(rows.getLong(1)!=0)throw new SQLException("Conflicting source IDs; projection handoff requires verified identity mapping");
        }
        for(var ref:REFERENCES)try(var statement=connection.createStatement();var rows=statement.executeQuery(
            "select count(*) from "+ref.table+" source left join "+ref.target+" target on source."+ref.column+"=target."+ref.key+" where target."+ref.key+" is null")) {
            rows.next();if(rows.getLong(1)!=0)throw new SQLException("Missing projection for historical reference: "+ref.table+"."+ref.column);
        }
        boolean mysql=connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");
        for(var ref:REFERENCES){
            List<String> oldKeys=new ArrayList<>();
            try(var rows=connection.getMetaData().getImportedKeys(connection.getCatalog(),null,ref.table)){
                while(rows.next())if(ref.column.equalsIgnoreCase(rows.getString("FKCOLUMN_NAME")) && ref.oldTarget.equalsIgnoreCase(rows.getString("PKTABLE_NAME")))oldKeys.add(rows.getString("FK_NAME"));
            }
            for(String key:oldKeys)try(var statement=connection.createStatement()){
                String quote=mysql?"`":"\"";
                statement.execute("alter table "+ref.table+(mysql?" drop foreign key ":" drop constraint ")+quote+key.replace(quote,quote+quote)+quote);
            }
            try(var statement=connection.createStatement()){
                statement.execute("alter table "+ref.table+" add constraint "+ref.constraint+" foreign key ("+ref.column+") references "+ref.target+"("+ref.key+")");
            }
        }
    }
}
