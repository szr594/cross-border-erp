package com.szr.crosserp.integration;

import java.nio.file.*;
import java.util.*;
import java.time.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** Actual procurement and backend JVMs with independent MySQL schemas and RabbitMQ. */
class ProcurementMessagingIT {
 @Test void purchaseSnapshotAndReceivingOutboxCrossRealBrokerIdempotently() throws Exception {
  ProjectionFulfillmentIT.root=Path.of("..").toAbsolutePath().normalize();
  var root=ProjectionFulfillmentIT.root;
  String suffix=UUID.randomUUID().toString().replace("-","");
  var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13318/mysql?allowPublicKeyRetrieval=true&useSSL=false","root","order-it-root-only"));
  String b="msg_backend_"+suffix,p="msg_purchase_"+suffix;admin.execute("create database "+b);admin.execute("create database "+p);
  String base="jdbc:mysql://127.0.0.1:13318/",args="?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC";
  var backend=new JdbcTemplate(new DriverManagerDataSource(base+b+args,"root","order-it-root-only"));
  var purchase=new JdbcTemplate(new DriverManagerDataSource(base+p+args,"root","order-it-root-only"));
  var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var keys=generator.generateKeyPair();
  var pub=Files.createTempFile("proc-msg-",".pem");Files.writeString(pub,"-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(keys.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n");
  ProjectionFulfillmentIT.management("PUT","/api/vhosts/"+ProjectionFulfillmentIT.VHOST,"{}");
  ProjectionFulfillmentIT.management("PUT","/api/permissions/"+ProjectionFulfillmentIT.VHOST+"/events_it","{\"configure\":\".*\",\"write\":\".*\",\"read\":\".*\"}");
  var bb=ProjectionFulfillmentIT.builder(root.resolve("backend/target/cross-border-erp-0.1.0-SNAPSHOT.jar"),"proc-msg-backend-"+suffix);
  bb.environment().putAll(Map.of("SPRING_DATASOURCE_URL",base+b+args,"SPRING_DATASOURCE_USERNAME","root","SPRING_DATASOURCE_PASSWORD","order-it-root-only","BACKEND_PORT",""+ProjectionFulfillmentIT.port(),"APP_AUTH_JWT_SECRET","messaging-only-secret-at-least-thirty-two-bytes","APP_EVENTS_ENABLED","true"));
  var pb=ProjectionFulfillmentIT.builder(root.resolve("services/procurement-service/target/procurement-service-0.1.0-SNAPSHOT.jar"),"proc-msg-purchase-"+suffix);
  pb.environment().putAll(Map.of("PROCUREMENT_DATABASE_URL",base+p+args,"PROCUREMENT_DATABASE_USER","root","PROCUREMENT_DATABASE_PASSWORD","order-it-root-only","PROCUREMENT_PORT",""+ProjectionFulfillmentIT.port(),"IDENTITY_PUBLIC_KEY",pub.toUri().toString(),"APP_EVENTS_ENABLED","true"));
  Process backendProcess=bb.start(),purchaseProcess=pb.start();
  try {
   await(()->exists(backend,"receiving_outbox")&&exists(purchase,"procurement_outbox"));
   backend.update("insert into order_business_projections(order_id,order_no,external_status,fulfillment_exception,source_version,updated_at,authority) values(1,'MSG','NEW',false,1,CURRENT_TIMESTAMP(6),'ORDER')");
   backend.update("insert into order_item_business_projections(order_item_id,order_id,product_name,quantity,fulfillment_status,source_version) values(1,1,'Test',2,'NORMAL',1)");
   backend.update("insert into procurement_request_projections values(1,1,2,'CONVERTED',1,CURRENT_TIMESTAMP(6))");
   purchase.update("insert into user_display_projections values(1,'test','Test','ACTIVE',1)");
   purchase.update("insert into suppliers(id,code,name,platform,status) values(1,'TEST','Test','MANUAL','ACTIVE')");
   purchase.update("insert into purchase_orders(id,purchase_order_no,supplier_id,status,currency,created_by) values(71,'MSG-PO',1,'ORDERED','CNY',1)");
   purchase.update("insert into purchase_order_items(id,purchase_order_id,product_name,purchased_quantity,currency) values(81,71,'Test',2,'CNY')");
   var snapshot=Map.of("eventId",UUID.randomUUID().toString(),"eventType","procurement.snapshot.v1","schemaVersion",1,"aggregateId",71,"aggregateVersion",1,"payload",Map.of("purchaseOrderId",71,"purchaseOrderNo","MSG-PO","supplierId",1,"supplierCode","TEST","supplierName","Test","status","ORDERED","currency","CNY","items",List.of(Map.of("purchaseOrderItemId",81,"productName","Test","purchasedQuantity",2,"allocations",List.of(Map.of("allocationId",91,"requestId",1,"orderItemId",1,"quantity",2))))));
   String eventId=(String)snapshot.get("eventId"),json=ProjectionFulfillmentIT.JSON.writeValueAsString(snapshot);
   broker("stop");
   purchase.update("insert into procurement_outbox(event_id,event_type,aggregate_id,aggregate_version,payload,occurred_at) values(?,'procurement.snapshot.v1',71,1,?,CURRENT_TIMESTAMP(6))",eventId,json);
   Thread.sleep(2000);
   assertThat(purchase.queryForObject("select count(*) from procurement_outbox where published_at is null",Integer.class)).isEqualTo(1);
   assertThat(backend.queryForObject("select count(*) from purchase_item_projections where id=81",Integer.class)).isZero();
   purchaseProcess.destroyForcibly();assertThat(purchaseProcess.waitFor(10,TimeUnit.SECONDS)).isTrue();
   purchaseProcess=pb.start();
   broker("start");
   await(()->backend.queryForObject("select count(*) from purchase_item_projections where id=81",Integer.class)==1);
   assertThat(backend.queryForObject("select count(*) from purchase_orders",Integer.class)).isZero();
   assertThat(backend.queryForObject("select count(*) from purchase_allocation_projections where id=91",Integer.class)).isEqualTo(1);
   var facts=Map.of("eventId",UUID.randomUUID().toString(),"eventType","receiving.snapshot.v1","schemaVersion",1,"aggregateId",71,"aggregateVersion",1,"payload",Map.of("items",List.of(Map.of("purchaseOrderItemId",81,"sourceVersion",1,"acceptedQuantity",1,"inspectedQuantity",2))));
   String factId=(String)facts.get("eventId"),factJson=ProjectionFulfillmentIT.JSON.writeValueAsString(facts);
   broker("stop");
   backend.update("insert into receiving_outbox(event_id,event_type,aggregate_id,aggregate_version,payload,occurred_at) values(?,'receiving.snapshot.v1',71,1,?,CURRENT_TIMESTAMP(6))",factId,factJson);
   Thread.sleep(2000);
   assertThat(backend.queryForObject("select count(*) from receiving_outbox where published_at is null",Integer.class)).isEqualTo(1);
   assertThat(purchase.queryForObject("select received_quantity from purchase_order_items where id=81",Integer.class)).isZero();
   backendProcess.destroyForcibly();assertThat(backendProcess.waitFor(10,TimeUnit.SECONDS)).isTrue();
   backendProcess=bb.start();
   broker("start");
   await(()->"RECEIVED".equals(purchase.queryForObject("select status from purchase_orders where id=71",String.class)));
   assertThat(purchase.queryForObject("select received_quantity from purchase_order_items where id=81",Integer.class)).isEqualTo(1);
   // Republish both durable rows after recovery; committed effects must remain unique.
   purchase.update("update procurement_outbox set published_at=null where event_id=?",eventId);
   backend.update("update receiving_outbox set published_at=null where event_id=?",factId);
   await(()->purchase.queryForObject("select count(*) from procurement_outbox where published_at is null",Integer.class)==0&&backend.queryForObject("select count(*) from receiving_outbox where published_at is null",Integer.class)==0);
   assertThat(backend.queryForObject("select count(*) from procurement_event_inbox where event_id=?",Integer.class,eventId)).isEqualTo(1);
   assertThat(purchase.queryForObject("select count(*) from procurement_event_inbox where event_id=?",Integer.class,factId)).isEqualTo(1);
   assertThat(purchase.queryForObject("select received_quantity from purchase_order_items where id=81",Integer.class)).isEqualTo(1);
  } finally {broker("start");backendProcess.destroyForcibly();purchaseProcess.destroyForcibly();backendProcess.waitFor(10,TimeUnit.SECONDS);purchaseProcess.waitFor(10,TimeUnit.SECONDS);Files.deleteIfExists(pub);}
 }
 static void broker(String action)throws Exception {
  var process=new ProcessBuilder("docker",action,"erp-events-it-rabbit").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
  assertThat(process.waitFor(60,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();
 }
 static boolean exists(JdbcTemplate jdbc,String table){try{jdbc.queryForObject("select count(*) from "+table,Integer.class);return true;}catch(Exception e){return false;}}
 static void await(java.util.function.BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);while(System.nanoTime()<end){if(condition.getAsBoolean())return;Thread.sleep(200);}assertThat(condition.getAsBoolean()).as("real broker chain within 90 seconds; inspect tmp/proc-msg logs").isTrue();}
}
