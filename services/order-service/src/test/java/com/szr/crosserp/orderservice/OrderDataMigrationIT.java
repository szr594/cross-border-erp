package com.szr.crosserp.orderservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.szr.crosserp.orderservice.events.OrderOutbox;
import com.szr.crosserp.orderservice.order.service.OrderDataMigrationService;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class OrderDataMigrationIT {
    record Database(String name,DriverManagerDataSource data,JdbcTemplate jdbc) {}
    private Database database() {
        String name="migration_it_"+UUID.randomUUID().toString().replace("-","");
        var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13318/mysql?allowPublicKeyRetrieval=true&useSSL=false","root","order-it-root-only"));
        admin.execute("create database "+name);
        var data=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13318/"+name+"?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC","root","order-it-root-only");
        Flyway.configure().dataSource(data).load().migrate();
        return new Database(name,data,new JdbcTemplate(data));
    }
    private Database source() {
        var db=database();var j=db.jdbc();
        j.execute("create table app_users(id bigint primary key,login_name varchar(100),display_name varchar(255),status varchar(20),password_hash varchar(255))");
        j.update("insert into app_users values(11,'migration-owner','Test owner','ACTIVE','NEVER_COPY_THIS_HASH')");
        j.update("insert into user_display_projections values(11,'migration-owner','Test owner','ACTIVE',0)");
        j.update("insert into sales_channels(id,code,name,enabled) values(21,'TEST','Test',true)");
        j.update("insert into sales_stores(id,channel_id,external_store_id,code,name,status) values(31,21,'store','TEST','Test','ACTIVE')");
        j.update("insert into sales_orders(id,order_no,store_id,external_order_id,external_status,version,responsible_user_id) values(41,'TEST-ORDER',31,'original','NEW',4,11)");
        j.update("insert into sales_order_items(id,order_id,external_item_id,product_name,quantity,fulfillment_status,version) values(51,41,'item','Test product',2,'NORMAL',3)");
        j.update("insert into order_procurement_requests(id,order_item_id,requested_quantity,status,requested_by,requested_at,version) values(61,51,2,'CONVERTED',11,now(6),1)");
        return db;
    }
    private void migrate(Database source,Database target) {
        var service=new OrderDataMigrationService(target.jdbc(),new OrderOutbox(target.jdbc(),new ObjectMapper()));
        new TransactionTemplate(new DataSourceTransactionManager(target.data())).executeWithoutResult(status->{
            try {service.migrate(source.data());} catch(java.sql.SQLException e){throw new IllegalStateException(e);}
        });
    }
    @Test void preservesIdsVersionsRelationsOutboxAndRejectsRepeatedMigration() {
        var source=source();var target=database();migrate(source,target);
        assertThat(target.jdbc().queryForObject("select version from sales_orders where id=41",Long.class)).isEqualTo(4);
        assertThat(target.jdbc().queryForObject("select order_id from sales_order_items where id=51",Long.class)).isEqualTo(41);
        assertThat(target.jdbc().queryForObject("select status from order_procurement_requests where id=61",String.class)).isEqualTo("CONVERTED");
        assertThat(target.jdbc().queryForObject("select count(*) from outbox_events",Long.class)).isEqualTo(2);
        assertThat(target.jdbc().queryForObject("select count(*) from information_schema.columns where table_schema=? and table_name='user_display_projections' and column_name='password_hash'",Long.class,target.name())).isZero();
        assertThatThrownBy(()->migrate(source,target)).isInstanceOf(IllegalStateException.class);
        assertThat(target.jdbc().queryForObject("select count(*) from sales_orders",Long.class)).isEqualTo(1);
    }
    @Test void midCopyFailureRollsBackAllOwnedRowsDirectoryAndOutbox() {
        var source=source();var target=database();
        target.jdbc().execute("create trigger migration_failure before insert on sales_order_items for each row signal sqlstate '45000' set message_text='test-only migration failure'");
        assertThatThrownBy(()->migrate(source,target)).isInstanceOf(RuntimeException.class).hasStackTraceContaining("test-only migration failure");
        for(String table:java.util.List.of("user_display_projections","sales_channels","sales_stores","sales_orders","sales_order_items","outbox_events"))
            assertThat(target.jdbc().queryForObject("select count(*) from "+table,Long.class)).as(table).isZero();
        assertThat(source.jdbc().queryForObject("select version from sales_orders where id=41",Long.class)).isEqualTo(4);
    }
    @Test void realDumpRestoresPreMigrationSourceIntoIsolatedRecoveryDatabase() throws Exception {
        var source=source();var recovery=database();
        var dump=new ProcessBuilder("docker","exec","-e","MYSQL_PWD=order-it-root-only","erp-order-mysql-it","mysqldump","-uroot","--single-transaction","--no-tablespaces","--set-gtid-purged=OFF",source.name()).start();
        byte[] backup=dump.getInputStream().readAllBytes();assertThat(dump.waitFor()).isZero();assertThat(backup.length).isGreaterThan(100);
        var target=database();migrate(source,target);
        var restore=new ProcessBuilder("docker","exec","-i","-e","MYSQL_PWD=order-it-root-only","erp-order-mysql-it","mysql","-uroot",recovery.name()).start();
        try(var input=restore.getOutputStream()){input.write(backup);}
        assertThat(restore.waitFor()).isZero();
        assertThat(recovery.jdbc().queryForObject("select version from sales_orders where id=41",Long.class)).isEqualTo(4);
        assertThat(recovery.jdbc().queryForObject("select requested_quantity from order_procurement_requests where id=61",Integer.class)).isEqualTo(2);
        assertThat(recovery.jdbc().queryForObject("select count(*) from outbox_events",Long.class)).isZero();
    }
}
