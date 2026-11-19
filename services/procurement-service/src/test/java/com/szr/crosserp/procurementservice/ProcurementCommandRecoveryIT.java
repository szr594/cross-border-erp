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

@SpringBootTest(properties={"spring.datasource.url=${PROCUREMENT_TEST_DATABASE_URL:jdbc:h2:mem:procurementcommands;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${PROCUREMENT_TEST_DATABASE_USER:sa}","spring.datasource.password=${PROCUREMENT_TEST_DATABASE_PASSWORD:}"})
@org.springframework.test.context.TestPropertySource(properties={"app.procurement.remote-enabled=true","ORDER_SERVICE_INTERNAL_URL=http://127.0.0.1:19999","ORDER_INTERNAL_SERVICE_TOKEN=procurement-test-only-at-least-thirty-two-bytes","app.procurement.recovery-delay-ms=3600000"})
@AutoConfigureMockMvc
class ProcurementCommandRecoveryIT {
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
    @org.springframework.boot.test.mock.mockito.MockBean
    com.szr.crosserp.procurementservice.procurement.service.RemoteProcurementClaims remote;
    @Autowired com.szr.crosserp.procurementservice.procurement.service.PurchaseBridgeWorkflow workflow;
    @Test void lostConfirmationResponseRecoversSamePurchaseAndOrderingReplaysOnce() throws Exception {
        jdbc.update("insert into user_display_projections values(1,'test-owner','测试老板','ACTIVE',1)");
        jdbc.update("insert into suppliers(id,code,name,platform,status) values(1,'TEST','测试供应商','MANUAL','ACTIVE')");
        jdbc.update("insert into procurement_order_projections values(41,'SOURCE-41','1:source','NEW',false,1)");
        jdbc.update("insert into procurement_item_projections(order_item_id,order_id,product_name,quantity,fulfillment_status,source_version) values(51,41,'演示商品',2,'NORMAL',1)");
        jdbc.update("insert into procurement_request_projections values(61,51,2,'REQUESTED',0)");
        var claimed=new com.szr.crosserp.procurementservice.procurement.service.RemoteProcurementClaims.Result("claim-key","CLAIMED",null);
        var state=new java.util.concurrent.atomic.AtomicReference<com.szr.crosserp.procurementservice.procurement.service.RemoteProcurementClaims.Result>();
        org.mockito.Mockito.when(remote.get("claim-key")).thenAnswer(call->{if(state.get()==null)throw new com.szr.crosserp.procurementservice.common.exception.ApiException(org.springframework.http.HttpStatus.NOT_FOUND,"CLAIM_NOT_FOUND","test");return state.get();});
        org.mockito.Mockito.when(remote.claim(org.mockito.ArgumentMatchers.eq("claim-key"),org.mockito.ArgumentMatchers.any())).thenAnswer(call->{state.set(claimed);return claimed;});
        var first=new java.util.concurrent.atomic.AtomicBoolean(true);
        org.mockito.Mockito.when(remote.confirm(org.mockito.ArgumentMatchers.eq("claim-key"),org.mockito.ArgumentMatchers.anyString())).thenAnswer(call->{
            var confirmed=new com.szr.crosserp.procurementservice.procurement.service.RemoteProcurementClaims.Result("claim-key","CONFIRMED",call.getArgument(1));state.set(confirmed);
            if(first.getAndSet(false))throw new com.szr.crosserp.procurementservice.common.exception.ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"PURCHASE_OPERATION_PENDING","test response lost");return confirmed;
        });
        String input="{\"supplierId\":1,\"currency\":\"CNY\",\"items\":[{\"productName\":\"演示商品\",\"unitPriceMinor\":100,\"allocations\":[{\"procurementRequestId\":61,\"quantity\":2,\"version\":0}]}]}";
        String auth="Bearer "+token("OWNER");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/purchase-orders").header("Authorization",auth).header("Idempotency-Key","claim-key").contentType("application/json").content(input)).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("select count(*) from purchase_orders",Long.class)).isEqualTo(1);
        mvc.perform(get("/api/purchase-orders").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        var recovered=workflow.resume("claim-key");
        var replay=workflow.create("claim-key",mapper.readValue(input,com.szr.crosserp.procurementservice.procurement.dto.CreatePurchaseOrderRequest.class),"1",java.util.List.of("OWNER"));
        assertThat(replay).isEqualTo(recovered);
        assertThat(jdbc.queryForObject("select count(*) from purchase_orders",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select state from purchase_create_operations where idempotency_key='claim-key'",String.class)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("select purchased_quantity from purchase_order_items",Integer.class)).isEqualTo(2);
        String path="/api/purchase-orders/"+recovered.id()+"/confirm-order";
        String version="{\"version\":"+recovered.version()+"}";
        String original=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Authorization",auth).contentType("application/json").content(version)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ORDERED")).andReturn().getResponse().getContentAsString();
        String again=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Authorization",auth).contentType("application/json").content(version)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(again).isEqualTo(original);
        assertThat(jdbc.queryForObject("select count(*) from purchase_order_events where event_type='ORDERED'",Long.class)).isEqualTo(1);
    }
}
