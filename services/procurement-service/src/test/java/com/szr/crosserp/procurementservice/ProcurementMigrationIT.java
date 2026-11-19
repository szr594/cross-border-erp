package com.szr.crosserp.procurementservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.security.interfaces.*;
import java.util.Base64;
import java.time.Instant;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${PROCUREMENT_TEST_DATABASE_URL:jdbc:mysql://127.0.0.1:13318/ms09_migration_target?allowPublicKeyRetrieval=true&useSSL=false}",
    "spring.datasource.username=${PROCUREMENT_TEST_DATABASE_USER:root}","spring.datasource.password=${PROCUREMENT_TEST_DATABASE_PASSWORD:order-it-root-only}"})
@AutoConfigureMockMvc
class ProcurementMigrationIT {
    static java.security.KeyPair pair;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry) throws Exception {
        String database="migration_target_"+java.util.UUID.randomUUID().toString().replace("-","");
        var admin=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:mysql://127.0.0.1:13318/mysql?allowPublicKeyRetrieval=true&useSSL=false","root","order-it-root-only"));
        admin.execute("create database "+database);
        registry.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:13318/"+database+"?allowPublicKeyRetrieval=true&useSSL=false");

        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);pair=generator.generateKeyPair();
        var file=Files.createTempFile("order-public-",".pem");
        Files.writeString(file,"-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{10})
            .encodeToString(pair.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n");file.toFile().deleteOnExit();
        registry.add("app.auth.public-key",()->file.toUri().toString());
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired com.szr.crosserp.procurementservice.migration.ProcurementRollbackService rollback;
    @Autowired com.szr.crosserp.procurementservice.migration.ProcurementDataMigrationService migration;
    @Test void historicalCopyRollsBackOnLateCountMismatchAndPreservesIdsOnRetry()throws Exception{
        String sourceName="migration_source_"+java.util.UUID.randomUUID().toString().replace("-","");
        var sourceDs=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:mysql://127.0.0.1:13318/"+sourceName+"?allowPublicKeyRetrieval=true&useSSL=false","root","order-it-root-only");
        jdbc.execute("create database "+sourceName);
        String target=jdbc.queryForObject("select database()",String.class);
        var tables=java.util.List.of("suppliers","purchase_orders","purchase_order_items","purchase_order_allocations","purchase_order_events","purchase_create_operations");
        for(String table:tables)jdbc.execute("create table "+sourceName+"."+table+" like "+target+"."+table);
        var source=new JdbcTemplate(sourceDs);
        source.execute("create table receiving_outbox(published_at datetime(6))");
        source.execute("create table purchase_business_projections(id bigint primary key,purchase_order_no varchar(64),supplier_id bigint,authority varchar(16),status varchar(32),bridge_confirmed boolean,version bigint default 0)");
        source.execute("create table goods_receipt_items(purchase_order_item_id bigint,accepted_quantity int,damaged_quantity int)");
        jdbc.update("insert into user_display_projections values(1,'migration-user','Test','ACTIVE',1)");
        jdbc.update("insert into procurement_order_projections values(1,'TEST','1:TEST','NEW',false,1)");
        jdbc.update("insert into procurement_item_projections(order_item_id,order_id,product_name,quantity,fulfillment_status,source_version) values(1,1,'Test',2,'NORMAL',1)");
        jdbc.update("insert into procurement_request_projections values(1,1,2,'CONVERTED',1)");
        source.update("insert into suppliers(id,code,name,platform,status) values(41,'MIG','Test','MANUAL','ACTIVE')");
        source.update("insert into purchase_orders(id,purchase_order_no,supplier_id,status,currency,created_by) values(71,'MIG-71',41,'PARTIALLY_RECEIVED','CNY',1)");
        source.update("insert into purchase_order_items(id,purchase_order_id,product_name,purchased_quantity,received_quantity,currency) values(81,71,'Test',2,1,'CNY')");
        source.update("insert into purchase_order_allocations(id,purchase_order_item_id,procurement_request_id,allocated_quantity) values(91,81,1,2)");
        // Failure occurs after all six tables have been copied into the target transaction.
        source.update("insert into goods_receipt_items values(81,0,1)");
        assertThatThrownBy(()->migration.migrate(sourceDs)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Historical receiving totals mismatch");
        for(String table:tables)assertThat(jdbc.queryForObject("select count(*) from "+table,Long.class)).as(table).isZero();
        assertThat(jdbc.queryForObject("select count(*) from procurement_outbox",Long.class)).isZero();
        source.update("update goods_receipt_items set accepted_quantity=1");
        var counts=migration.migrate(sourceDs);
        assertThat(counts.get("purchase_orders")).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select supplier_id from purchase_orders where id=71",Long.class)).isEqualTo(41);
        assertThat(jdbc.queryForObject("select purchase_order_item_id from purchase_order_allocations where id=91",Long.class)).isEqualTo(81);
        assertThat(jdbc.queryForObject("select accepted_quantity from procurement_receiving_projections where purchase_order_item_id=81",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select inspected_quantity from procurement_receiving_projections where purchase_order_item_id=81",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from procurement_outbox",Long.class)).isEqualTo(1);
        assertThat(source.queryForObject("select received_quantity from purchase_order_items where id=81",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->migration.migrate(sourceDs)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Target must be empty");
        assertThatThrownBy(()->rollback.restore(sourceDs)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Drain procurement outbox");
        jdbc.update("update procurement_outbox set published_at=CURRENT_TIMESTAMP(6)");
        jdbc.update("insert into suppliers(id,code,name,platform,status) values(42,'AFTER-CUTOVER','New supplier','MANUAL','ACTIVE')");
        jdbc.update("insert into purchase_order_events(id,purchase_order_id,event_type,operator_id,occurred_at) values(99,71,'RECEIVING_SYNCED',1,CURRENT_TIMESTAMP(6))");
        jdbc.update("insert into procurement_order_projections values(2,'NEW','1:NEW','NEW',false,1)");
        jdbc.update("insert into procurement_item_projections(order_item_id,order_id,product_name,quantity,fulfillment_status,source_version) values(2,2,'New product',2,'NORMAL',1)");
        jdbc.update("insert into procurement_request_projections values(2,2,2,'CONVERTED',1)");
        jdbc.update("insert into purchase_orders(id,purchase_order_no,supplier_id,status,currency,created_by) values(72,'POST-CUTOVER-72',42,'DRAFT','CNY',1)");
        jdbc.update("insert into purchase_order_items(id,purchase_order_id,product_name,purchased_quantity,currency) values(82,72,'New product',2,'CNY')");
        jdbc.update("insert into purchase_order_allocations(id,purchase_order_item_id,procurement_request_id,allocated_quantity) values(92,82,2,2)");
        source.update("insert into purchase_business_projections(id,purchase_order_no,supplier_id,authority,status) values(71,'MIG-71',41,'PROCUREMENT','PARTIALLY_RECEIVED'),(72,'POST-CUTOVER-72',42,'PROCUREMENT','DRAFT')");
        source.update("update purchase_order_allocations set allocated_quantity=3 where id=91");
        assertThatThrownBy(()->rollback.restore(sourceDs)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Rollback identity conflict");
        assertThat(source.queryForObject("select count(*) from suppliers where id=42",Integer.class)).isZero();
        source.update("update purchase_order_allocations set allocated_quantity=2 where id=91");
        assertThat(source.queryForObject("select authority from purchase_business_projections where id=71",String.class)).isEqualTo("PROCUREMENT");
        var restored=rollback.restore(sourceDs);
        assertThat(source.queryForObject("select count(*) from purchase_business_projections where authority='LEGACY'",Integer.class)).isEqualTo(2);
        assertThat(restored.get("suppliers")).isEqualTo(2L);
        assertThat(source.queryForObject("select purchase_order_no from purchase_orders where id=72",String.class)).isEqualTo("POST-CUTOVER-72");
        assertThat(source.queryForObject("select purchase_order_item_id from purchase_order_allocations where id=92",Long.class)).isEqualTo(82L);
        assertThat(source.queryForObject("select code from suppliers where id=42",String.class)).isEqualTo("AFTER-CUTOVER");
        assertThat(source.queryForObject("select event_type from purchase_order_events where id=99",String.class)).isEqualTo("RECEIVING_SYNCED");
        assertThat(rollback.restore(sourceDs)).isEqualTo(restored);

    }
}
