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

@SpringBootTest(properties={"spring.datasource.url=${PROCUREMENT_TEST_DATABASE_URL:jdbc:h2:mem:procurementquery;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${PROCUREMENT_TEST_DATABASE_USER:sa}","spring.datasource.password=${PROCUREMENT_TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc
class ProcurementQueryContractTest {
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

    private String token(String role) {
        var key=new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey)pair.getPublic()).privateKey((RSAPrivateKey)pair.getPrivate()).build();
        var encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),JwtClaimsSet.builder().issuer("cross-border-erp").subject("1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).claim("roles",java.util.List.of(role)).build())).getTokenValue();
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void supplierPermissionsUniquenessVersionAndQueryContract() throws Exception {
        String input="{\"code\":\"SUP-QUERY\",\"name\":\"测试供应商\",\"platform\":\"MANUAL\"}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/suppliers").header("Authorization","Bearer "+token("OPERATIONS")).contentType("application/json").content(input)).andExpect(status().isForbidden());
        var created=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/suppliers").header("Authorization","Bearer "+token("OWNER")).contentType("application/json").content(input)).andExpect(status().isCreated()).andReturn();
        long id=mapper.readTree(created.getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/suppliers").header("Authorization","Bearer "+token("PROCUREMENT")).contentType("application/json").content(input)).andExpect(status().isConflict());
        mvc.perform(get("/api/suppliers").param("keyword","SUP-QUERY").header("Authorization","Bearer "+token("WAREHOUSE"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/suppliers/"+id+"/disable").header("Authorization","Bearer "+token("OWNER")).contentType("application/json").content("{\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/suppliers/"+id+"/enable").header("Authorization","Bearer "+token("OWNER")).contentType("application/json").content("{\"version\":0}")).andExpect(status().isConflict());
        mvc.perform(get("/api/suppliers/"+id).header("Authorization","Bearer "+token("PACKING"))).andExpect(status().isOk());
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void purchaseQueriesResolveOnlyLocalProjectionsAndHideUnconfirmedOrders() throws Exception {
        jdbc.update("insert into user_display_projections values(1,'test-owner','测试老板','ACTIVE',1)");
        jdbc.update("insert into suppliers(id,code,name,platform,status) values(1,'TEST','测试供应商','MANUAL','ACTIVE')");
        jdbc.update("insert into procurement_order_projections values(41,'SOURCE-41','1:source','NEW',false,1)");
        jdbc.update("insert into procurement_item_projections(order_item_id,order_id,product_name,quantity,fulfillment_status,source_version) values(51,41,'演示商品',2,'NORMAL',1)");
        jdbc.update("insert into procurement_request_projections values(61,51,2,'CONVERTED',1)");
        jdbc.update("insert into purchase_orders(id,purchase_order_no,supplier_id,status,currency,created_by) values(71,'PO-QUERY',1,'DRAFT','CNY',1)");
        jdbc.update("insert into purchase_orders(id,purchase_order_no,supplier_id,status,currency,created_by,bridge_confirmed) values(72,'PO-HIDDEN',1,'DRAFT','CNY',1,false)");
        jdbc.update("insert into purchase_order_items(id,purchase_order_id,product_name,purchased_quantity,currency) values(81,71,'演示商品',2,'CNY')");
        jdbc.update("insert into purchase_order_allocations(purchase_order_item_id,procurement_request_id,allocated_quantity) values(81,61,2)");
        jdbc.update("insert into purchase_order_events(purchase_order_id,event_type,operator_id,occurred_at) values(71,'CREATED',1,CURRENT_TIMESTAMP(6))");
        String auth="Bearer "+token("OWNER");
        mvc.perform(get("/api/purchase-orders").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].purchasedQuantity").value(2));
        mvc.perform(get("/api/purchase-orders/71").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].sources[0].salesOrderId").value(41)).andExpect(jsonPath("$.items[0].sources[0].procurementRequestId").value(61));
        mvc.perform(get("/api/purchase-orders/71/events").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/purchase-orders/72").header("Authorization",auth)).andExpect(status().isNotFound());
        mvc.perform(get("/api/purchase-orders").param("sort","invalid,asc").header("Authorization",auth)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/purchase-orders/71/events").param("page","0").header("Authorization",auth)).andExpect(status().isBadRequest());
    }
}
