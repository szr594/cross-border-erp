package com.szr.crosserp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.szr.crosserp.integration.orders.OrderEventConsumer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class ProcurementProjectionTest {
    @Autowired JdbcTemplate jdbc;@Autowired OrderEventConsumer consumer;@Autowired ObjectMapper mapper;
    @Autowired com.szr.crosserp.integration.orders.LegacyProjectionListener legacy;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    static final AtomicLong IDS=new AtomicLong(9000000000L);
    @Test void independentDemandVersionPreventsDuplicateAndOutOfOrderRegression() throws Exception {
        long id=IDS.incrementAndGet();byte[] latest=event(id,2,"CONVERTED",id,id);
        consumer.accept(latest);consumer.accept(latest);consumer.accept(event(id,1,"REQUESTED",id,id));
        assertThat(jdbc.queryForObject("select status from procurement_request_projections where request_id=?",String.class,id)).isEqualTo("CONVERTED");
        assertThat(jdbc.queryForObject("select source_version from procurement_request_projections where request_id=?",Long.class,id)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select source_version from order_business_projections where order_id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("select authority from order_business_projections where order_id=?",String.class,id)).isEqualTo("ORDER");
        assertThat(jdbc.queryForObject("select count(*) from sales_orders where id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from order_procurement_requests where id=?",Long.class,id)).isZero();
    }
    @Test void changingDemandOwnerRollsBackInboxAndNewOrderProjection() throws Exception {
        long id=IDS.incrementAndGet();consumer.accept(event(id,0,"REQUESTED",id,id));
        byte[] conflict=event(id,1,"CONVERTED",id+100,id+100);
        String eventId=mapper.readTree(conflict).path("eventId").asText();
        assertThatThrownBy(()->consumer.accept(conflict)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from order_event_inbox where event_id=?",Long.class,eventId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from order_business_projections where order_id=?",Long.class,id+100)).isZero();
        assertThat(jdbc.queryForObject("select status from procurement_request_projections where request_id=?",String.class,id)).isEqualTo("REQUESTED");
    }
    @Test void legacyIdCollisionCannotBeSilentlyOverwritten() throws Exception {
        long id=IDS.incrementAndGet();
        jdbc.update("insert into order_business_projections(order_id,order_no,external_status,fulfillment_exception,source_version,updated_at,authority,identity_key) values(?,'LEGACY','NEW',false,0,CURRENT_TIMESTAMP(6),'LEGACY','other-source')",id);
        byte[] event=event(id,0,"REQUESTED",id,id);String eventId=mapper.readTree(event).path("eventId").asText();
        assertThatThrownBy(()->consumer.accept(event)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("identity conflict");
        assertThat(jdbc.queryForObject("select authority from order_business_projections where order_id=?",String.class,id)).isEqualTo("LEGACY");
        assertThat(jdbc.queryForObject("select count(*) from order_event_inbox where event_id=?",Long.class,eventId)).isZero();
    }
    @Test void matchingIdentityTransfersAuthorityAndFencesLegacyWrites() throws Exception {
        long id=IDS.incrementAndGet();
        jdbc.update("insert into order_business_projections(order_id,order_no,external_status,fulfillment_exception,source_version,updated_at,authority,identity_key) values(?,?,'NEW',false,0,CURRENT_TIMESTAMP(6),'LEGACY',?)",id,"P-"+id,"store:"+id);
        consumer.accept(event(id,0,"REQUESTED",id,id));
        assertThat(jdbc.queryForObject("select authority from order_business_projections where order_id=?",String.class,id)).isEqualTo("ORDER");
        assertThatThrownBy(()->legacy.refreshOrder(id)).isInstanceOf(IllegalStateException.class).hasMessageContaining("authority");
        assertThat(jdbc.queryForObject("select order_no from order_business_projections where order_id=?",String.class,id)).isEqualTo("P-"+id);
    }
    @Test void legacyMessageCannotPersistUnexpectedPrivateFields() throws Exception {
        long id=IDS.incrementAndGet();
        var message=Map.of("eventId",UUID.randomUUID().toString(),"eventType","order.created.v1","schemaVersion",1,"aggregateId",id,"aggregateVersion",0,"payload",Map.of("orderId",id,"items",List.of(Map.of("orderItemId",id,"quantity",1,"recipientPhone","private-field-must-not-persist"))));
        consumer.accept(mapper.writeValueAsBytes(message));
        assertThat(jdbc.queryForObject("select items_payload from order_event_projections where order_id=?",String.class,id)).doesNotContain("recipientPhone","private-field-must-not-persist");
    }
    @Test void executionCannotUseAProjectionBeforeAuthorityHandoff(){
        long id=IDS.incrementAndGet();
        jdbc.update("insert into order_business_projections(order_id,order_no,external_status,fulfillment_exception,source_version,updated_at,authority) values(?,'LEGACY','NEW',false,0,CURRENT_TIMESTAMP(6),'LEGACY')",id);
        var remote=new org.springframework.beans.factory.support.DefaultListableBeanFactory().getBeanProvider(com.szr.crosserp.integration.orders.RemoteExecutionAuthority.class);
        var gate=new com.szr.crosserp.integration.orders.OrderExecutionGate(jdbc,transactions,mapper,remote,true);
        var effects=new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(()->gate.execute(List.of(id),effects::incrementAndGet)).isInstanceOfSatisfying(com.szr.crosserp.common.exception.ApiException.class,e->assertThat(e.getCode()).isEqualTo("ORDER_PROJECTION_NOT_READY"));
        assertThat(effects.get()).isZero();
    }
    private byte[] event(long request,long version,String status,long order,long item) throws Exception {
        var product=Map.of("orderItemId",item,"quantity",2,"productName","Projection product","externalItemId","EXT-"+item,"externalSku","SKU","variantName","Blue","fulfillmentStatus","NORMAL");
        Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("orderId",order);snapshot.put("orderVersion",0);snapshot.put("orderNo","P-"+order);snapshot.put("externalStatus","NEW");snapshot.put("exception",false);snapshot.put("identityKey","store:"+order);snapshot.put("storeName","Test store");snapshot.put("items",List.of(product));
        var payload=Map.of("requestId",request,"orderItemId",item,"quantity",2,"status",status,"requestedAt","2026-11-17T00:00:00Z","orderSnapshot",snapshot);
        return mapper.writeValueAsBytes(Map.of("eventId",UUID.randomUUID().toString(),"eventType","order.procurement.snapshot.v1","schemaVersion",1,"aggregateId",request,"aggregateVersion",version,"payload",payload));
    }
}
