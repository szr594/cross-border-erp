package com.szr.crosserp.orderservice;

import com.szr.crosserp.orderservice.fulfillment.*;
import com.szr.crosserp.orderservice.procurement.*;
import com.szr.crosserp.orderservice.order.dto.*;
import com.szr.crosserp.orderservice.common.exception.ApiException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:execution_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","ORDER_DATABASE_URL=jdbc:h2:mem:execution_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","ORDER_DATABASE_USER=sa","ORDER_DATABASE_PASSWORD=","app.orders.cancel-recovery-delay-ms=600000"})
class ExecutionAuthorityTest {
    @Autowired JdbcTemplate jdbc;@Autowired ExecutionAuthorityService execution;@Autowired OrderCancellationService cancellations;
    @Autowired ProcurementRequestService requests;@Autowired ProcurementClaimService claims;
    static final AtomicLong IDS=new AtomicLong(10000);
    static final java.util.concurrent.atomic.AtomicInteger reply=new java.util.concurrent.atomic.AtomicInteger(200);
    static final com.sun.net.httpserver.HttpServer server=server();
    long id;
    static com.sun.net.httpserver.HttpServer server(){try{var value=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);value.createContext("/internal/orders/",exchange->{byte[] bytes=(reply.get()==409?"{\"code\":\"ORDER_CANCEL_BLOCKED_BY_CONSUMED_INVENTORY\"}":"{\"status\":\"RELEASED\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(reply.get(),bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});value.start();return value;}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry registry) throws Exception {
        var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var key=generator.generateKeyPair().getPublic();
        var file=java.nio.file.Files.createTempFile("execution-public-",".pem");java.nio.file.Files.writeString(file,"-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(key.getEncoded())+"\n-----END PUBLIC KEY-----\n");file.toFile().deleteOnExit();
        registry.add("app.auth.public-key",()->file.toUri().toString());registry.add("LEGACY_BACKEND_INTERNAL_URL",()->"http://127.0.0.1:"+server.getAddress().getPort());registry.add("ORDER_INTERNAL_SERVICE_TOKEN",()->"execution-test-only-credential-at-least-32-bytes");
    }
    @AfterAll static void stop(){server.stop(0);}
    @BeforeEach void seed(){
        id=IDS.incrementAndGet();reply.set(200);
        jdbc.update("insert into user_display_projections(id,login_name,display_name,status,source_version) values(?,?,?,'ACTIVE',0)",id,"u"+id,"Test owner");
        jdbc.update("insert into sales_channels(id,code,name,enabled) values(?,?,?,true)",id,"c"+id,"Channel");
        jdbc.update("insert into sales_stores(id,channel_id,external_store_id,code,name,status) values(?,?,?,?,?,'ACTIVE')",id,id,"ext"+id,"s"+id,"Store");
        jdbc.update("insert into sales_orders(id,order_no,store_id,external_order_id,external_status,fulfillment_exception,currency,version,created_at,updated_at) values(?,?,?,?,'NEW',false,'USD',0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",id,"O"+id,id,"ext"+id);
        jdbc.update("insert into sales_order_items(id,order_id,external_item_id,product_name,quantity,currency,fulfillment_status,version,created_at,updated_at) values(?,?,?,'Product',2,'USD','NORMAL',0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",id,id,"item"+id);
    }
    @Test void activeExecutionPreventsCancelAndReleaseTombstonePreventsLateAcquire(){
        String operation=UUID.randomUUID().toString();execution.acquire(id,operation);
        assertThatThrownBy(()->cancellations.cancel(id,new ReasonVersionRequest("cancel",0),"cancel-"+id,Long.toString(id),List.of("OWNER"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("ORDER_CANCEL_PENDING"));
        execution.release(id,operation);execution.release(id,operation);
        assertThatThrownBy(()->execution.acquire(id,operation)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("ORDER_EXECUTION_RELEASED"));
        assertThat(cancellations.cancel(id,new ReasonVersionRequest("cancel",0),"cancel-"+id,Long.toString(id),List.of("OWNER")).externalStatus().name()).isEqualTo("CANCELED");
    }
    @Test void timeoutRetainsCancellationBarrierAndSameKeyCompletesExactlyOnce(){
        reply.set(503);String key="cancel-"+id;var input=new ReasonVersionRequest("cancel",0);
        assertThatThrownBy(()->cancellations.cancel(id,input,key,Long.toString(id),List.of("OWNER"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getStatus().value()).isEqualTo(503));
        assertThatThrownBy(()->execution.acquire(id,UUID.randomUUID().toString())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("ORDER_EXECUTION_FORBIDDEN"));
        assertThat(jdbc.queryForObject("select external_status from sales_orders where id=?",String.class,id)).isEqualTo("NEW");
        reply.set(200);var result=cancellations.cancel(id,input,key,Long.toString(id),List.of("OWNER"));
        assertThat(cancellations.cancel(id,input,key,Long.toString(id),List.of("OWNER"))).isEqualTo(result);
        assertThat(result.version()).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='order.updated.v2' and aggregate_id=?",Long.class,id)).isEqualTo(1);
    }
    @Test void claimedDemandCannotBeWithdrawnAndConfirmPublishesOnce(){
        var created=requests.create(id,new CreateProcurementRequestsRequest(List.of(new ProcurementRequestItemCommand(id,2)),null,0),Long.toString(id),List.of("OWNER"));long request=created.requests().get(0).id();
        String key="claim-"+id;claims.claim(new ProcurementClaimService.Command(key,List.of(new ProcurementClaimService.Item(request,2,0))));
        assertThatThrownBy(()->requests.withdraw(id,request,new ReasonVersionRequest("withdraw",0),Long.toString(id),List.of("OWNER"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("PROCUREMENT_REQUEST_CLAIMED"));
        claims.confirm(key,"PO"+id);claims.confirm(key,"PO"+id);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='order.procurement.snapshot.v1' and aggregate_id=?",Long.class,request)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select version from order_procurement_requests where id=?",Long.class,request)).isEqualTo(1);
    }
    @Test void consumedInventoryFailureDoesNotCancelOrder(){
        reply.set(409);
        assertThatThrownBy(()->cancellations.cancel(id,new ReasonVersionRequest("cancel",0),"cancel-"+id,Long.toString(id),List.of("OWNER"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("ORDER_CANCEL_BLOCKED_BY_CONSUMED_INVENTORY"));
        assertThat(jdbc.queryForObject("select external_status from sales_orders where id=?",String.class,id)).isEqualTo("NEW");
        assertThat(execution.acquire(id,UUID.randomUUID().toString()).status()).isEqualTo("ACTIVE");
    }
}
