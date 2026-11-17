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

@SpringBootTest(properties={"spring.datasource.url=${ORDER_TEST_DATABASE_URL:jdbc:h2:mem:orderfoundation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${ORDER_TEST_DATABASE_USER:sa}","spring.datasource.password=${ORDER_TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc
class OrderFoundationTest {
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
    @Test
    @org.springframework.transaction.annotation.Transactional
    void csvPreviewConfirmAndErrorsWorkAgainstIndependentDatabase() throws Exception {
        long id=Math.abs(java.util.UUID.randomUUID().getMostSignificantBits()%1000000000L)+100;
        String code="CSV-"+id;
        jdbc.update("insert into user_display_projections(id,login_name,display_name,status,source_version) values(?,?,?,'ACTIVE',1)",id,code,"测试操作人");
        jdbc.update("insert into sales_channels(id,code,name,enabled) values(?,?,?,true)",id,code,"测试渠道");
        jdbc.update("insert into sales_stores(id,channel_id,external_store_id,code,name,status) values(?,?,?,?,?,'ACTIVE')",id,id,code,code,"测试店铺");
        var key=new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey)pair.getPublic()).privateKey((RSAPrivateKey)pair.getPrivate()).build();
        var encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        var claims=JwtClaimsSet.builder().issuer("cross-border-erp").subject(Long.toString(id)).issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(900)).claim("roles",java.util.List.of("OWNER")).build();
        String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),claims)).getTokenValue();
        String header="store_code,external_order_id,display_order_no,external_status,ordered_at,requested_ship_at,currency,responsible_login,customer_id,customer_name,recipient_name,recipient_phone,country_code,province,city,district,address_line1,address_line2,postal_code,external_item_id,external_product_id,external_variant_id,external_sku,product_name,variant_name,image_url,quantity,unit_price_minor,note";
        var fields=header.split(",");var values=new java.util.HashMap<String,String>();
        values.put("store_code",code);values.put("external_order_id",code);values.put("external_status","NEW");
        values.put("ordered_at","2026-11-17T00:00:00Z");values.put("currency","USD");
        values.put("external_item_id",code);values.put("product_name","Test product");values.put("quantity","2");values.put("unit_price_minor","100");
        String row=java.util.Arrays.stream(fields).map(f->values.getOrDefault(f,"")).collect(java.util.stream.Collectors.joining(","));
        var file=new org.springframework.mock.web.MockMultipartFile("file","test.csv","text/csv",(header+"\n"+row+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var preview=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/order-imports/preview")
            .file(file).header("Authorization","Bearer "+token)).andExpect(status().isCreated()).andReturn();
        var response=mapper.readTree(preview.getResponse().getContentAsString());
        assertThat(response.get("status").asText()).isEqualTo("VALIDATED");
        String importId=response.get("importId").asText();
        mvc.perform(get("/api/order-imports/"+importId+"/errors").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/order-imports/"+importId+"/confirm")
            .header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(jdbc.queryForObject("select count(*) from sales_orders where external_order_id=?",Long.class,code)).isEqualTo(1);
        Long orderId=jdbc.queryForObject("select id from sales_orders where external_order_id=?",Long.class,code);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?",Long.class,orderId)).isEqualTo(1);
        Long itemId=jdbc.queryForObject("select id from sales_order_items where order_id=?",Long.class,orderId);
        jdbc.update("insert into order_procurement_requests(order_item_id,requested_quantity,status,requested_by,requested_at,version) values(?,2,'REQUESTED',?,CURRENT_TIMESTAMP(6),0)",itemId,id);
        Long requestId=jdbc.queryForObject("select id from order_procurement_requests where order_item_id=?",Long.class,itemId);
        var command=new com.szr.crosserp.orderservice.procurement.ProcurementClaimService.Command("claim-"+id,
            java.util.List.of(new com.szr.crosserp.orderservice.procurement.ProcurementClaimService.Item(requestId,2,0)));
        assertThat(this.claims.claim(command).status()).isEqualTo("CLAIMED");
        assertThat(this.claims.claim(command).status()).isEqualTo("CLAIMED");
        assertThat(this.claims.confirm(command.idempotencyKey(),"PO-TEST").status()).isEqualTo("CONFIRMED");
        assertThat(this.claims.confirm(command.idempotencyKey(),"PO-TEST").status()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select status from order_procurement_requests where id=?",String.class,requestId)).isEqualTo("CONVERTED");
        String event=jdbc.queryForObject("select payload from outbox_events where event_type='order.created.v2' and aggregate_id=?",String.class,orderId);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='order.procurement.snapshot.v1' and aggregate_id=?",Long.class,requestId)).isEqualTo(1);
        assertThat(event).doesNotContain("recipient_phone","address_line1","password");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/order-imports/"+importId+"/confirm")
            .header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='order.created.v2' and aggregate_id=?",Long.class,orderId)).isEqualTo(1);
    }
    @Test void schemaContainsOnlyDisplayProjectionAndOwnedTables() {
        assertThat(jdbc.queryForObject("select count(*) from sales_orders",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from order_import_batches",Long.class)).isZero();
        String schema=jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection ->
            connection.getMetaData().getDatabaseProductName().equals("MySQL") ? connection.getCatalog() : connection.getSchema());
        var columns=jdbc.queryForList("select column_name from information_schema.columns where table_name='user_display_projections' and table_schema=?",String.class,schema);
        assertThat(columns).containsExactlyInAnyOrder("id","login_name","display_name","status","source_version");
    }
    @Test void listEnvelopeMatchesExistingContract() throws Exception {
        var json=mapper.readTree(mapper.writeValueAsString(new OrderPageResponse(java.util.List.of(),1,20,0,0,new OrderSummaryResponse(0,0,0,0))));
        assertThat(json.size()).isEqualTo(6);
        assertThat(json.get("items").isArray()).isTrue();
        assertThat(json.get("page").asInt()).isEqualTo(1);
        assertThat(json.get("size").asInt()).isEqualTo(20);
        assertThat(json.get("totalElements").asLong()).isZero();
        assertThat(json.get("totalPages").asInt()).isZero();
        assertThat(json.get("summary").has("waitingProcurement")).isTrue();
    }
    @Test void missingAndInvalidTokensReturnContract401() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        mvc.perform(get("/api/orders").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
    }
    @Test void rs256DecoderAcceptsOnlyCorrectSignatureAndIssuer() throws Exception {
        var key=new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey)pair.getPublic()).privateKey((RSAPrivateKey)pair.getPrivate()).build();
        var encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        for(String issuer:java.util.List.of("cross-border-erp","wrong")) {
            var claims=JwtClaimsSet.builder().issuer(issuer).subject("1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
            var token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),claims)).getTokenValue();
            if(issuer.equals("wrong")) assertThatThrownBy(()->decoder.decode(token)).isInstanceOf(JwtException.class);
            else assertThat(decoder.decode(token).getSubject()).isEqualTo("1");
        }
    }
}
