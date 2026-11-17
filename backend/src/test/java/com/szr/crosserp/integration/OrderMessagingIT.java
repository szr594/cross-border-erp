package com.szr.crosserp.integration;

import com.szr.crosserp.integration.orders.OrderEventConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.profiles.active=prod","spring.datasource.url=jdbc:mysql://127.0.0.1:13318/order_consumer_it?connectionTimeZone=UTC",
    "spring.datasource.username=order_app","spring.datasource.password=order-it-app-only",
    "app.auth.jwt-secret=messaging-test-only-secret-at-least-32-bytes","app.events.enabled=true",
    "spring.rabbitmq.port=15673","spring.rabbitmq.username=events_it","spring.rabbitmq.password=events-it-only"})
class OrderMessagingIT {
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @SpyBean OrderEventConsumer consumer;
    @Autowired org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry listeners;

    @Test void versionTwoUpdatesBusinessSnapshotsAndProtectsHistoricalItems() throws Exception {
        long orderId=Math.abs(UUID.randomUUID().getMostSignificantBits()%1000000000L)+100;
        long itemId=orderId+1000000000L;
        var item=Map.of("orderItemId",itemId,"quantity",4,"productName","Business fixture","externalSku","SKU-TEST","fulfillmentStatus","NORMAL");
        java.util.function.BiFunction<String,Long,Map<String,Object>> snapshot=(eventId,version)->Map.of(
            "eventId",eventId,"eventType","order.updated.v2","schemaVersion",2,"aggregateId",orderId,
            "aggregateVersion",version,"payload",Map.of("orderId",orderId,"orderNo","BIZ-"+orderId,
                "externalStatus","PROCESSING","exception",false,"items",List.of(item)));
        String id=UUID.randomUUID().toString();byte[] body=mapper.writeValueAsBytes(snapshot.apply(id,2L));
        rabbit.send("cross-erp.events","order.updated.v2",new org.springframework.amqp.core.Message(body));
        rabbit.send("cross-erp.events","order.updated.v2",new org.springframework.amqp.core.Message(body));
        await(()->jdbc.queryForObject("select count(*) from order_business_projections where order_id=?",Long.class,orderId)==1);
        String oldId=UUID.randomUUID().toString();consumer.accept(mapper.writeValueAsBytes(snapshot.apply(oldId,1L)));
        assertThat(jdbc.queryForObject("select source_version from order_business_projections where order_id=?",Long.class,orderId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select quantity from order_item_business_projections where order_item_id=?",Integer.class,itemId)).isEqualTo(4);
        String missingId=UUID.randomUUID().toString();
        var missing=new HashMap<String,Object>(snapshot.apply(missingId,3L));
        missing.put("payload",Map.of("orderId",orderId,"orderNo","BIZ-"+orderId,"externalStatus","PROCESSING","exception",false,
            "items",List.of(Map.of("orderItemId",itemId+1,"quantity",1,"productName","Other","fulfillmentStatus","NORMAL"))));
        assertThatThrownBy(()->consumer.accept(mapper.writeValueAsBytes(missing))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("historical item");
        assertThat(jdbc.queryForObject("select count(*) from order_event_inbox where event_id=?",Long.class,missingId)).isZero();
        assertThat(jdbc.queryForObject("select source_version from order_business_projections where order_id=?",Long.class,orderId)).isEqualTo(2);
    }

    @Test void csvToOutboxToConsumerRecoversAcrossBrokerAndOrderProcessRestart() throws Exception {
        var source=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            "jdbc:mysql://127.0.0.1:13318/cross_erp_order_it2?connectionTimeZone=UTC","order_app","order-it-app-only"));
        long id=Math.abs(UUID.randomUUID().getMostSignificantBits()%1000000000L)+100;
        String code="PIPE-"+id;
        source.update("insert into user_display_projections(id,login_name,display_name,status,source_version) values(?,?,?,'ACTIVE',1)",id,code,"Test actor");
        source.update("insert into sales_channels(id,code,name,enabled) values(?,?,?,true)",id,code,"Test channel");
        source.update("insert into sales_stores(id,channel_id,external_store_id,code,name,status) values(?,?,?,?,?,'ACTIVE')",id,id,code,code,"Test store");
        var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
        var publicFile=java.nio.file.Files.createTempFile("pipeline-public-",".pem");
        java.nio.file.Files.writeString(publicFile,"-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(pair.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n");
        publicFile.toFile().deleteOnExit();
        var key=new com.nimbusds.jose.jwk.RSAKey.Builder((java.security.interfaces.RSAPublicKey)pair.getPublic()).privateKey((java.security.interfaces.RSAPrivateKey)pair.getPrivate()).build();
        var encoder=new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(key)));
        var claims=org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("cross-border-erp").subject(Long.toString(id)).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).claim("roles",List.of("OWNER")).build();
        String token=encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build(),claims)).getTokenValue();
        var root=java.nio.file.Path.of("..").toAbsolutePath().normalize();
        var builder=new ProcessBuilder("java","-jar",root.resolve("services/order-service/target/order-service-0.1.0-SNAPSHOT.jar").toString());
        builder.environment().putAll(Map.of("ORDER_DATABASE_URL","jdbc:mysql://127.0.0.1:13318/cross_erp_order_it2?connectionTimeZone=UTC",
            "ORDER_DATABASE_USER","order_app","ORDER_DATABASE_PASSWORD","order-it-app-only","ORDER_PORT","19082",
            "IDENTITY_PUBLIC_KEY",publicFile.toUri().toString(),"APP_EVENTS_ENABLED","true",
            "RABBITMQ_PORT","15673","RABBITMQ_USER","events_it","RABBITMQ_PASSWORD","events-it-only"));
        var log=java.nio.file.Files.createTempFile("order-pipeline-",".log");
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        Process process=builder.start();
        try {
            waitOrder();
            long first=importCsv(code,token,"first");
            await(()->jdbc.queryForObject("select count(*) from order_business_projections where order_id=?",Long.class,first)==1);
            assertThat(source.queryForObject("select published_at from outbox_events where aggregate_id=?",java.sql.Timestamp.class,first)).isNotNull();
            assertThat(new ProcessBuilder("docker","stop","erp-events-it-rabbit").redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor()).isZero();
            long second=importCsv(code,token,"offline");
            assertThat(source.queryForObject("select published_at from outbox_events where aggregate_id=?",java.sql.Timestamp.class,second)).isNull();
            process.destroyForcibly();process.waitFor();
            assertThat(new ProcessBuilder("docker","start","erp-events-it-rabbit").redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor()).isZero();
            process=builder.start();waitOrder();
            await(()->jdbc.queryForObject("select count(*) from order_business_projections where order_id=?",Long.class,second)==1);
            assertThat(source.queryForObject("select published_at from outbox_events where aggregate_id=?",java.sql.Timestamp.class,second)).isNotNull();
            assertThat(jdbc.queryForObject("select count(*) from order_event_inbox where event_id=(select event_id from order_event_inbox where event_id=? limit 1)",Long.class,
                source.queryForObject("select event_id from outbox_events where aggregate_id=?",String.class,second))).isEqualTo(1);
        } finally {
            if(process.isAlive()){process.destroyForcibly();process.waitFor();}
            new ProcessBuilder("docker","start","erp-events-it-rabbit").redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
        }
    }
    private void waitOrder() throws Exception {
        await(()->{try{return java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:19082/api/order-imports/x")).GET().build(),java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode()==401;}catch(Exception e){return false;}});
    }
    private long importCsv(String code,String token,String suffix) throws Exception {
        String header="store_code,external_order_id,display_order_no,external_status,ordered_at,requested_ship_at,currency,responsible_login,customer_id,customer_name,recipient_name,recipient_phone,country_code,province,city,district,address_line1,address_line2,postal_code,external_item_id,external_product_id,external_variant_id,external_sku,product_name,variant_name,image_url,quantity,unit_price_minor,note";
        var values=Map.of("store_code",code,"external_order_id",code+suffix,"external_status","NEW","ordered_at","2026-11-17T00:00:00Z","currency","USD","external_item_id",code+suffix,"product_name","Pipeline test","quantity","2","unit_price_minor","100");
        String csv=header+"\n"+Arrays.stream(header.split(",")).map(f->values.getOrDefault(f,"")).collect(java.util.stream.Collectors.joining(","))+"\n";
        String boundary="test-"+UUID.randomUUID();String body="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.csv\"\r\nContent-Type: text/csv\r\n\r\n"+csv+"\r\n--"+boundary+"--\r\n";
        var client=java.net.http.HttpClient.newHttpClient();
        var preview=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:19082/api/order-imports/preview"))
            .header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary).POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(preview.statusCode()).isEqualTo(201);var json=mapper.readTree(preview.body());assertThat(json.path("status").asText()).isEqualTo("VALIDATED");
        var confirm=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:19082/api/order-imports/"+json.path("importId").asText()+"/confirm"))
            .header("Authorization","Bearer "+token).POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(confirm.statusCode()).isEqualTo(200);assertThat(mapper.readTree(confirm.body()).path("status").asText()).isEqualTo("COMPLETED");
        try(var connection=java.sql.DriverManager.getConnection("jdbc:mysql://127.0.0.1:13318/cross_erp_order_it2","order_app","order-it-app-only");
            var statement=connection.prepareStatement("select id from sales_orders where external_order_id=?")) {
            statement.setString(1,code+suffix);try(var result=statement.executeQuery()){assertThat(result.next()).isTrue();return result.getLong(1);}
        }
    }

    @Test void committedMessageWithoutAckIsRedeliveredAfterConnectionLoss() throws Exception {
        String id=UUID.randomUUID().toString();long orderId=Math.abs(UUID.randomUUID().getMostSignificantBits()%1000000000L)+1;
        byte[] body=event(id,orderId,1,2);
        listeners.stop();
        try {
            send(body);
            var connection=rabbit.getConnectionFactory().createConnection();
            var channel=connection.createChannel(false);
            try {
                var delivery=new java.util.concurrent.atomic.AtomicReference<com.rabbitmq.client.GetResponse>();
                await(()->{try {delivery.set(channel.basicGet("legacy.order-sync",false));return delivery.get()!=null;}catch(Exception e){throw new RuntimeException(e);}});
                assertThat(delivery.get().getBody()).isEqualTo(body);
                consumer.accept(body);
                assertThat(jdbc.queryForObject("select count(*) from order_event_inbox where event_id=?",Long.class,id)).isEqualTo(1);
                // Close the channel after commit but before ACK: RabbitMQ must requeue this delivery.
                if(channel instanceof org.springframework.amqp.rabbit.connection.ChannelProxy proxy) {
                    proxy.getTargetChannel().abort();
                } else {channel.abort();}
            } finally {connection.close();}
            listeners.start();
            await(()->{try {verify(consumer,atLeast(2)).accept(org.mockito.ArgumentMatchers.argThat(b->Arrays.equals(b,body)));return true;}catch(AssertionError e){return false;}catch(Exception e){throw new RuntimeException(e);}});
            assertThat(jdbc.queryForObject("select count(*) from order_event_inbox where event_id=?",Long.class,id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from order_event_projections where order_id=?",Long.class,orderId)).isEqualTo(1);
        } finally {listeners.start();}
    }

    @Test void durableMessageSurvivesBrokerRestartAndListenerRecovery() throws Exception {
        String id=UUID.randomUUID().toString();long orderId=Math.abs(UUID.randomUUID().getMostSignificantBits()%1000000000L)+1;
        listeners.stop();
        try {
            byte[] body=event(id,orderId,1,2);
            var properties=new org.springframework.amqp.core.MessageProperties();
            properties.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
            rabbit.send("cross-erp.events","order.created.v1",new org.springframework.amqp.core.Message(body,properties));
            assertThat(new ProcessBuilder("docker","stop","erp-events-it-rabbit").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor()).isZero();
            assertThat(new ProcessBuilder("docker","start","erp-events-it-rabbit").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor()).isZero();
            listeners.start();
            await(()->jdbc.queryForObject("select count(*) from order_event_inbox where event_id=?",Long.class,id)==1);
            assertThat(jdbc.queryForObject("select source_version from order_event_projections where order_id=?",Long.class,orderId)).isEqualTo(1);
        } finally {
            new ProcessBuilder("docker","start","erp-events-it-rabbit").redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
            listeners.start();
        }
    }
    private byte[] event(String id,long orderId,long version,int quantity) throws Exception {
        return mapper.writeValueAsBytes(Map.of("eventId",id,"eventType","order.created.v1","schemaVersion",1,
            "aggregateId",orderId,"aggregateVersion",version,"payload",Map.of("orderId",orderId,
                "items",List.of(Map.of("orderItemId",orderId+1,"quantity",quantity)))));
    }
    private void send(byte[] bytes){rabbit.send("cross-erp.events","order.created.v1",new org.springframework.amqp.core.Message(bytes));}
    private void await(java.util.function.BooleanSupplier condition) throws Exception {
        long until=System.nanoTime()+Duration.ofSeconds(45).toNanos();
        while(!condition.getAsBoolean()){if(System.nanoTime()>until)throw new AssertionError("Timed out waiting for message processing");Thread.sleep(100);}
    }
    @Test void duplicateAndStaleEventsDoNotOverwriteProjection() throws Exception {
        long orderId=Math.abs(UUID.randomUUID().getMostSignificantBits()%1000000000L)+1;
        String id=UUID.randomUUID().toString();byte[] latest=event(id,orderId,2,8);
        send(latest);send(latest);
        await(()->jdbc.queryForObject("select count(*) from order_event_inbox where event_id=?",Long.class,id)==1);
        String staleId=UUID.randomUUID().toString();send(event(staleId,orderId,1,3));
        await(()->jdbc.queryForObject("select count(*) from order_event_inbox where event_id=?",Long.class,staleId)==1);
        assertThat(jdbc.queryForObject("select source_version from order_event_projections where order_id=?",Long.class,orderId)).isEqualTo(2);
        assertThat(mapper.readTree(jdbc.queryForObject("select items_payload from order_event_projections where order_id=?",String.class,orderId)).get(0).get("quantity").asInt()).isEqualTo(8);
        assertThat(jdbc.queryForObject("select count(*) from order_event_inbox where event_id=?",Long.class,id)).isEqualTo(1);
    }
    @Test void invalidEventRetriesThreeTimesThenDeadLettersWithoutInbox() throws Exception {
        String id=UUID.randomUUID().toString();byte[] invalid=event(id,999999999L,1,-1);send(invalid);
        var dead=rabbit.receive("legacy.order-sync.dead",15000);
        assertThat(dead).isNotNull();assertThat(dead.getBody()).isEqualTo(invalid);
        verify(consumer,times(3)).accept(org.mockito.ArgumentMatchers.argThat(bytes->Arrays.equals(bytes,invalid)));
        assertThat(jdbc.queryForObject("select count(*) from order_event_inbox where event_id=?",Long.class,id)).isZero();
    }
}
