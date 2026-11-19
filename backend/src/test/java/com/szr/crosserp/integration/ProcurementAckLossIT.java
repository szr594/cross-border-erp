package com.szr.crosserp.integration;
import com.szr.crosserp.integration.procurement.ProcurementSnapshotConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(properties={"spring.profiles.active=prod","spring.datasource.username=root","spring.datasource.password=order-it-root-only","app.auth.jwt-secret=ack-test-only-secret-at-least-thirty-two-bytes","app.events.enabled=true","spring.rabbitmq.port=15673","spring.rabbitmq.username=events_it","spring.rabbitmq.password=events-it-only"})
class ProcurementAckLossIT {
 @DynamicPropertySource static void configure(DynamicPropertyRegistry r)throws Exception{
  String name="ack_purchase_"+UUID.randomUUID().toString().replace("-","");
  var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13318/mysql?allowPublicKeyRetrieval=true&useSSL=false","root","order-it-root-only"));admin.execute("create database "+name);
  r.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:13318/"+name+"?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC");
  ProjectionFulfillmentIT.management("PUT","/api/vhosts/"+name,"{}");
  ProjectionFulfillmentIT.management("PUT","/api/permissions/"+name+"/events_it","{\"configure\":\".*\",\"write\":\".*\",\"read\":\".*\"}");
  r.add("spring.rabbitmq.virtual-host",()->name);
 }
 @Autowired JdbcTemplate jdbc; @Autowired RabbitTemplate rabbit; @Autowired ObjectMapper json;
 @Autowired RabbitListenerEndpointRegistry listeners; @SpyBean ProcurementSnapshotConsumer consumer;
 @Test void commitBeforeChannelAbortIsRedeliveredWithoutDuplicateEffects()throws Exception{
  jdbc.update("insert into order_business_projections(order_id,order_no,external_status,fulfillment_exception,source_version,updated_at,authority) values(1,'ACK','NEW',false,1,CURRENT_TIMESTAMP(6),'ORDER')");
  jdbc.update("insert into order_item_business_projections(order_item_id,order_id,product_name,quantity,fulfillment_status,source_version) values(1,1,'Test',2,'NORMAL',1)");
  jdbc.update("insert into procurement_request_projections values(1,1,2,'CONVERTED',1,CURRENT_TIMESTAMP(6))");
  String id=UUID.randomUUID().toString();
  byte[] body=json.writeValueAsBytes(Map.of("eventId",id,"eventType","procurement.snapshot.v1","schemaVersion",1,"aggregateId",71,"aggregateVersion",1,"payload",Map.of("purchaseOrderId",71,"purchaseOrderNo","ACK-PO","supplierId",1,"supplierCode","TEST","supplierName","Test","status","ORDERED","currency","CNY","items",List.of(Map.of("purchaseOrderItemId",81,"productName","Test","purchasedQuantity",2,"allocations",List.of(Map.of("allocationId",91,"requestId",1,"orderItemId",1,"quantity",2)))))));
  listeners.stop();
  var connection=rabbit.getConnectionFactory().createConnection();var channel=connection.createChannel(false);
  try{
   var properties=new org.springframework.amqp.core.MessageProperties();properties.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
   rabbit.send("cross-erp.events","procurement.snapshot.v1",new org.springframework.amqp.core.Message(body,properties));
   var delivery=new java.util.concurrent.atomic.AtomicReference<com.rabbitmq.client.GetResponse>();
   waitUntil(()->{try{delivery.set(channel.basicGet("legacy.procurement-sync",false));return delivery.get()!=null;}catch(Exception e){throw new RuntimeException(e);}});
   assertThat(delivery.get().getBody()).isEqualTo(body);
   consumer.accept(body); // Transaction has committed; deliberately do not basicAck.
   assertThat(jdbc.queryForObject("select count(*) from procurement_event_inbox where event_id=?",Integer.class,id)).isEqualTo(1);
   if(channel instanceof org.springframework.amqp.rabbit.connection.ChannelProxy proxy)proxy.getTargetChannel().abort();else channel.abort();
   listeners.start();
   waitUntil(()->{try{verify(consumer,atLeast(2)).accept(org.mockito.ArgumentMatchers.argThat(bytes->Arrays.equals(bytes,body)));return true;}catch(Throwable e){return false;}});
   assertThat(jdbc.queryForObject("select count(*) from procurement_event_inbox where event_id=?",Integer.class,id)).isEqualTo(1);
   assertThat(jdbc.queryForObject("select count(*) from purchase_item_projections where id=81",Integer.class)).isEqualTo(1);
   assertThat(jdbc.queryForObject("select allocated_quantity from purchase_allocation_projections where id=91",Integer.class)).isEqualTo(2);
  }finally{connection.close();listeners.start();}
 }
 static void waitUntil(java.util.function.BooleanSupplier condition)throws Exception{long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);while(System.nanoTime()<end){if(condition.getAsBoolean())return;Thread.sleep(100);}assertThat(condition.getAsBoolean()).isTrue();}
}
