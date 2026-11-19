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

@SpringBootTest(properties={"spring.datasource.url=${PROCUREMENT_TEST_DATABASE_URL:jdbc:h2:mem:procurementfoundation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${PROCUREMENT_TEST_DATABASE_USER:sa}","spring.datasource.password=${PROCUREMENT_TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc
class ProcurementFoundationTest {
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

    @Test void schemaOwnsPurchaseTablesAndOnlySafeExternalProjections() {
        for(String table:java.util.List.of("suppliers","purchase_orders","purchase_order_items","purchase_order_allocations","purchase_order_events","user_display_projections","procurement_order_projections","procurement_item_projections","procurement_request_projections"))
            assertThat(jdbc.queryForObject("select count(*) from "+table,Long.class)).isZero();
        assertThatThrownBy(()->jdbc.queryForObject("select count(*) from app_users",Long.class)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.queryForObject("select count(*) from sales_orders",Long.class)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("select max(version) from flyway_schema_history where success=true",String.class)).isEqualTo("4");
    }
    @Test void anonymousAndMalformedJwtAreRejected() throws Exception {
        mvc.perform(get("/api/purchase-orders")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        mvc.perform(get("/api/suppliers").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
    }
    @Test void rs256AcceptsOnlyConfiguredIssuer() {
        var key=new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey)pair.getPublic()).privateKey((RSAPrivateKey)pair.getPrivate()).build();
        var encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        for(String issuer:java.util.List.of("cross-border-erp","wrong")) {
            var claims=JwtClaimsSet.builder().issuer(issuer).subject("1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
            String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),claims)).getTokenValue();
            if(issuer.equals("wrong"))assertThatThrownBy(()->decoder.decode(token)).isInstanceOf(JwtException.class);
            else assertThat(decoder.decode(token).getSubject()).isEqualTo("1");
        }
    }
}
