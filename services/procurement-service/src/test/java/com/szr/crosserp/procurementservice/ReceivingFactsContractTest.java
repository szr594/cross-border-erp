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

@SpringBootTest(properties={"spring.datasource.url=${PROCUREMENT_TEST_DATABASE_URL:jdbc:h2:mem:receivingfacts;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${PROCUREMENT_TEST_DATABASE_USER:sa}","spring.datasource.password=${PROCUREMENT_TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc
class ReceivingFactsContractTest {
    static java.security.KeyPair pair;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry) throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);pair=generator.generateKeyPair();
        var file=Files.createTempFile("order-public-",".pem");
        Files.writeString(file,"-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{10})
            .encodeToString(pair.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n");file.toFile().deleteOnExit();
        registry.add("app.auth.public-key",()->file.toUri().toString());
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtDecoder decoder;

    @Autowired com.szr.crosserp.procurementservice.events.ReceivingFactsConsumer consumer;
    byte[] event(String id,long version,int inspected) throws Exception {
        return mapper.writeValueAsBytes(java.util.Map.of("eventId",id,"eventType","receiving.snapshot.v1","schemaVersion",1,"aggregateId",71,"payload",java.util.Map.of("items",java.util.List.of(java.util.Map.of("purchaseOrderItemId",81,"sourceVersion",version,"inspectedQuantity",inspected,"acceptedQuantity",inspected)))));
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void duplicatesAndOldVersionsNeverAddQuantitiesAgain() throws Exception {
        jdbc.update("insert into user_display_projections values(1,'owner','Test','ACTIVE',1)");
        jdbc.update("insert into suppliers(id,code,name,platform,status) values(1,'TEST','Test','MANUAL','ACTIVE')");
        jdbc.update("insert into purchase_orders(id,purchase_order_no,supplier_id,status,currency,created_by) values(71,'TEST',1,'ORDERED','CNY',1)");
        jdbc.update("insert into purchase_order_items(id,purchase_order_id,product_name,purchased_quantity,currency) values(81,71,'Test',2,'CNY')");
        byte[] first=event(java.util.UUID.randomUUID().toString(),1,1);consumer.accept(first);consumer.accept(first);
        assertThat(jdbc.queryForObject("select received_quantity from purchase_order_items where id=81",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from purchase_orders where id=71",String.class)).isEqualTo("PARTIALLY_RECEIVED");
        var mixed=mapper.readTree(event(java.util.UUID.randomUUID().toString(),2,2));
        ((com.fasterxml.jackson.databind.node.ObjectNode)mixed.path("payload").path("items").get(0)).put("acceptedQuantity",1);
        consumer.accept(mapper.writeValueAsBytes(mixed));consumer.accept(event(java.util.UUID.randomUUID().toString(),1,1));
        assertThat(jdbc.queryForObject("select received_quantity from purchase_order_items where id=81",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from purchase_orders where id=71",String.class)).isEqualTo("RECEIVED");
        assertThat(jdbc.queryForObject("select source_version from procurement_receiving_projections where purchase_order_item_id=81",Long.class)).isEqualTo(2);
    }
}
