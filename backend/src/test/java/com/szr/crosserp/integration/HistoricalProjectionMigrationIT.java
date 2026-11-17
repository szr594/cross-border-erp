package com.szr.crosserp.integration;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class HistoricalProjectionMigrationIT {
    private record Database(String url,String password,JdbcTemplate jdbc) {}
    private Database beforeUpgrade(){
        String password=System.getenv().getOrDefault("BRIDGE_TEST_DATABASE_PASSWORD","order-it-root-only");
        String schema="historical_projection_"+UUID.randomUUID().toString().replace("-","");
        var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13318/mysql?allowPublicKeyRetrieval=true&useSSL=false","root",password));admin.execute("create database "+schema);
        String url="jdbc:mysql://127.0.0.1:13318/"+schema+"?allowPublicKeyRetrieval=true&useSSL=false";
        Flyway.configure().dataSource(url,"root",password).target("13").load().migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"root",password));
        jdbc.update("insert into app_users(id,login_name,display_name,password_hash,status,must_change_password) values(1,'migration-test','Test','unused-test-hash','ACTIVE',false)");
        jdbc.update("insert into sales_channels(id,code,name,enabled) values(1,'MIGRATION','Test',true)");
        jdbc.update("insert into sales_stores(id,channel_id,external_store_id,code,name,status) values(1,1,'test','MIGRATION','Test','ACTIVE')");
        jdbc.update("insert into sales_orders(id,order_no,store_id,external_order_id,external_status,fulfillment_exception,currency,version,created_at,updated_at) values(1,'MIGRATION',1,'original-order','NEW',false,'USD',4,now(6),now(6))");
        jdbc.update("insert into sales_order_items(id,order_id,external_item_id,product_name,quantity,currency,fulfillment_status,version,created_at,updated_at) values(1,1,'original-item','Test',2,'USD','NORMAL',0,now(6),now(6))");
        jdbc.update("insert into order_procurement_requests(id,order_item_id,requested_quantity,status,requested_by,requested_at,version) values(1,1,2,'CONVERTED',1,now(6),1)");
        jdbc.update("insert into suppliers(id,code,name,platform,status) values(1,'MIGRATION','Test','MANUAL','ACTIVE')");
        jdbc.update("insert into purchase_orders(id,purchase_order_no,supplier_id,status,currency,created_by) values(1,'MIGRATION',1,'ORDERED','CNY',1)");
        jdbc.update("insert into purchase_order_items(id,purchase_order_id,product_name,purchased_quantity,currency) values(1,1,'Test',2,'CNY')");
        jdbc.update("insert into purchase_order_allocations(id,purchase_order_item_id,procurement_request_id,allocated_quantity) values(1,1,1,2)");
        jdbc.update("insert into domestic_packages(id,package_no,purchase_order_id,tracking_number,carrier,status,created_by) values(1,'MIGRATION',1,'MIGRATION','TEST','RECEIVED',1)");
        jdbc.update("insert into goods_receipts(id,receipt_no,domestic_package_id,status,operator_id,confirmed_at,idempotency_key,request_hash) values(1,'MIGRATION',1,'CONFIRMED',1,now(6),'MIGRATION',?)","0".repeat(64));
        jdbc.update("insert into goods_receipt_items(id,goods_receipt_id,purchase_order_item_id,accepted_quantity,damaged_quantity) values(1,1,1,2,0)");
        jdbc.update("insert into inventory_lots(id,lot_no,goods_receipt_item_id,purchase_order_item_id,quality_status,status,initial_quantity,available_quantity,reserved_quantity,received_at) values(1,'MIGRATION',1,1,'ACCEPTED','ALLOCATED',2,0,2,now(6))");
        jdbc.update("insert into inventory_allocations(id,allocation_no,inventory_lot_id,sales_order_item_id,purchase_order_allocation_id,quantity,status,allocated_by,allocated_at) values(1,'MIGRATION',1,1,1,2,'RESERVED',1,now(6))");
        jdbc.update("insert into packing_tasks(id,task_no,sales_order_id,status,started_by,started_at) values(1,'MIGRATION',1,'DRAFT',1,now(6))");
        jdbc.update("insert into packing_boxes(id,box_no,packing_task_id) values(1,'MIGRATION',1)");
        jdbc.update("insert into packing_box_items(id,packing_box_id,sales_order_item_id,inventory_allocation_id,quantity) values(1,1,1,1,2)");
        return new Database(url,password,jdbc);
    }
    @Test void upgradePreservesEveryReferenceAndMovesAllFourForeignKeys() throws Exception {
        var database=beforeUpgrade();var jdbc=database.jdbc();
        Flyway.configure().dataSource(database.url(),"root",database.password()).load().migrate();
        assertThat(jdbc.queryForObject("select source_version from order_business_projections where order_id=1",Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select status from procurement_request_projections where request_id=1",String.class)).isEqualTo("CONVERTED");
        assertThat(jdbc.queryForObject("select procurement_request_id from purchase_order_allocations where id=1",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select reserved_quantity from inventory_lots where id=1",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select quantity from packing_box_items where id=1",Integer.class)).isEqualTo(2);
        Map<String,String> expected=Map.of("purchase_order_allocations","procurement_request_projections","inventory_allocations","order_item_business_projections","packing_tasks","order_business_projections","packing_box_items","order_item_business_projections");
        try(var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()){
            for(var entry:expected.entrySet()){
                Set<String> targets=new HashSet<>();try(var rows=connection.getMetaData().getImportedKeys(connection.getCatalog(),null,entry.getKey())){while(rows.next())targets.add(rows.getString("PKTABLE_NAME"));}
                assertThat(targets).contains(entry.getValue()).doesNotContain("sales_orders","sales_order_items","order_procurement_requests");
            }
        }
        assertThatThrownBy(()->jdbc.update("update purchase_order_allocations set procurement_request_id=999999 where id=1")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void orphanedProjectionStopsBeforeAnyReferenceIsReplaced() throws Exception {
        var database=beforeUpgrade();var jdbc=database.jdbc();
        Flyway.configure().dataSource(database.url(),"root",database.password()).target("14").load().migrate();
        jdbc.update("delete from procurement_request_projections where request_id=1");
        assertThatThrownBy(()->Flyway.configure().dataSource(database.url(),"root",database.password()).load().migrate()).hasStackTraceContaining("Missing projection for historical reference");
        try(var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection();var rows=connection.getMetaData().getImportedKeys(connection.getCatalog(),null,"packing_tasks")){
            Set<String> targets=new HashSet<>();while(rows.next())targets.add(rows.getString("PKTABLE_NAME"));assertThat(targets).contains("sales_orders").doesNotContain("order_business_projections");
        }
    }
}
