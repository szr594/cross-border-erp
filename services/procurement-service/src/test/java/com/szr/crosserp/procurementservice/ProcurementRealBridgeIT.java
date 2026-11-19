package com.szr.crosserp.procurementservice;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProcurementRealBridgeIT {
    static final String INTERNAL="real-procurement-test-only-at-least-32-bytes";
    static final ObjectMapper JSON=new ObjectMapper();
    static final HttpClient HTTP=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();
    static JdbcTemplate source,target;static Process orderProcess,purchaseProcess;static ProcessBuilder purchaseBuilder;
    static final String VHOST="procurement-bridge-"+UUID.randomUUID();
    static int orderPort,purchasePort;static String token;static Path root;static HttpServer proxy;
    static volatile boolean loseConfirmation;static volatile CountDownLatch confirmed=new CountDownLatch(1),release=new CountDownLatch(1);
    static ExecutorService proxyThreads=Executors.newCachedThreadPool();
    static String database(String prefix) {
        String name=prefix+UUID.randomUUID().toString().replace("-","");
        var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13318/mysql?allowPublicKeyRetrieval=true&useSSL=false","root","order-it-root-only"));admin.execute("create database "+name);
        return "jdbc:mysql://127.0.0.1:13318/"+name+"?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
    }
    static int port() throws Exception {try(var socket=new java.net.ServerSocket(0)){return socket.getLocalPort();}}
    static ProcessBuilder process(String jar,String log,Map<String,String> env) {
        var result=new ProcessBuilder("java","-jar",root.resolve(jar).toString());result.environment().putAll(env);result.environment().putAll(Map.of("APP_EVENTS_ENABLED","true","RABBITMQ_HOST","127.0.0.1","RABBITMQ_PORT","15673","RABBITMQ_USER","events_it","RABBITMQ_PASSWORD","events-it-only","SPRING_RABBITMQ_VIRTUAL_HOST",VHOST));result.environment().put("SERVER_ADDRESS","127.0.0.1");result.redirectErrorStream(true).redirectOutput(root.resolve("tmp/"+log+".log").toFile());return result;
    }
    @BeforeAll static void start() throws Exception {
        root=Path.of("../..").toAbsolutePath().normalize();String suffix=UUID.randomUUID().toString();
        String a=database("real_order_"),b=database("real_purchase_");source=new JdbcTemplate(new DriverManagerDataSource(a,"root","order-it-root-only"));target=new JdbcTemplate(new DriverManagerDataSource(b,"root","order-it-root-only"));
        var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();var pub=Files.createTempFile("procurement-public-",".pem");Files.writeString(pub,"-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(pair.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n");pub.toFile().deleteOnExit();
        var key=new com.nimbusds.jose.jwk.RSAKey.Builder((java.security.interfaces.RSAPublicKey)pair.getPublic()).privateKey((java.security.interfaces.RSAPrivateKey)pair.getPrivate()).build();
        var encoder=new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(key)));
        token=encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build(),org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("cross-border-erp").subject("1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).claim("roles",List.of("OWNER")).build())).getTokenValue();
        management("/api/vhosts/"+VHOST,"{}");
        management("/api/permissions/"+VHOST+"/events_it","{\"configure\":\".*\",\"write\":\".*\",\"read\":\".*\"}");
        orderPort=port();purchasePort=port();
        proxy=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);proxy.setExecutor(proxyThreads);
        proxy.createContext("/",exchange->{try{
            byte[] body=exchange.getRequestBody().readAllBytes();var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+orderPort+exchange.getRequestURI())).header("X-Service-Token",INTERNAL).header("Content-Type","application/json").method(exchange.getRequestMethod(),HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var response=HTTP.send(request,HttpResponse.BodyHandlers.ofByteArray());
            if(loseConfirmation && exchange.getRequestURI().getPath().endsWith("/confirm") && response.statusCode()==200){confirmed.countDown();release.await(30,TimeUnit.SECONDS);exchange.close();return;}
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(response.statusCode(),response.body().length);exchange.getResponseBody().write(response.body());
        }catch(Exception error){exchange.close();}finally{exchange.close();}});proxy.start();
        orderProcess=process("services/order-service/target/order-service-0.1.0-SNAPSHOT.jar","real-order-"+suffix,Map.of("ORDER_DATABASE_URL",a,"ORDER_DATABASE_USER","root","ORDER_DATABASE_PASSWORD","order-it-root-only","ORDER_PORT",Integer.toString(orderPort),"IDENTITY_PUBLIC_KEY",pub.toUri().toString())).start();
        purchaseBuilder=process("services/procurement-service/target/procurement-service-0.1.0-SNAPSHOT.jar","real-procurement-"+suffix,Map.of("PROCUREMENT_DATABASE_URL",b,"PROCUREMENT_DATABASE_USER","root","PROCUREMENT_DATABASE_PASSWORD","order-it-root-only","PROCUREMENT_PORT",Integer.toString(purchasePort),"IDENTITY_PUBLIC_KEY",pub.toUri().toString(),"APP_PROCUREMENT_REMOTE_ENABLED","true","ORDER_SERVICE_INTERNAL_URL","http://127.0.0.1:"+proxy.getAddress().getPort(),"ORDER_INTERNAL_SERVICE_TOKEN",INTERNAL,"APP_PROCUREMENT_RECOVERY_DELAY_MS","1000"));
        // Internal token authenticates the real Order controller; proxy supplies it only for fault transport.
        orderProcess.destroyForcibly();orderProcess.waitFor(10,TimeUnit.SECONDS);
        var orderBuilder=process("services/order-service/target/order-service-0.1.0-SNAPSHOT.jar","real-order-"+suffix,Map.of("ORDER_DATABASE_URL",a,"ORDER_DATABASE_USER","root","ORDER_DATABASE_PASSWORD","order-it-root-only","ORDER_PORT",Integer.toString(orderPort),"IDENTITY_PUBLIC_KEY",pub.toUri().toString(),"ORDER_INTERNAL_SERVICE_TOKEN",INTERNAL));orderProcess=orderBuilder.start();
        purchaseProcess=purchaseBuilder.start();await(()->ready(orderPort));await(()->ready(purchasePort));
        source.update("insert into user_display_projections values(1,'owner','Test owner','ACTIVE',1)");source.update("insert into sales_channels(id,code,name,enabled) values(1,'TEST','Test',true)");source.update("insert into sales_stores(id,channel_id,external_store_id,code,name,status) values(1,1,'store','TEST','Test','ACTIVE')");
        target.update("insert into user_display_projections values(1,'owner','Test owner','ACTIVE',1)");target.update("insert into suppliers(id,code,name,platform,status) values(1,'TEST','Test','MANUAL','ACTIVE')");
    }
    static boolean ready(int port){try{return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/purchase-orders")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==401;}catch(Exception e){return false;}}
    static void await(BooleanSupplier condition) throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(80);while(System.nanoTime()<until){if(condition.getAsBoolean())return;Thread.sleep(150);}assertThat(condition.getAsBoolean()).isTrue();}
    static void management(String path,String body)throws Exception{
        var response=HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:15674"+path)).header("Authorization","Basic "+Base64.getEncoder().encodeToString("events_it:events-it-only".getBytes(java.nio.charset.StandardCharsets.UTF_8))).header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isBetween(200,204);
    }
    static void seed(long id)throws Exception {
        source.update("insert into sales_orders(id,order_no,store_id,external_order_id,external_status,version) values(?,?,1,?,'NEW',0)",id,"REAL-"+id,"real-"+id);
        source.update("insert into sales_order_items(id,order_id,external_item_id,product_name,quantity,fulfillment_status,version) values(?,?,?,'Product',2,'NORMAL',0)",id,id,"item-"+id);
        // Reserve deterministic fixture IDs; the demand itself is created by the public HTTP API.
        source.execute("alter table order_procurement_requests auto_increment="+id);
        String body="{\"version\":0,\"items\":[{\"orderItemId\":"+id+",\"quantity\":2}]}";
        var response=HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+orderPort+"/api/orders/"+id+"/procurement-requests")).header("Authorization","Bearer "+token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        await(()->target.queryForObject("select count(*) from procurement_request_projections where request_id=? and status='REQUESTED'",Integer.class,id)==1);
        assertThat(target.queryForObject("select order_item_id from procurement_request_projections where request_id=?",Long.class,id)).isEqualTo(id);
    }
    static String input(long id){return "{\"supplierId\":1,\"currency\":\"CNY\",\"items\":[{\"productName\":\"Product\",\"unitPriceMinor\":100,\"allocations\":[{\"procurementRequestId\":"+id+",\"quantity\":2,\"version\":0}]}]}";}
    static HttpResponse<String> create(long id,String key)throws Exception{return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+purchasePort+"/api/purchase-orders")).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+token).header("Idempotency-Key",key).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(input(id))).build(),HttpResponse.BodyHandlers.ofString());}
    @Test @Order(1) void realHttpClaimsAndConfirmsAgainstSeparateOrderDatabase() throws Exception {
        seed(101);var response=create(101,"real-key");assertThat(response.statusCode()).as(response.body()).isEqualTo(201);assertThat(create(101,"real-key").body()).isEqualTo(response.body());
        assertThat(source.queryForObject("select status from order_procurement_requests where id=101",String.class)).isEqualTo("CONVERTED");
        assertThat(target.queryForObject("select state from purchase_create_operations where idempotency_key='real-key'",String.class)).isEqualTo("SUCCEEDED");
    }
    @Test @Order(2) void concurrentSameKeyLeaseCreatesOnlyOneLocalPurchase() throws Exception {
        seed(102);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {var a=pool.submit(()->{start.await();return create(102,"lease-key");});var b=pool.submit(()->{start.await();return create(102,"lease-key");});start.countDown();var responses=List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS));
            assertThat(responses.stream().map(HttpResponse::statusCode).toList()).allMatch(code->code==201||code==503);
            await(()->"SUCCEEDED".equals(target.queryForObject("select state from purchase_create_operations where idempotency_key='lease-key'",String.class)));
            assertThat(create(102,"lease-key").statusCode()).isEqualTo(201);
            assertThat(target.queryForObject("select count(*) from purchase_order_allocations where procurement_request_id=102",Long.class)).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }
    @Test @Order(3) void killedProcessRecoversConfirmedRemoteClaimAfterLeaseExpires() throws Exception {
        seed(103);loseConfirmation=true;var pool=Executors.newSingleThreadExecutor();
        try {var pending=pool.submit(()->create(103,"restart-key"));assertThat(confirmed.await(20,TimeUnit.SECONDS)).isTrue();
            assertThat(source.queryForObject("select status from procurement_claims where claim_id='restart-key'",String.class)).isEqualTo("CONFIRMED");
            purchaseProcess.destroyForcibly();assertThat(purchaseProcess.waitFor(10,TimeUnit.SECONDS)).isTrue();loseConfirmation=false;release.countDown();
            assertThat(target.queryForObject("select state from purchase_create_operations where idempotency_key='restart-key'",String.class)).isEqualTo("PENDING");
            assertThat(target.queryForObject("select bridge_confirmed from purchase_orders where id=(select purchase_order_id from purchase_create_operations where idempotency_key='restart-key')",Boolean.class)).isFalse();
            purchaseProcess=purchaseBuilder.start();await(()->ready(purchasePort));
            await(()->"SUCCEEDED".equals(target.queryForObject("select state from purchase_create_operations where idempotency_key='restart-key'",String.class)));
            var replay=create(103,"restart-key");assertThat(replay.statusCode()).as(replay.body()).isEqualTo(201);
            assertThat(target.queryForObject("select count(*) from purchase_order_allocations where procurement_request_id=103",Long.class)).isEqualTo(1);
            assertThat(target.queryForObject("select bridge_confirmed from purchase_orders where id=(select purchase_order_id from purchase_create_operations where idempotency_key='restart-key')",Boolean.class)).isTrue();
        }finally{loseConfirmation=false;release.countDown();pool.shutdownNow();}
    }
    @AfterAll static void stop()throws Exception{if(purchaseProcess!=null){purchaseProcess.destroyForcibly();purchaseProcess.waitFor(10,TimeUnit.SECONDS);}if(orderProcess!=null){orderProcess.destroyForcibly();orderProcess.waitFor(10,TimeUnit.SECONDS);}if(proxy!=null)proxy.stop(0);proxyThreads.shutdownNow();}
}
