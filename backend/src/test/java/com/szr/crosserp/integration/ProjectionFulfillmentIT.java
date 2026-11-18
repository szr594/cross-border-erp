package com.szr.crosserp.integration;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.assertj.core.api.Assertions.*;

/** Two real JVMs, independent MySQL schemas and an isolated real RabbitMQ vhost. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectionFulfillmentIT {
    static final ObjectMapper JSON=new ObjectMapper();
    static final HttpClient HTTP=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();
    static final String INTERNAL="projection-it-only-credential-at-least-thirty-two-bytes";
    static final String SUFFIX=UUID.randomUUID().toString().replace("-","");
    static final String VHOST="projection-it-"+SUFFIX;
    static JdbcTemplate source,backend;
    static String sourceUrl,backendUrl,token;
    static int sourcePort,backendPort;
    static Process sourceProcess,backendProcess;
    static Path root,publicKey;
    static ProcessBuilder backendBuilder;
    static CachingConnectionFactory rabbitConnection;
    static RabbitTemplate rabbit;

    @BeforeAll static void start() throws Exception {
        root=Path.of("..").toAbsolutePath().normalize();
        String password=System.getenv().getOrDefault("BRIDGE_TEST_DATABASE_PASSWORD","order-it-root-only");
        var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13318/mysql?allowPublicKeyRetrieval=true&useSSL=false","root",password));
        String a="projection_order_"+SUFFIX,b="projection_backend_"+SUFFIX;
        admin.execute("create database "+a);admin.execute("create database "+b);
        sourceUrl="jdbc:mysql://127.0.0.1:13318/"+a+"?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
        backendUrl="jdbc:mysql://127.0.0.1:13318/"+b+"?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
        source=new JdbcTemplate(new DriverManagerDataSource(sourceUrl,"root",password));backend=new JdbcTemplate(new DriverManagerDataSource(backendUrl,"root",password));
        var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
        publicKey=Files.createTempFile("projection-integration-",".pem");Files.writeString(publicKey,"-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(pair.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n");publicKey.toFile().deleteOnExit();
        var key=new com.nimbusds.jose.jwk.RSAKey.Builder((java.security.interfaces.RSAPublicKey)pair.getPublic()).privateKey((java.security.interfaces.RSAPrivateKey)pair.getPrivate()).build();
        var encoder=new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(key)));
        var claims=org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("cross-border-erp").subject("1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).claim("roles",List.of("OWNER")).build();
        token=encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build(),claims)).getTokenValue();
        management("PUT","/api/vhosts/"+VHOST,"{}");
        management("PUT","/api/permissions/"+VHOST+"/events_it","{\"configure\":\".*\",\"write\":\".*\",\"read\":\".*\"}");
        rabbitConnection=new CachingConnectionFactory("127.0.0.1",15673);rabbitConnection.setUsername("events_it");rabbitConnection.setPassword("events-it-only");rabbitConnection.setVirtualHost(VHOST);rabbit=new RabbitTemplate(rabbitConnection);
        sourcePort=port();backendPort=port();
        var producer=builder(root.resolve("services/order-service/target/order-service-0.1.0-SNAPSHOT.jar"),"projection-order-"+SUFFIX);
        producer.environment().putAll(Map.of("ORDER_DATABASE_URL",sourceUrl,"ORDER_DATABASE_USER","root","ORDER_DATABASE_PASSWORD",password,"ORDER_PORT",Integer.toString(sourcePort),"IDENTITY_PUBLIC_KEY",publicKey.toUri().toString(),"ORDER_INTERNAL_SERVICE_TOKEN",INTERNAL,"LEGACY_BACKEND_INTERNAL_URL",backendBase(),"APP_EVENTS_ENABLED","true"));
        backendBuilder=builder(root.resolve("backend/target/cross-border-erp-0.1.0-SNAPSHOT.jar"),"projection-backend-"+SUFFIX);
        backendBuilder.environment().put("APP_AUTH_LEGACY_ENABLED","false");
        backendBuilder.environment().putAll(Map.of("SPRING_DATASOURCE_URL",backendUrl,"SPRING_DATASOURCE_USERNAME","root","SPRING_DATASOURCE_PASSWORD",password,"BACKEND_PORT",Integer.toString(backendPort),"IDENTITY_PUBLIC_KEY",publicKey.toUri().toString(),"ORDER_INTERNAL_SERVICE_TOKEN",INTERNAL,"ORDER_SERVICE_INTERNAL_URL",sourceBase(),"APP_PROCUREMENT_REMOTE_ENABLED","true","APP_EVENTS_ENABLED","true"));
        sourceProcess=producer.start();backendProcess=backendBuilder.start();
        await(()->ready(sourceBase()+"/api/order-imports/missing"));await(()->ready(backendBase()+"/api/purchase-orders"));
        source.update("insert into user_display_projections(id,login_name,display_name,status,source_version) values(1,'projection-owner','Test owner','ACTIVE',0)");
        source.update("insert into sales_channels(id,code,name,enabled) values(1,'PROJECTION','Channel',true)");
        source.update("insert into sales_stores(id,channel_id,external_store_id,code,name,status) values(1,1,'projection-store','PROJECTION','Projection store','ACTIVE')");
        backend.update("insert into app_users(id,login_name,display_name,password_hash,status,must_change_password) values(1,'projection-owner','Test owner','test-only-unused','ACTIVE',false)");
        backend.update("insert into suppliers(id,code,name,platform,status) values(1,'PROJECTION','Test supplier','MANUAL','ACTIVE')");
    }
    @AfterAll static void stop() throws Exception {
        if(sourceProcess!=null){sourceProcess.destroyForcibly();sourceProcess.waitFor(10,TimeUnit.SECONDS);}
        if(backendProcess!=null){backendProcess.destroyForcibly();backendProcess.waitFor(10,TimeUnit.SECONDS);}
        if(rabbitConnection!=null)rabbitConnection.destroy();
    }
    static ProcessBuilder builder(Path jar,String log){
        var result=new ProcessBuilder("java","-jar",jar.toString());
        result.environment().putAll(Map.of("SPRING_PROFILES_ACTIVE","prod","SERVER_ADDRESS","127.0.0.1","RABBITMQ_HOST","127.0.0.1","RABBITMQ_PORT","15673","RABBITMQ_USER","events_it","RABBITMQ_PASSWORD","events-it-only","SPRING_RABBITMQ_VIRTUAL_HOST",VHOST));
        result.redirectErrorStream(true).redirectOutput(root.resolve("tmp/"+log+".log").toFile());return result;
    }
    static int port() throws Exception {try(var socket=new java.net.ServerSocket(0)){return socket.getLocalPort();}}
    static String sourceBase(){return "http://127.0.0.1:"+sourcePort;}
    static String backendBase(){return "http://127.0.0.1:"+backendPort;}
    static boolean ready(String url){try{return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==401;}catch(Exception e){return false;}}
    static void await(BooleanSupplier test) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);while(System.nanoTime()<end){if(test.getAsBoolean())return;Thread.sleep(100);}assertThat(test.getAsBoolean()).as("condition within 60 seconds; inspect tmp/projection-*-"+SUFFIX+".log").isTrue();}
    static void management(String method,String path,String body) throws Exception {
        var result=HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:15674"+path)).header("Authorization","Basic "+Base64.getEncoder().encodeToString("events_it:events-it-only".getBytes(StandardCharsets.UTF_8))).header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).as(result.body()).isBetween(200,204);
    }
    static HttpResponse<String> send(String base,String method,String path,Object body,String key,boolean internal) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json");
        builder.header(internal?"X-Service-Token":"Authorization",internal?INTERNAL:"Bearer "+token);if(key!=null)builder.header("Idempotency-Key",key);
        return HTTP.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    static JsonNode ok(HttpResponse<String> response,int code) throws Exception {assertThat(response.statusCode()).as(response.body()).isEqualTo(code);return JSON.readTree(response.body());}
    static long imported(String label) throws Exception {
        String header="store_code,external_order_id,display_order_no,external_status,ordered_at,requested_ship_at,currency,responsible_login,customer_id,customer_name,recipient_name,recipient_phone,country_code,province,city,district,address_line1,address_line2,postal_code,external_item_id,external_product_id,external_variant_id,external_sku,product_name,variant_name,image_url,quantity,unit_price_minor,note";
        Map<String,String> values=Map.of("store_code","PROJECTION","external_order_id",label,"external_status","NEW","ordered_at","2026-11-17T00:00:00Z","currency","USD","external_item_id",label,"product_name","Projection product","variant_name","Blue","quantity","2","unit_price_minor","100");
        String csv=header+"\n"+Arrays.stream(header.split(",")).map(k->values.getOrDefault(k,"")).collect(java.util.stream.Collectors.joining(","))+"\n";
        String boundary="projection-"+UUID.randomUUID(),body="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.csv\"\r\nContent-Type: text/csv\r\n\r\n"+csv+"\r\n--"+boundary+"--\r\n";
        var preview=HTTP.send(HttpRequest.newBuilder(URI.create(sourceBase()+"/api/order-imports/preview")).header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        var json=ok(preview,201);assertThat(json.path("status").asText()).isEqualTo("VALIDATED");
        ok(send(sourceBase(),"POST","/api/order-imports/"+json.path("importId").asText()+"/confirm",null,null,false),200);
        long id=source.queryForObject("select id from sales_orders where external_order_id=?",Long.class,label);
        await(()->backend.queryForObject("select count(*) from order_business_projections where order_id=?",Long.class,id)==1);return id;
    }
    static long requested(long order) throws Exception {
        long item=source.queryForObject("select id from sales_order_items where order_id=?",Long.class,order);
        var response=ok(send(sourceBase(),"POST","/api/orders/"+order+"/procurement-requests",Map.of("version",0,"items",List.of(Map.of("orderItemId",item,"quantity",2))),null,false),201);
        long id=response.path("requests").get(0).path("id").asLong();
        await(()->backend.queryForObject("select count(*) from procurement_request_projections where request_id=?",Long.class,id)==1);return id;
    }
    static long purchased(long request) throws Exception {
        var command=Map.of("supplierId",1,"currency","CNY","items",List.of(Map.of("productName","Test product","unitPriceMinor",100,"allocations",List.of(Map.of("procurementRequestId",request,"quantity",2,"version",0)))));
        String key="po-"+UUID.randomUUID();var response=ok(send(backendBase(),"POST","/api/purchase-orders",command,key,false),201);
        assertThat(ok(send(backendBase(),"POST","/api/purchase-orders",command,key,false),201)).isEqualTo(response);
        long id=response.path("id").asLong();
        assertThat(backend.queryForObject("select bridge_confirmed from purchase_orders where id=?",Boolean.class,id)).isTrue();
        var ordered=ok(send(backendBase(),"POST","/api/purchase-orders/"+id+"/confirm-order",
            Map.of("version",response.path("version").asLong()),null,false),200);
        assertThat(ordered.path("status").asText()).isEqualTo("ORDERED");
        assertThat(ok(send(backendBase(),"POST","/api/purchase-orders/"+id+"/confirm-order",
            Map.of("version",response.path("version").asLong()),null,false),200)).isEqualTo(ordered);
        await(()->"CONVERTED".equals(backend.queryForObject("select status from procurement_request_projections where request_id=?",String.class,request)));
        return id;
    }
    static void received(long purchase) throws Exception {
        var pkg=ok(send(backendBase(),"POST","/api/domestic-packages",Map.of("purchaseOrderId",purchase,"carrier","SF","trackingNumber","track-"+purchase),null,false),201);
        long item=backend.queryForObject("select id from purchase_order_items where purchase_order_id=?",Long.class,purchase);
        var input=Map.of("idempotencyKey","receipt-"+purchase,"packageVersion",0,"items",List.of(Map.of("purchaseOrderItemId",item,"acceptedQuantity",2,"damagedQuantity",0)),"unexpectedItems",List.of());
        var receipt=ok(send(backendBase(),"POST","/api/domestic-packages/"+pkg.path("id").asLong()+"/receipts",input,null,false),201);
        assertThat(ok(send(backendBase(),"POST","/api/domestic-packages/"+pkg.path("id").asLong()+"/receipts",input,null,false),201)).isEqualTo(receipt);
    }
    @Test @Order(1) void csvToProcurementReceiptInventoryAndPackingUsesNoLegacyOrders() throws Exception {
        long order=imported("packed"),request=requested(order),purchase=purchased(request);received(purchase);
        var orderDetail=ok(send(sourceBase(),"GET","/api/orders/"+order,null,null,false),200);
        assertThat(orderDetail.path("stage").asText()).isEqualTo("READY_FOR_PACKING");
        assertThat(orderDetail.path("items").get(0).path("inventoryAllocatedQuantity").asInt()).isEqualTo(2);
        var readyPage=ok(send(sourceBase(),"GET","/api/orders?stage=READY_FOR_PACKING",null,null,false),200);
        assertThat(readyPage.path("totalElements").asInt()).isGreaterThanOrEqualTo(1);
        var detail=ok(send(backendBase(),"GET","/api/packing/orders/"+order,null,null,false),200);
        assertThat(detail.path("availableActions").toString()).contains("START");
        var started=ok(send(backendBase(),"POST","/api/packing/orders/"+order+"/start",Map.of("reason","test"),null,false),200);
        long task=started.path("taskId").asLong(),allocation=detail.path("allocations").get(0).path("allocationId").asLong();
        var boxed=ok(send(backendBase(),"POST","/api/packing/tasks/"+task+"/boxes",Map.of("version",started.path("version").asLong(),"items",List.of(Map.of("allocationId",allocation,"quantity",2))),null,false),200);
        var input=Map.of("idempotencyKey","packed-"+order,"version",boxed.path("version").asLong());
        var completed=ok(send(backendBase(),"POST","/api/packing/tasks/"+task+"/complete",input,null,false),200);
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(ok(send(sourceBase(),"GET","/api/orders/"+order+"/fulfillment",null,null,false),200).path("stage").asText()).isEqualTo("PACKED");
        assertThat(ok(send(backendBase(),"POST","/api/packing/tasks/"+task+"/complete",input,null,false),200)).isEqualTo(completed);
        assertThat(backend.queryForObject("select consumed_quantity from inventory_lots where purchase_order_item_id=(select id from purchase_order_items where purchase_order_id=?)",Integer.class,purchase)).isEqualTo(2);
        var cancel=send(sourceBase(),"POST","/api/orders/"+order+"/cancel",Map.of("version",1,"reason","consumed test"),"cancel-packed",false);
        assertThat(cancel.statusCode()).as(cancel.body()).isEqualTo(409);assertThat(cancel.body()).contains("ORDER_CANCEL_BLOCKED_BY_CONSUMED_INVENTORY");
        assertThat(source.queryForObject("select external_status from sales_orders where id=?",String.class,order)).isEqualTo("NEW");
        assertThat(backend.queryForObject("select count(*) from sales_orders",Long.class)).isZero();
        assertThat(backend.queryForObject("select count(*) from sales_order_items",Long.class)).isZero();
        assertThat(backend.queryForObject("select count(*) from order_procurement_requests",Long.class)).isZero();
        assertThat(source.queryForObject("select count(*) from fulfillment_execution_holds where status='ACTIVE'",Long.class)).isZero();
    }
    @Test @Order(2) void repeatedAndOldDemandEventsCannotUndoConversionAndCancelReleasesStock() throws Exception {
        long order=imported("reserved"),request=requested(order),purchase=purchased(request);received(purchase);
        String body=source.queryForObject("select payload from outbox_events where event_type='order.procurement.snapshot.v1' and aggregate_id=? and aggregate_version=1",String.class,request);
        rabbit.convertAndSend("cross-erp.events","order.procurement.snapshot.v1",body);rabbit.convertAndSend("cross-erp.events","order.procurement.snapshot.v1",body);
        var old=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(source.queryForObject("select payload from outbox_events where event_type='order.procurement.snapshot.v1' and aggregate_id=? and aggregate_version=0",String.class,request));
        String event=UUID.randomUUID().toString();old.put("eventId",event);rabbit.convertAndSend("cross-erp.events","order.procurement.snapshot.v1",JSON.writeValueAsString(old));
        await(()->backend.queryForObject("select count(*) from order_event_inbox where event_id=?",Long.class,event)==1);
        assertThat(backend.queryForObject("select status from procurement_request_projections where request_id=?",String.class,request)).isEqualTo("CONVERTED");
        var input=Map.of("version",1,"reason","reserved cancellation");
        var canceled=ok(send(sourceBase(),"POST","/api/orders/"+order+"/cancel",input,"cancel-reserved",false),200);
        assertThat(ok(send(sourceBase(),"POST","/api/orders/"+order+"/cancel",input,"cancel-reserved",false),200)).isEqualTo(canceled);
        assertThat(canceled.path("externalStatus").asText()).isEqualTo("CANCELED");
        assertThat(backend.queryForObject("select count(*) from inventory_allocations a join order_item_business_projections i on i.order_item_id=a.sales_order_item_id where i.order_id=? and a.status='RESERVED'",Long.class,order)).isZero();
        assertThat(backend.queryForObject("select count(*) from order_cancellation_receipts where order_id=?",Long.class,order)).isEqualTo(1);
        assertThat(source.queryForObject("select status from order_procurement_requests where id=?",String.class,request)).isEqualTo("CONVERTED");
        assertThat(send(backendBase(),"POST","/api/packing/orders/"+order+"/start",Map.of(),null,false).statusCode()).isEqualTo(409);
    }
    @Test @Order(3) void claimAndWithdrawalSerializeAtTheOrderOwner() throws Exception {
        long order=imported("race"),request=requested(order);String key="race-claim";
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            var a=executor.submit(()->{start.await();return send(sourceBase(),"POST","/internal/procurement-requests/claims",Map.of("idempotencyKey",key,"items",List.of(Map.of("requestId",request,"quantity",2,"version",0))),null,true);});
            var b=executor.submit(()->{start.await();return send(sourceBase(),"POST","/api/orders/"+order+"/procurement-requests/"+request+"/withdraw",Map.of("reason","race","version",0),null,false);});
            start.countDown();var first=a.get(20,TimeUnit.SECONDS);var second=b.get(20,TimeUnit.SECONDS);
            assertThat(List.of(first.statusCode(),second.statusCode())).as(first.body()+second.body()).containsExactlyInAnyOrder(200,409);
            if(first.statusCode()==200)ok(send(sourceBase(),"POST","/internal/procurement-requests/claims/"+key+"/release",null,null,true),200);
        }finally{executor.shutdownNow();}
    }
    @Test @Order(4) void backendRestartRecoversExpiredPreExecutionFence() throws Exception {
        long order=imported("restart");String operation=UUID.randomUUID().toString();
        ok(send(sourceBase(),"POST","/internal/orders/"+order+"/executions/"+operation,null,null,true),200);
        backend.update("insert into fulfillment_execution_operations(operation_id,order_ids,state,lease_until) values(?,?,'READY',CURRENT_TIMESTAMP(6))",operation,"["+order+"]");
        backendProcess.destroyForcibly();backendProcess.waitFor(10,TimeUnit.SECONDS);backendProcess=backendBuilder.start();
        await(()->ready(backendBase()+"/api/purchase-orders"));
        await(()->"RELEASED".equals(backend.queryForObject("select state from fulfillment_execution_operations where operation_id=?",String.class,operation)));
        assertThat(source.queryForObject("select status from fulfillment_execution_holds where operation_id=? and order_id=?",String.class,operation,order)).isEqualTo("RELEASED");
        assertThat(send(sourceBase(),"POST","/internal/orders/"+order+"/executions/"+operation,null,null,true).statusCode()).isEqualTo(409);
    }
    @Test @Order(5) void publicJwtCannotAuthorizeInternalExecutionOrCancelCallback() throws Exception {
        assertThat(send(sourceBase(),"POST","/internal/orders/1/executions/"+UUID.randomUUID(),null,null,false).statusCode()).isEqualTo(401);
        assertThat(send(backendBase(),"POST","/internal/orders/1/cancel-inventory",Map.of("idempotencyKey","unauthorized","actorId",1,"reason","test"),null,false).statusCode()).isEqualTo(401);
        assertThat(send(backendBase(),"POST","/api/orders/1/cancel",Map.of("version",0,"reason","old route disabled"),null,false).statusCode()).isEqualTo(404);
    }
    @Test @Order(6) void releasedQuarantineCanBeManuallyAllocatedThroughTheNewSourceAssociation() throws Exception {
        long order=imported("manual"),request=requested(order),purchase=purchased(request);
        var pkg=ok(send(backendBase(),"POST","/api/domestic-packages",Map.of("purchaseOrderId",purchase,"carrier","SF","trackingNumber","manual-"+purchase),null,false),201);
        long purchaseItem=backend.queryForObject("select id from purchase_order_items where purchase_order_id=?",Long.class,purchase);
        var input=Map.of("idempotencyKey","mixed-"+purchase,"packageVersion",0,"items",List.of(Map.of("purchaseOrderItemId",purchaseItem,"acceptedQuantity",1,"damagedQuantity",1,"note","Damaged item requires inspection")),"unexpectedItems",List.of());
        ok(send(backendBase(),"POST","/api/domestic-packages/"+pkg.path("id").asLong()+"/receipts",input,null,false),201);
        long lot=backend.queryForObject("select id from inventory_lots where purchase_order_item_id=? and quality_status='DAMAGED'",Long.class,purchaseItem);
        var released=ok(send(backendBase(),"POST","/api/inventory/lots/"+lot+"/release-quarantine",Map.of("quantity",1,"reason","inspected","idempotencyKey","quarantine-"+purchase,"version",0),null,false),200);
        long item=source.queryForObject("select id from sales_order_items where order_id=?",Long.class,order);
        var allocationInput=Map.of("orderItemId",item,"quantity",1,"reason","manual source test","idempotencyKey","manual-"+purchase,"version",released.path("lotVersion").asLong());
        var result=ok(send(backendBase(),"POST","/api/inventory/lots/"+lot+"/allocate",allocationInput,null,false),200);
        assertThat(ok(send(backendBase(),"POST","/api/inventory/lots/"+lot+"/allocate",allocationInput,null,false),200)).isEqualTo(result);
        assertThat(backend.queryForObject("select sum(a.quantity) from inventory_allocations a join order_item_business_projections i on i.order_item_id=a.sales_order_item_id where i.order_id=? and a.status='RESERVED'",Integer.class,order)).isEqualTo(2);
        assertThat(source.queryForObject("select count(*) from fulfillment_execution_holds where status='ACTIVE'",Long.class)).isZero();
    }
}
