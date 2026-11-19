package db.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserve IDs and verify every historical reference before changing any FK. */
public class V19__replace_procurement_references extends BaseJavaMigration {
    private record Reference(String table,String column,String oldTarget,String target,String key,String constraint) {}
    private static final List<Reference> REFERENCES=List.of(
        new Reference("domestic_packages","purchase_order_id","purchase_orders","purchase_business_projections","id","fk_package_purchase_projection"),
        new Reference("goods_receipt_items","purchase_order_item_id","purchase_order_items","purchase_item_projections","id","fk_receipt_purchase_item_projection"),
        new Reference("inventory_lots","purchase_order_item_id","purchase_order_items","purchase_item_projections","id","fk_lot_purchase_item_projection"),
        new Reference("inventory_allocations","purchase_order_allocation_id","purchase_order_allocations","purchase_allocation_projections","id","fk_inventory_purchase_allocation_projection")
    );
    @Override public void migrate(Context context) throws Exception {
        Connection connection=context.getConnection();
        for(String query:List.of(
            "select count(*) from purchase_orders o join purchase_business_projections p on p.id=o.id where p.purchase_order_no<>o.purchase_order_no or p.supplier_id<>o.supplier_id",
            "select count(*) from purchase_order_items i join purchase_item_projections p on p.id=i.id where p.purchase_order_id<>i.purchase_order_id"
        ))try(var statement=connection.createStatement();var rows=statement.executeQuery(query)){
            rows.next();if(rows.getLong(1)!=0)throw new SQLException("Conflicting source IDs; projection handoff requires verified identity mapping");
        }
        for(var ref:REFERENCES)try(var statement=connection.createStatement();var rows=statement.executeQuery(
            "select count(*) from "+ref.table+" source left join "+ref.target+" target on source."+ref.column+"=target."+ref.key+" where source."+ref.column+" is not null and target."+ref.key+" is null")) {
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
