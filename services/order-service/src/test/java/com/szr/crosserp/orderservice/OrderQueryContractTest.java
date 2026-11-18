package com.szr.crosserp.orderservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szr.crosserp.orderservice.order.dto.*;
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

@SpringBootTest(properties={"spring.datasource.url=${ORDER_TEST_DATABASE_URL:jdbc:h2:mem:orderquerycontract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${ORDER_TEST_DATABASE_USER:sa}","spring.datasource.password=${ORDER_TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc
class OrderQueryContractTest {
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
    @Autowired com.szr.crosserp.orderservice.procurement.ProcurementClaimService claims;
    @org.springframework.boot.test.mock.mockito.MockBean
    com.szr.crosserp.orderservice.order.service.FulfillmentFactsClient facts;
    @org.junit.jupiter.api.BeforeEach void facts() {
        org.mockito.Mockito.when(facts.snapshot()).thenReturn(new com.szr.crosserp.orderservice.order.service.FulfillmentFactsClient.Snapshot(java.util.Map.of(),java.util.Map.of(),java.util.List.of()));
    }
    private String token(long id,String role) {
        var key=new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey)pair.getPublic()).privateKey((RSAPrivateKey)pair.getPrivate()).build();
        var encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
            JwtClaimsSet.builder().issuer("cross-border-erp").subject(Long.toString(id)).issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(900)).claim("roles",java.util.List.of(role)).build())).getTokenValue();
    }
    private long seed() {
        long id=Math.abs(java.util.UUID.randomUUID().getMostSignificantBits()%1000000000L)+100;
        jdbc.update("insert into user_display_projections(id,login_name,display_name,status,source_version) values(?,?,?,'ACTIVE',1)",id,"user"+id,"测试员工");
        jdbc.update("insert into sales_channels(id,code,name,enabled) values(?,?,?,true)",id,"channel"+id,"测试渠道");
        jdbc.update("insert into sales_stores(id,channel_id,external_store_id,code,name,status) values(?,?,?,?,?,'ACTIVE')",id,id,"store"+id,"store"+id,"测试店铺");
        jdbc.update("insert into sales_orders(id,order_no,store_id,external_order_id,external_status,recipient_phone,address_line1,country_code,version) values(?,?,?,?,'NEW','PRIVATE_PHONE','PRIVATE_ADDRESS','US',0)",id,"order"+id,id,"external"+id);
        jdbc.update("insert into sales_order_items(id,order_id,external_item_id,external_sku,product_name,quantity,fulfillment_status,version) values(?,?,?,'QUERY-SKU','测试商品',2,'NORMAL',0)",id,id,"item"+id);
        return id;
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void listFiltersSummaryDetailFulfillmentAndEventsKeepContractAndRedactProcurement() throws Exception {
        long id=seed();String owner=token(id,"OWNER");
        org.mockito.Mockito.when(facts.snapshot()).thenReturn(new com.szr.crosserp.orderservice.order.service.FulfillmentFactsClient.Snapshot(java.util.Map.of(id,2),java.util.Map.of(),java.util.List.of(id)));
        mvc.perform(get("/api/orders").param("stage","READY_FOR_PACKING").param("keyword","order"+id).header("Authorization","Bearer "+owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].stage").value("READY_FOR_PACKING"))
            .andExpect(jsonPath("$.summary.readyForPacking").value(1));
        mvc.perform(get("/api/orders/"+id).header("Authorization","Bearer "+owner)).andExpect(status().isOk())
            .andExpect(jsonPath("$.customer.recipientPhone").value("PRIVATE_PHONE"));
        var redacted=mvc.perform(get("/api/orders/"+id).header("Authorization","Bearer "+token(id,"PROCUREMENT")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(redacted).doesNotContain("PRIVATE_PHONE","PRIVATE_ADDRESS");
        mvc.perform(get("/api/orders/"+id+"/fulfillment").header("Authorization","Bearer "+owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].inventoryAllocatedQuantity").value(2));
        mvc.perform(get("/api/orders/filter-options").header("Authorization","Bearer "+owner)).andExpect(status().isOk());
        jdbc.update("insert into order_status_events(order_id,event_type,from_value,to_value,operator_id,source,remark,occurred_at) values(?,'TEST','PRIVATE_PHONE','PRIVATE_ADDRESS',?,'USER','PRIVATE_PHONE',CURRENT_TIMESTAMP(6))",id,id);
        String events=mvc.perform(get("/api/orders/"+id+"/events").header("Authorization","Bearer "+token(id,"PROCUREMENT")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andReturn().getResponse().getContentAsString();
        assertThat(events).doesNotContain("PRIVATE_PHONE","PRIVATE_ADDRESS");
        mvc.perform(get("/api/orders/"+id+"/procurement-requests").header("Authorization","Bearer "+token(id,"PACKING"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders").param("page","0").header("Authorization","Bearer "+owner)).andExpect(status().isBadRequest());
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void responsibleAndExceptionCommandsEnforceRolesVersionsAndPublishInSameTransaction() throws Exception {
        long id=seed();String owner=token(id,"OWNER");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/orders/"+id+"/responsible-user")
            .contentType("application/json").content("{\"userId\":"+id+",\"version\":0,\"reason\":\"调整负责人\"}")
            .header("Authorization","Bearer "+token(id,"OPERATIONS"))).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/orders/"+id+"/responsible-user")
            .contentType("application/json").content("{\"userId\":"+id+",\"version\":0,\"reason\":\"调整负责人\"}")
            .header("Authorization","Bearer "+owner)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/orders/"+id+"/exceptions")
            .contentType("application/json").content("{\"version\":0,\"reason\":\"库存待核对\"}")
            .header("Authorization","Bearer "+owner)).andExpect(status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/orders/"+id+"/exceptions")
            .contentType("application/json").content("{\"version\":1,\"reason\":\"库存待核对\"}")
            .header("Authorization","Bearer "+owner)).andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/orders/"+id+"/exceptions/resolve")
            .contentType("application/json").content("{\"version\":2,\"resolution\":\"已核对\"}")
            .header("Authorization","Bearer "+owner)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?",Long.class,id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from order_status_events where order_id=?",Long.class,id)).isEqualTo(3);
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void remoteFailureIsNotMisreportedAsEmptyOrPending() throws Exception {
        long id=seed();
        org.mockito.Mockito.when(facts.snapshot()).thenThrow(new com.szr.crosserp.orderservice.common.exception.ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"ORDER_FULFILLMENT_UNAVAILABLE","履约数据暂不可用"));
        mvc.perform(get("/api/orders").header("Authorization","Bearer "+token(id,"OWNER")))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ORDER_FULFILLMENT_UNAVAILABLE"));
    }
}
